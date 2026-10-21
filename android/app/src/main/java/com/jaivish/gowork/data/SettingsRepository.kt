package com.jaivish.gowork.data

import android.content.Context
import com.jaivish.gowork.core.AppRule
import com.jaivish.gowork.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** Settings stored in SharedPreferences and exposed as a StateFlow for the UI and the engine. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val flow: StateFlow<Settings> = state.asStateFlow()
    val current: Settings get() = state.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = sanitize(transform(state.value))
        if (next == state.value) return
        save(next)
        state.value = next
    }

    private fun sanitize(s: Settings) = s.copy(
        userName = s.userName.take(40),
        goal = s.goal.take(120),
        nudgeIntervalMinutes = s.nudgeIntervalMinutes.coerceIn(1, 60),
        nudgeWaitSeconds = s.nudgeWaitSeconds.coerceIn(0, 120),
        mindfulOpenSeconds = s.mindfulOpenSeconds.coerceIn(1, 30),
        focusStartMinute = s.focusStartMinute.coerceIn(0, 24 * 60 - 1),
        focusEndMinute = s.focusEndMinute.coerceIn(0, 24 * 60 - 1),
        rules = s.rules
            .distinctBy { it.packageName }
            .map { it.copy(dailyLimitMinutes = it.dailyLimitMinutes.coerceIn(0, 24 * 60)) },
    )

    private fun load(): Settings {
        val d = Settings()
        if (!prefs.contains(K_ONBOARDED)) return d
        return Settings(
            onboarded = prefs.getBoolean(K_ONBOARDED, d.onboarded),
            protectionEnabled = prefs.getBoolean(K_PROTECTION, d.protectionEnabled),
            userName = prefs.getString(K_NAME, d.userName).orEmpty(),
            goal = prefs.getString(K_GOAL, d.goal).orEmpty(),
            nudgeIntervalMinutes = prefs.getInt(K_INTERVAL, d.nudgeIntervalMinutes),
            nudgeWaitSeconds = prefs.getInt(K_WAIT, d.nudgeWaitSeconds),
            escalateWait = prefs.getBoolean(K_ESCALATE, d.escalateWait),
            mindfulOpen = prefs.getBoolean(K_MINDFUL, d.mindfulOpen),
            mindfulOpenSeconds = prefs.getInt(K_MINDFUL_SECONDS, d.mindfulOpenSeconds),
            voiceEnabled = prefs.getBoolean(K_VOICE, d.voiceEnabled),
            vibrationEnabled = prefs.getBoolean(K_VIBRATION, d.vibrationEnabled),
            strictMode = prefs.getBoolean(K_STRICT, d.strictMode),
            focusEnabled = prefs.getBoolean(K_FOCUS, d.focusEnabled),
            focusStartMinute = prefs.getInt(K_FOCUS_START, d.focusStartMinute),
            focusEndMinute = prefs.getInt(K_FOCUS_END, d.focusEndMinute),
            focusDays = daysFromMask(prefs.getInt(K_FOCUS_DAYS, daysToMask(d.focusDays))),
            sprintEndsAt = prefs.getLong(K_SPRINT_ENDS, d.sprintEndsAt),
            rules = rulesFromJson(prefs.getString(K_RULES, null)) ?: d.rules,
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putBoolean(K_ONBOARDED, s.onboarded)
            .putBoolean(K_PROTECTION, s.protectionEnabled)
            .putString(K_NAME, s.userName)
            .putString(K_GOAL, s.goal)
            .putInt(K_INTERVAL, s.nudgeIntervalMinutes)
            .putInt(K_WAIT, s.nudgeWaitSeconds)
            .putBoolean(K_ESCALATE, s.escalateWait)
            .putBoolean(K_MINDFUL, s.mindfulOpen)
            .putInt(K_MINDFUL_SECONDS, s.mindfulOpenSeconds)
            .putBoolean(K_VOICE, s.voiceEnabled)
            .putBoolean(K_VIBRATION, s.vibrationEnabled)
            .putBoolean(K_STRICT, s.strictMode)
            .putBoolean(K_FOCUS, s.focusEnabled)
            .putInt(K_FOCUS_START, s.focusStartMinute)
            .putInt(K_FOCUS_END, s.focusEndMinute)
            .putInt(K_FOCUS_DAYS, daysToMask(s.focusDays))
            .putLong(K_SPRINT_ENDS, s.sprintEndsAt)
            .putString(K_RULES, rulesToJson(s.rules))
            .apply()
    }

    private fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    private fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    private fun rulesToJson(rules: List<AppRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("pkg", rule.packageName)
                    .put("on", rule.enabled)
                    .put("limit", rule.dailyLimitMinutes)
                    .put("short", rule.blockShortForm),
            )
        }
        return array.toString()
    }

    private fun rulesFromJson(json: String?): List<AppRule>? {
        if (json.isNullOrBlank()) return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                AppRule(
                    packageName = o.getString("pkg"),
                    enabled = o.optBoolean("on", true),
                    dailyLimitMinutes = o.optInt("limit", 30),
                    blockShortForm = o.optBoolean("short", false),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val K_ONBOARDED = "onboarded"
        const val K_PROTECTION = "protection"
        const val K_NAME = "name"
        const val K_GOAL = "goal"
        const val K_INTERVAL = "interval_min"
        const val K_WAIT = "wait_sec"
        const val K_ESCALATE = "escalate"
        const val K_MINDFUL = "mindful"
        const val K_MINDFUL_SECONDS = "mindful_sec"
        const val K_VOICE = "voice"
        const val K_VIBRATION = "vibration"
        const val K_STRICT = "strict"
        const val K_FOCUS = "focus"
        const val K_FOCUS_START = "focus_start"
        const val K_FOCUS_END = "focus_end"
        const val K_FOCUS_DAYS = "focus_days"
        const val K_SPRINT_ENDS = "sprint_ends"
        const val K_RULES = "rules"
    }
}
