package com.jaivish.gowork.data

import android.content.Context
import com.jaivish.gowork.core.DayCounters
import com.jaivish.gowork.core.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate

/**
 * Daily counters kept in memory and written to SharedPreferences (one JSON blob per day)
 * whenever [flush] is called. The engine updates usage every second, so writes are batched.
 */
class PrefsStatsRepository(context: Context) : StatsRepository {

    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private val cache = HashMap<String, HashMap<String, DayCounters>>()
    private val dirty = HashSet<String>()
    private val version = MutableStateFlow(0L)

    /** Bumps on every change; the UI collects it to refresh. */
    val changes: StateFlow<Long> = version.asStateFlow()

    @Synchronized
    override fun get(day: String, pkg: String): DayCounters = load(day)[pkg] ?: DayCounters()

    @Synchronized
    override fun update(day: String, pkg: String, transform: (DayCounters) -> DayCounters) {
        val map = load(day)
        val before = map[pkg] ?: DayCounters()
        val after = transform(before)
        if (after == before) return
        map[pkg] = after
        dirty += day
        version.value = version.value + 1
    }

    @Synchronized
    override fun day(day: String): Map<String, DayCounters> = HashMap(load(day))

    @Synchronized
    override fun flush() {
        if (dirty.isEmpty()) return
        val editor = prefs.edit()
        for (day in dirty) editor.putString(PREFIX + day, encode(cache[day].orEmpty()))
        dirty.clear()
        editor.apply()
    }

    /** Drops days older than [keepDays] so the file stays small. */
    @Synchronized
    fun prune(today: LocalDate, keepDays: Long = 120) {
        val oldest = today.minusDays(keepDays).toString()
        val stale = prefs.all.keys.filter { it.startsWith(PREFIX) && it.removePrefix(PREFIX) < oldest }
        if (stale.isNotEmpty()) {
            val editor = prefs.edit()
            stale.forEach { editor.remove(it) }
            editor.apply()
        }
        cache.keys.filter { it < oldest }.forEach { cache.remove(it) }
    }

    private fun load(day: String): HashMap<String, DayCounters> =
        cache.getOrPut(day) { decode(prefs.getString(PREFIX + day, null)) }

    private fun encode(map: Map<String, DayCounters>): String {
        val root = JSONObject()
        for ((pkg, c) in map) {
            root.put(
                pkg,
                JSONObject()
                    .put("u", c.usageMs)
                    .put("o", c.opens)
                    .put("n", c.nudges)
                    .put("l", c.listened)
                    .put("su", c.shortFormMs)
                    .put("sb", c.shortFormBlocks)
                    .put("e", c.emergencies)
                    .put("h", c.limitHit),
            )
        }
        return root.toString()
    }

    private fun decode(json: String?): HashMap<String, DayCounters> {
        val result = HashMap<String, DayCounters>()
        if (json.isNullOrBlank()) return result
        try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val o = root.getJSONObject(pkg)
                result[pkg] = DayCounters(
                    usageMs = o.optLong("u"),
                    opens = o.optInt("o"),
                    nudges = o.optInt("n"),
                    listened = o.optInt("l"),
                    shortFormMs = o.optLong("su"),
                    shortFormBlocks = o.optInt("sb"),
                    emergencies = o.optInt("e"),
                    limitHit = o.optBoolean("h"),
                )
            }
        } catch (e: Exception) {
            // Corrupt day: start it fresh rather than crash the guard.
        }
        return result
    }

    private companion object {
        const val PREFIX = "d:"
    }
}
