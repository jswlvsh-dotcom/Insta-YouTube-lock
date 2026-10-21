package com.jaivish.gowork.engine

enum class LockKind {
    /** Short breathing pause when a watched app is opened after a break. */
    OPEN_PAUSE,

    /** "Go do your work" - shown every N minutes of use. */
    NUDGE,

    /** Daily limit reached: locked until midnight. */
    LIMIT,

    /** Inside the user's focus hours. */
    FOCUS,

    /** A focus sprint started from the dashboard is running. */
    SPRINT,

    /** Reels / Shorts screen while short-form blocking is on. */
    SHORT_FORM,
}

/** Everything the lock screen needs to render one lock. */
data class LockRequest(
    val id: Long,
    val kind: LockKind,
    val packageName: String,
    val appLabel: String,
    /** Seconds before the secondary button ("Continue", "Emergency 5 min") unlocks. */
    val waitSeconds: Int,
    val nudgeNumber: Int,
    val intervalMinutes: Int,
    val todayUsageMs: Long,
    val todayOpens: Int,
    val limitMinutes: Int,
    val emergenciesLeft: Int,
    /** "6:00 PM" for focus hours / sprints. */
    val untilLabel: String?,
    val message: String,
    val userName: String,
    val goal: String,
)

/** Buttons on the lock screen. */
interface LockActions {
    fun primary(request: LockRequest)
    fun secondary(request: LockRequest)
    fun back(request: LockRequest)
}

/** Live state for the dashboard and the status notification. */
data class LiveStatus(
    val connected: Boolean = false,
    val trackingPackage: String? = null,
    val msToNextNudge: Long? = null,
    val lockKind: LockKind? = null,
    val inShortForm: Boolean = false,
)
