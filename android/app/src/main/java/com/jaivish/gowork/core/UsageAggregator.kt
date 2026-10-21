package com.jaivish.gowork.core

/**
 * Turns raw UsageStatsManager events into foreground time per app, the same way
 * Digital Wellbeing does: an app is "on screen" from its first resumed activity
 * until its last activity pauses, the screen turns off or the phone shuts down.
 */
object UsageAggregator {

    // Values of android.app.usage.UsageEvents.Event constants (kept here so this stays pure Kotlin).
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26

    data class RawEvent(val packageName: String, val className: String?, val type: Int, val timeMs: Long)

    fun aggregate(events: List<RawEvent>, startMs: Long, endMs: Long): Map<String, Long> {
        val totals = HashMap<String, Long>()
        val resumed = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()

        fun close(pkg: String, at: Long) {
            val from = since.remove(pkg) ?: return
            resumed.remove(pkg)
            val duration = (minOf(at, endMs) - from).coerceAtLeast(0L)
            totals[pkg] = (totals[pkg] ?: 0L) + duration
        }

        for (event in events.sortedBy { it.timeMs }) {
            when (event.type) {
                ACTIVITY_RESUMED -> {
                    resumed.getOrPut(event.packageName) { HashSet() } += event.className.orEmpty()
                    if (event.packageName !in since) {
                        since[event.packageName] = event.timeMs.coerceIn(startMs, endMs)
                    }
                }
                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> {
                    val open = resumed[event.packageName] ?: continue
                    open.remove(event.className.orEmpty())
                    if (open.isEmpty()) close(event.packageName, event.timeMs)
                }
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> {
                    since.keys.toList().forEach { close(it, event.timeMs) }
                }
            }
        }
        since.keys.toList().forEach { close(it, endMs) }
        return totals
    }
}
