package com.jaivish.gowork.core

import java.time.ZonedDateTime

/** Focus hours: watched apps stay locked during this window on the chosen days. */
object FocusSchedule {

    fun isActive(settings: Settings, at: ZonedDateTime): Boolean {
        if (!settings.focusEnabled || settings.focusDays.isEmpty()) return false
        val start = settings.focusStartMinute
        val end = settings.focusEndMinute
        if (start == end) return false
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek
        return if (start < end) {
            today in settings.focusDays && minute >= start && minute < end
        } else {
            // Overnight window, e.g. 22:00 - 06:00. The early-morning part belongs to the previous day.
            (minute >= start && today in settings.focusDays) ||
                (minute < end && today.minus(1) in settings.focusDays)
        }
    }
}
