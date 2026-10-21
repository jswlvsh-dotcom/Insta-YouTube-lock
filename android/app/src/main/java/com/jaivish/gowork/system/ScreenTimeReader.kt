package com.jaivish.gowork.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.jaivish.gowork.core.UsageAggregator
import com.jaivish.gowork.data.Graph
import java.time.LocalDate
import java.time.ZoneId

/** Reads the phone's real screen time from UsageStatsManager (needs Usage access). */
class ScreenTimeReader(private val context: Context) {

    private val usm: UsageStatsManager? = context.getSystemService(UsageStatsManager::class.java)

    fun usageBetween(startMs: Long, endMs: Long): Map<String, Long> {
        val manager = usm ?: return emptyMap()
        if (endMs <= startMs) return emptyMap()
        val events = manager.queryEvents(startMs, endMs) ?: return emptyMap()
        val raw = ArrayList<UsageAggregator.RawEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            raw += UsageAggregator.RawEvent(pkg, event.className, event.eventType, event.timeStamp)
        }
        return UsageAggregator.aggregate(raw, startMs, endMs)
    }

    /** Foreground time per app on [date], up to now if it is today. */
    fun usageForDay(date: LocalDate, zone: ZoneId): Map<String, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        return usageBetween(start, end)
    }

    fun snapshot(tracked: List<String>, days: Int = 7, zone: ZoneId = ZoneId.systemDefault()): ScreenTimeSnapshot {
        if (!Permissions.hasUsageAccess(context)) return ScreenTimeSnapshot(hasAccess = false)
        val today = LocalDate.now(zone)
        val todayAll = usageForDay(today, zone)
        val hidden = Graph.apps.launcherPackages() + setOf(context.packageName, "com.android.systemui", "android")
        val visible = todayAll.filterKeys { it !in hidden }
        val week = (days - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val usage = if (back == 0) todayAll else usageForDay(date, zone)
            DayUsage(date, tracked.associateWith { usage[it] ?: 0L })
        }
        return ScreenTimeSnapshot(
            hasAccess = true,
            totalTodayMs = visible.values.sum(),
            topApps = visible.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            trackedToday = tracked.associateWith { todayAll[it] ?: 0L },
            week = week,
        )
    }
}

data class DayUsage(val date: LocalDate, val perApp: Map<String, Long>) {
    val total: Long get() = perApp.values.sum()
}

data class ScreenTimeSnapshot(
    val hasAccess: Boolean,
    val totalTodayMs: Long = 0L,
    val topApps: List<Pair<String, Long>> = emptyList(),
    val trackedToday: Map<String, Long> = emptyMap(),
    val week: List<DayUsage> = emptyList(),
)
