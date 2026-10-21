package com.jaivish.gowork.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.engine.EngineHost
import com.jaivish.gowork.engine.FocusEngine
import com.jaivish.gowork.engine.LiveState
import com.jaivish.gowork.engine.LiveStatus
import com.jaivish.gowork.engine.LockActions
import com.jaivish.gowork.engine.LockKind
import com.jaivish.gowork.engine.LockRequest
import com.jaivish.gowork.engine.Messages
import com.jaivish.gowork.system.Permissions
import com.jaivish.gowork.system.ScreenTimeReader
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Watches which app is on screen, feeds the [FocusEngine] and draws its locks.
 * Runs as long as GoWork is switched on in Settings > Accessibility.
 */
class GuardAccessibilityService : AccessibilityService(), EngineHost, LockActions {

    private val handler = Handler(Looper.getMainLooper())
    private var engine: FocusEngine? = null
    private var overlay: LockOverlay? = null
    private var audioFocus: AudioFocusHolder? = null
    private var voice: Voice? = null
    private var io: ExecutorService? = null
    private val ignoredPackages = HashSet<String>()
    private var tickCount = 0L
    private var lastScanAt = 0L
    private var scanPending = false
    private var receiverRegistered = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val scanRunnable = Runnable {
        scanPending = false
        scanShortForm()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> engine?.onScreenInteractive(false)
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    engine?.onScreenInteractive(isInteractiveNow())
                    verifyForeground()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        io = Executors.newSingleThreadExecutor()
        refreshIgnoredPackages()
        overlay = LockOverlay(this, this)
        audioFocus = AudioFocusHolder(this)
        engine = FocusEngine(
            host = this,
            stats = Graph.stats,
            settings = { Graph.settings.current },
            onLive = { LiveState.update(it) },
        ).also { it.onScreenInteractive(isInteractiveNow()) }
        LiveState.update(LiveStatus(connected = true))
        registerScreenReceiver()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        verifyForeground()
        refreshSystemUsage()
        GuardService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val engine = engine ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg !in ignoredPackages) engine.onForegroundChanged(pkg)
                requestShortFormScan(pkg, urgent = true)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            -> requestShortFormScan(pkg, urgent = false)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------- EngineHost

    override fun showLock(request: LockRequest, fresh: Boolean) {
        overlay?.show(request)
        if (!fresh) return
        val s = Graph.settings.current
        if (request.kind != LockKind.OPEN_PAUSE) audioFocus?.pauseOthers()
        if (s.vibrationEnabled) Haptics.buzz(this, request.kind)
        if (s.voiceEnabled && request.kind != LockKind.OPEN_PAUSE) {
            val v = voice ?: Voice(this).also { voice = it }
            v.speak(Messages.spoken(request.kind, s.userName))
        }
    }

    override fun hideLock() {
        overlay?.hide()
        audioFocus?.release()
    }

    override fun goHome(fromPkg: String) {
        // YouTube keeps playing in picture-in-picture (or in the background with Premium)
        // after Home, so pause it first. Not done for other apps so background music survives.
        if (fromPkg in VIDEO_APPS) pauseMedia()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun goBack() {
        // Let the overlay disappear first so the back press reaches the app underneath.
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, BACK_DELAY_MS)
    }

    override fun labelFor(pkg: String): String = Graph.apps.label(pkg)

    // ------------------------------------------------------------ LockActions

    override fun primary(request: LockRequest) {
        engine?.onPrimary(request.id)
    }

    override fun secondary(request: LockRequest) {
        engine?.onSecondary(request.id)
    }

    override fun back(request: LockRequest) {
        engine?.onBack(request.id)
    }

    // --------------------------------------------------------------- internals

    private fun onTick() {
        val engine = engine ?: return
        tickCount++
        engine.onScreenInteractive(isInteractiveNow())
        if (tickCount % VERIFY_EVERY_TICKS == 0L) verifyForeground()
        engine.tick()
        if (tickCount % FLUSH_EVERY_TICKS == 0L) Graph.stats.flush()
        if (tickCount % SYSTEM_USAGE_EVERY_TICKS == 0L) refreshSystemUsage()
        if (tickCount % IGNORED_REFRESH_EVERY_TICKS == 0L) refreshIgnoredPackages()
    }

    /**
     * Window events can be missed (screen unlock, floating windows), so ask the system directly.
     * A watched app playing in picture-in-picture counts as being used, so a floating YouTube
     * video can't run past the reminders.
     */
    private fun verifyForeground() {
        val active = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }?.takeIf { it !in ignoredPackages }
        val settings = Graph.settings.current
        val pkg = if (active != null && settings.isTracked(active)) {
            active
        } else {
            pictureInPicturePackage()?.takeIf { settings.isTracked(it) && isAudioPlaying() } ?: active
        } ?: return
        engine?.onForegroundChanged(pkg)
    }

    private fun pictureInPicturePackage(): String? = try {
        windows.firstOrNull { it.isInPictureInPictureMode }?.root?.packageName?.toString()
    } catch (e: Exception) {
        null
    }

    private fun isAudioPlaying(): Boolean = getSystemService(AudioManager::class.java)?.isMusicActive == true

    private fun pauseMedia() {
        val audio = getSystemService(AudioManager::class.java) ?: return
        val now = SystemClock.uptimeMillis()
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        } catch (e: Exception) {
            Log.w(TAG, "Could not pause media", e)
        }
    }

    private fun requestShortFormScan(pkg: String, urgent: Boolean) {
        if (!ShortFormDetector.supports(pkg) || !Graph.settings.current.isTracked(pkg)) return
        if (scanPending) return
        val sinceLast = SystemClock.uptimeMillis() - lastScanAt
        val delay = if (urgent) URGENT_SCAN_DELAY_MS else (SCAN_INTERVAL_MS - sinceLast).coerceAtLeast(0L)
        scanPending = true
        handler.postDelayed(scanRunnable, delay)
    }

    private fun scanShortForm() {
        lastScanAt = SystemClock.uptimeMillis()
        val engine = engine ?: return
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return
        val pkg = root.packageName?.toString() ?: return
        if (!ShortFormDetector.supports(pkg)) return
        engine.onShortFormChanged(pkg, ShortFormDetector.isShortForm(pkg, root))
    }

    private fun isInteractiveNow(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val screenOn = power?.isInteractive ?: true
        val locked = keyguard?.isKeyguardLocked ?: false
        return screenOn && !locked
    }

    /** Our own lock, the notification shade and keyboards never count as "leaving" an app. */
    private fun refreshIgnoredPackages() {
        val next = HashSet<String>()
        next += packageName
        next += listOf(
            "com.android.systemui",
            "android",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
        try {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { next += it.packageName }
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { next += it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list keyboards", e)
        }
        ignoredPackages.clear()
        ignoredPackages += next
    }

    private fun refreshSystemUsage() {
        val executor = io ?: return
        if (!Permissions.hasUsageAccess(this)) return
        val tracked = Graph.settings.current.trackedPackages.toSet()
        executor.execute {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val usage = try {
                ScreenTimeReader(this).usageForDay(today, zone).filterKeys { it in tracked }
            } catch (e: Exception) {
                Log.w(TAG, "Usage stats unavailable", e)
                return@execute
            }
            handler.post { engine?.updateSystemUsage(today.toString(), usage) }
            Graph.stats.prune(today)
        }
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        overlay?.hide()
        overlay = null
        audioFocus?.release()
        audioFocus = null
        voice?.shutdown()
        voice = null
        engine = null
        io?.shutdown()
        io = null
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Receiver already gone", e)
            }
            receiverRegistered = false
        }
        Graph.stats.flush()
        LiveState.update(LiveStatus(connected = false))
    }

    private companion object {
        const val TAG = "GoWorkGuard"
        const val TICK_MS = 1_000L
        const val VERIFY_EVERY_TICKS = 3L
        const val FLUSH_EVERY_TICKS = 15L
        const val SYSTEM_USAGE_EVERY_TICKS = 300L
        const val IGNORED_REFRESH_EVERY_TICKS = 600L
        const val URGENT_SCAN_DELAY_MS = 250L
        const val SCAN_INTERVAL_MS = 700L
        const val BACK_DELAY_MS = 200L

        val VIDEO_APPS = setOf(
            KnownApps.YOUTUBE,
            "app.revanced.android.youtube",
            "com.google.android.apps.youtube.music",
        )
    }
}
