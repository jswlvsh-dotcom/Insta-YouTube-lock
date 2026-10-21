package com.jaivish.gowork.core

import java.time.DayOfWeek

/** How one watched app (Instagram, YouTube, ...) is handled. */
data class AppRule(
    val packageName: String,
    val enabled: Boolean = true,
    /** Daily screen-time limit. 0 = no limit, only reminders. */
    val dailyLimitMinutes: Int = 30,
    /** Lock Reels / Shorts screens the moment they open (Instagram & YouTube only). */
    val blockShortForm: Boolean = false,
)

/** Everything the user can configure. Pure data so the engine can be unit tested. */
data class Settings(
    val onboarded: Boolean = false,
    val protectionEnabled: Boolean = true,
    val userName: String = "",
    val goal: String = "",
    /** Show the "Go do your work" lock after this many minutes of use. */
    val nudgeIntervalMinutes: Int = 2,
    /** Seconds the user must wait before "Continue" unlocks on a reminder. */
    val nudgeWaitSeconds: Int = 10,
    /** Every reminder in the same session makes the wait 5 s longer. */
    val escalateWait: Boolean = true,
    /** Short pause screen every time a watched app is opened after a break. */
    val mindfulOpen: Boolean = true,
    val mindfulOpenSeconds: Int = 5,
    val voiceEnabled: Boolean = false,
    val vibrationEnabled: Boolean = true,
    /** Settings can only be changed after a 60 s cool-down. */
    val strictMode: Boolean = false,
    val focusEnabled: Boolean = false,
    val focusStartMinute: Int = 10 * 60,
    val focusEndMinute: Int = 18 * 60,
    val focusDays: Set<DayOfWeek> = WORK_DAYS,
    /** Wall-clock end of a running focus sprint, 0 when none is running. */
    val sprintEndsAt: Long = 0L,
    val rules: List<AppRule> = DEFAULT_RULES,
) {
    fun ruleFor(pkg: String): AppRule? = rules.firstOrNull { it.packageName == pkg }

    fun isTracked(pkg: String?): Boolean = pkg != null && ruleFor(pkg)?.enabled == true

    val trackedPackages: List<String>
        get() = rules.filter { it.enabled }.map { it.packageName }

    companion object {
        val WORK_DAYS: Set<DayOfWeek> = DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }.toSet()

        val DEFAULT_RULES = listOf(
            AppRule(KnownApps.INSTAGRAM, dailyLimitMinutes = 30),
            AppRule(KnownApps.YOUTUBE, dailyLimitMinutes = 45),
        )
    }
}
