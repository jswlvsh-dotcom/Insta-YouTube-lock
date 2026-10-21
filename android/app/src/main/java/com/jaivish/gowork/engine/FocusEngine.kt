package com.jaivish.gowork.engine

import com.jaivish.gowork.core.FocusSchedule
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.core.StatsRepository
import com.jaivish.gowork.core.dayKey
import com.jaivish.gowork.core.formatMinuteOfDay
import com.jaivish.gowork.core.formatTimeOfDay
import java.time.Instant
import java.time.ZoneId

/** What the engine needs from Android. Kept tiny so the engine runs in plain JVM tests. */
interface EngineHost {
    /** Show or update the lock. [fresh] is true when a lock appears or changes kind. */
    fun showLock(request: LockRequest, fresh: Boolean)
    fun hideLock()
    fun goHome()
    fun goBack()
    fun labelFor(pkg: String): String
}

/**
 * The brain of GoWork. It is fed foreground-app changes, screen on/off, Reels/Shorts
 * detection and a 1 s tick, and decides when to count time and which lock to show.
 *
 * Reminder timing: every [Settings.nudgeIntervalMinutes] of use across all watched apps
 * the "Go do your work" lock appears. A break of [SESSION_BREAK_MS] starts a new session,
 * which resets the timer and shows the mindful-open pause on the next open.
 */
class FocusEngine(
    private val host: EngineHost,
    private val stats: StatsRepository,
    private val settings: () -> Settings,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val onLive: (LiveStatus) -> Unit = {},
) {
    private var foreground: String? = null
    private var interactive = true
    private var shortFormPkg: String? = null
    private var shortFormMutedUntil = 0L

    private var systemUsageDay = ""
    private var systemUsage: Map<String, Long> = emptyMap()

    private var lastTickAt = 0L
    private var lastTrackedSeenAt = 0L
    private var lastLeftPkg: String? = null
    private var lastLeftAt = 0L
    private var usageSinceNudgeMs = 0L
    private var sessionNudges = 0
    private var pending: LockKind? = null
    private var showing: LockRequest? = null
    private var shownAt = 0L
    private val allowanceUntil = HashMap<String, Long>()
    private var lockSeq = 0L

    val currentLock: LockRequest? get() = showing
    val foregroundPackage: String? get() = foreground

    // ------------------------------------------------------------------ inputs

    fun onForegroundChanged(pkg: String) {
        val previous = foreground
        if (pkg == previous) return
        val now = clock()
        val s = settings()
        foreground = pkg
        if (shortFormPkg != pkg) shortFormPkg = null
        if (interactive) {
            if (previous != null && s.isTracked(previous)) markLeft(previous, now)
            if (s.isTracked(pkg)) enterTracked(pkg, now, s, countOpen = true)
        }
        evaluate(now)
        publish(s)
    }

    /** Screen on and unlocked = interactive. Time is never counted while locked or off. */
    fun onScreenInteractive(isInteractive: Boolean) {
        if (interactive == isInteractive) return
        val now = clock()
        val s = settings()
        val fg = foreground
        if (!isInteractive) {
            if (fg != null && s.isTracked(fg)) markLeft(fg, now)
            interactive = false
        } else {
            interactive = true
            if (fg != null && s.isTracked(fg)) enterTracked(fg, now, s, countOpen = false)
        }
        lastTickAt = now
        evaluate(now)
        publish(s)
    }

    fun onShortFormChanged(pkg: String, inShortForm: Boolean) {
        val now = clock()
        if (inShortForm) {
            if (shortFormPkg == pkg || now < shortFormMutedUntil) return
            shortFormPkg = pkg
        } else {
            if (shortFormPkg != pkg) return
            shortFormPkg = null
        }
        evaluate(now)
        publish(settings())
    }

    /** Today's screen time per app as measured by Android itself (Usage access). */
    fun updateSystemUsage(day: String, usage: Map<String, Long>) {
        systemUsageDay = day
        systemUsage = usage
        evaluate(clock())
    }

    fun tick() {
        val now = clock()
        val dt = if (lastTickAt == 0L) 0L else (now - lastTickAt).coerceIn(0L, MAX_TICK_GAP_MS)
        lastTickAt = now
        val s = settings()
        val pkg = activeTracked(s)
        if (pkg == null) {
            hide()
            publish(s)
            return
        }
        lastTrackedSeenAt = now
        evaluate(now)
        if (showing == null && dt > 0L) {
            val day = today(now)
            val inShortForm = shortFormPkg == pkg
            stats.update(day, pkg) {
                it.copy(
                    usageMs = it.usageMs + dt,
                    shortFormMs = if (inShortForm) it.shortFormMs + dt else it.shortFormMs,
                )
            }
            if (s.protectionEnabled && !isAllowed(pkg, now)) {
                usageSinceNudgeMs += dt
                if (usageSinceNudgeMs >= s.nudgeIntervalMinutes * MINUTE) {
                    usageSinceNudgeMs = 0L
                    sessionNudges += 1
                    pending = LockKind.NUDGE
                    stats.update(day, pkg) { it.copy(nudges = it.nudges + 1) }
                }
            }
            evaluate(now)
        }
        publish(s)
    }

    // ------------------------------------------------------------ lock buttons

    /** "I'm going to work" / "Close app" / "Get me out of Reels". */
    fun onPrimary(requestId: Long) {
        val request = showing?.takeIf { it.id == requestId } ?: return
        val now = clock()
        if (request.kind == LockKind.SHORT_FORM) {
            shortFormPkg = null
            shortFormMutedUntil = now + SHORT_FORM_MUTE_MS
            hide()
            host.goBack()
            publish(settings())
            return
        }
        if (request.kind == LockKind.NUDGE) usageSinceNudgeMs = 0L
        // A reminder stays pending: coming straight back shows it again (with its countdown),
        // so "I'm going to work" can't be used as a shortcut around "Continue".
        // A break of SESSION_BREAK_MS clears it.
        stats.update(today(now), request.packageName) { it.copy(listened = it.listened + 1) }
        leaveApp(request.packageName, now)
    }

    /** "Continue" on reminders, "Emergency 5 min" on hard locks, "Close app" on Reels. */
    fun onSecondary(requestId: Long) {
        val request = showing?.takeIf { it.id == requestId } ?: return
        val now = clock()
        if (now - shownAt < request.waitSeconds * 1000L - WAIT_SLACK_MS) return
        when (request.kind) {
            LockKind.OPEN_PAUSE, LockKind.NUDGE -> {
                pending = null
                usageSinceNudgeMs = 0L
                hide()
            }
            LockKind.LIMIT, LockKind.FOCUS, LockKind.SPRINT -> {
                if (request.emergenciesLeft <= 0) return
                allowanceUntil[request.packageName] = now + EMERGENCY_MS
                pending = null
                usageSinceNudgeMs = 0L
                stats.update(today(now), request.packageName) { it.copy(emergencies = it.emergencies + 1) }
                hide()
            }
            LockKind.SHORT_FORM -> {
                leaveApp(request.packageName, now)
                return
            }
        }
        evaluate(now)
        publish(settings())
    }

    /** System back while a lock is up counts as choosing to leave. */
    fun onBack(requestId: Long) = onPrimary(requestId)

    // --------------------------------------------------------------- internals

    private fun leaveApp(pkg: String, now: Long) {
        hide()
        markLeft(pkg, now)
        foreground = null
        shortFormPkg = null
        host.goHome()
        publish(settings())
    }

    private fun markLeft(pkg: String, now: Long) {
        lastTrackedSeenAt = now
        lastLeftPkg = pkg
        lastLeftAt = now
    }

    private fun enterTracked(pkg: String, now: Long, s: Settings, countOpen: Boolean) {
        val newSession = lastTrackedSeenAt == 0L || now - lastTrackedSeenAt >= SESSION_BREAK_MS
        if (newSession) {
            usageSinceNudgeMs = 0L
            sessionNudges = 0
            pending = if (s.protectionEnabled && s.mindfulOpen) LockKind.OPEN_PAUSE else null
        }
        val quickReturn = pkg == lastLeftPkg && now - lastLeftAt < REOPEN_GRACE_MS
        if (countOpen && !quickReturn) {
            stats.update(today(now), pkg) { it.copy(opens = it.opens + 1) }
        }
        lastTrackedSeenAt = now
    }

    private fun activeTracked(s: Settings): String? {
        val fg = foreground ?: return null
        return if (interactive && s.isTracked(fg)) fg else null
    }

    private fun isAllowed(pkg: String, now: Long) = (allowanceUntil[pkg] ?: 0L) > now

    private fun desiredLock(pkg: String, now: Long, s: Settings): LockKind? {
        val rule = s.ruleFor(pkg) ?: return null
        if (!isAllowed(pkg, now)) {
            if (s.sprintEndsAt > now) return LockKind.SPRINT
            if (FocusSchedule.isActive(s, Instant.ofEpochMilli(now).atZone(zone()))) return LockKind.FOCUS
            if (rule.dailyLimitMinutes > 0 && usageToday(pkg, now) >= rule.dailyLimitMinutes * MINUTE) {
                return LockKind.LIMIT
            }
        }
        if (rule.blockShortForm && shortFormPkg == pkg) return LockKind.SHORT_FORM
        return pending
    }

    private fun evaluate(now: Long) {
        val s = settings()
        if (!s.protectionEnabled) {
            pending = null
            hide()
            return
        }
        val pkg = activeTracked(s)
        val kind = pkg?.let { desiredLock(it, now, s) }
        if (pkg == null || kind == null) {
            hide()
            return
        }
        val current = showing
        if (current != null && current.kind == kind && current.packageName == pkg) return
        val day = today(now)
        when (kind) {
            LockKind.LIMIT -> stats.update(day, pkg) { if (it.limitHit) it else it.copy(limitHit = true) }
            LockKind.SHORT_FORM -> stats.update(day, pkg) { it.copy(shortFormBlocks = it.shortFormBlocks + 1) }
            else -> Unit
        }
        val request = buildRequest(kind, pkg, now, s)
        showing = request
        shownAt = now
        host.showLock(request, fresh = current == null || current.kind != kind)
    }

    private fun buildRequest(kind: LockKind, pkg: String, now: Long, s: Settings): LockRequest {
        val counters = stats.get(today(now), pkg)
        val wait = when (kind) {
            LockKind.OPEN_PAUSE -> s.mindfulOpenSeconds
            LockKind.NUDGE -> if (s.escalateWait) {
                val escalated = s.nudgeWaitSeconds + (sessionNudges - 1).coerceAtLeast(0) * ESCALATE_STEP_SECONDS
                escalated.coerceAtMost(maxOf(MAX_WAIT_SECONDS, s.nudgeWaitSeconds))
            } else {
                s.nudgeWaitSeconds
            }
            LockKind.LIMIT, LockKind.FOCUS, LockKind.SPRINT -> EMERGENCY_WAIT_SECONDS
            LockKind.SHORT_FORM -> 0
        }
        lockSeq += 1
        return LockRequest(
            id = lockSeq,
            kind = kind,
            packageName = pkg,
            appLabel = host.labelFor(pkg),
            waitSeconds = wait,
            nudgeNumber = sessionNudges,
            intervalMinutes = s.nudgeIntervalMinutes,
            todayUsageMs = usageToday(pkg, now),
            todayOpens = counters.opens,
            limitMinutes = s.ruleFor(pkg)?.dailyLimitMinutes ?: 0,
            emergenciesLeft = (MAX_EMERGENCIES_PER_DAY - counters.emergencies).coerceAtLeast(0),
            untilLabel = when (kind) {
                LockKind.FOCUS -> formatMinuteOfDay(s.focusEndMinute)
                LockKind.SPRINT -> formatTimeOfDay(s.sprintEndsAt, zone())
                else -> null
            },
            message = Messages.line(now / MINUTE + lockSeq),
            userName = s.userName.trim(),
            goal = s.goal.trim(),
        )
    }

    private fun usageToday(pkg: String, now: Long): Long {
        val day = today(now)
        val own = stats.get(day, pkg).usageMs
        val system = if (systemUsageDay == day) systemUsage[pkg] ?: 0L else 0L
        return maxOf(own, system)
    }

    private fun hide() {
        if (showing == null) return
        showing = null
        host.hideLock()
    }

    private fun publish(s: Settings) {
        val pkg = activeTracked(s)
        onLive(
            LiveStatus(
                connected = true,
                trackingPackage = pkg,
                msToNextNudge = if (pkg != null && s.protectionEnabled) {
                    (s.nudgeIntervalMinutes * MINUTE - usageSinceNudgeMs).coerceAtLeast(0L)
                } else {
                    null
                },
                lockKind = showing?.kind,
                inShortForm = pkg != null && shortFormPkg == pkg,
            ),
        )
    }

    private fun today(now: Long) = dayKey(now, zone())

    companion object {
        const val MINUTE = 60_000L
        const val SESSION_BREAK_MS = 3 * MINUTE
        const val REOPEN_GRACE_MS = 10_000L
        const val MAX_TICK_GAP_MS = 5_000L
        const val ESCALATE_STEP_SECONDS = 5
        const val MAX_WAIT_SECONDS = 60
        const val EMERGENCY_WAIT_SECONDS = 30
        const val EMERGENCY_MS = 5 * MINUTE
        const val MAX_EMERGENCIES_PER_DAY = 3
        const val SHORT_FORM_MUTE_MS = 1_500L
        const val WAIT_SLACK_MS = 1_500L
    }
}
