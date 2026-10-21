package com.jaivish.gowork.core

/** What happened with one app on one day. */
data class DayCounters(
    val usageMs: Long = 0L,
    val opens: Int = 0,
    val nudges: Int = 0,
    /** Times the user picked "I'm going to work" / "Close app" on a lock. */
    val listened: Int = 0,
    val shortFormMs: Long = 0L,
    val shortFormBlocks: Int = 0,
    val emergencies: Int = 0,
    val limitHit: Boolean = false,
)

/** Per-day, per-app counters. Days are ISO dates like "2026-10-21". */
interface StatsRepository {
    fun get(day: String, pkg: String): DayCounters
    fun update(day: String, pkg: String, transform: (DayCounters) -> DayCounters)
    fun day(day: String): Map<String, DayCounters>
    fun flush() {}
}

class MemoryStatsRepository : StatsRepository {
    private val days = HashMap<String, HashMap<String, DayCounters>>()

    override fun get(day: String, pkg: String): DayCounters = days[day]?.get(pkg) ?: DayCounters()

    override fun update(day: String, pkg: String, transform: (DayCounters) -> DayCounters) {
        val map = days.getOrPut(day) { HashMap() }
        map[pkg] = transform(map[pkg] ?: DayCounters())
    }

    override fun day(day: String): Map<String, DayCounters> = HashMap(days[day].orEmpty())
}
