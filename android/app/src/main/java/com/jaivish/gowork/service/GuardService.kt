package com.jaivish.gowork.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jaivish.gowork.GoWorkApp
import com.jaivish.gowork.MainActivity
import com.jaivish.gowork.R
import com.jaivish.gowork.core.dayKey
import com.jaivish.gowork.core.formatClockDuration
import com.jaivish.gowork.core.formatDuration
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.engine.LiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Foreground service with the always-on "GoWork is watching" notification. It keeps the
 * process high-priority so aggressive battery savers do not kill the guard.
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!enterForeground()) return
        scope.launch {
            LiveState.status
                .map { Triple(it.connected, it.trackingPackage, it.lockKind) }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        scope.launch {
            Graph.settings.flow
                .map { it.protectionEnabled to it.trackedPackages }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        scope.launch {
            while (isActive) {
                delay(REFRESH_MS)
                refresh()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enterForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not start foreground", e)
        stopSelf()
        false
    }

    private fun refresh() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val settings = Graph.settings.current
        val live = LiveState.status.value
        val tracked = settings.trackedPackages
        val day = dayKey(System.currentTimeMillis(), ZoneId.systemDefault())
        val summary = tracked.joinToString("  ·  ") { pkg ->
            "${Graph.apps.label(pkg)} ${formatDuration(Graph.stats.get(day, pkg).usageMs)}"
        }.ifEmpty { "No apps selected yet" }
        val names = tracked.take(3).joinToString(" & ") { Graph.apps.label(it) }.ifEmpty { "your apps" }

        val (title, text) = when {
            !settings.protectionEnabled -> "Protection paused" to "Open GoWork to switch it back on."
            !live.connected -> "GoWork is not running" to "Turn on GoWork in Accessibility settings. Tap to fix."
            live.trackingPackage != null -> {
                val next = live.msToNextNudge?.let { " · next reminder in ${formatClockDuration(it)}" }.orEmpty()
                "On ${Graph.apps.label(live.trackingPackage)}$next" to "Today: $summary"
            }
            else -> "Watching $names" to "Today: $summary"
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, GoWorkApp.CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_gowork)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ContextCompat.getColor(this, R.color.volt))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "GoWorkService"
        private const val NOTIFICATION_ID = 42
        private const val REFRESH_MS = 30_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
            } catch (e: Exception) {
                // Android may refuse a background start; the accessibility service still works.
                Log.w(TAG, "Could not start guard service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
