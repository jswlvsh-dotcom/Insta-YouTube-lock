package com.jaivish.gowork.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusScheduleTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // 19 Oct 2026 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private val daytime = Settings(
        focusEnabled = true,
        focusStartMinute = 10 * 60,
        focusEndMinute = 18 * 60,
        focusDays = Settings.WORK_DAYS,
    )

    @Test
    fun daytimeWindow() {
        assertTrue(FocusSchedule.isActive(daytime, at(21, 12)))
        assertTrue(FocusSchedule.isActive(daytime, at(21, 10)))
        assertFalse(FocusSchedule.isActive(daytime, at(21, 9, 59)))
        assertFalse(FocusSchedule.isActive(daytime, at(21, 18)))
        assertFalse("Sunday is a day off", FocusSchedule.isActive(daytime, at(25, 12)))
    }

    @Test
    fun overnightWindowBelongsToTheStartDay() {
        val night = daytime.copy(
            focusStartMinute = 22 * 60,
            focusEndMinute = 6 * 60,
            focusDays = setOf(DayOfWeek.FRIDAY),
        )
        assertTrue(FocusSchedule.isActive(night, at(23, 23))) // Friday night
        assertTrue(FocusSchedule.isActive(night, at(24, 5))) // early Saturday
        assertFalse(FocusSchedule.isActive(night, at(24, 23))) // Saturday night
        assertFalse(FocusSchedule.isActive(night, at(23, 5))) // early Friday belongs to Thursday
    }

    @Test
    fun disabledOrEmptyNeverLocks() {
        assertFalse(FocusSchedule.isActive(daytime.copy(focusEnabled = false), at(21, 12)))
        assertFalse(FocusSchedule.isActive(daytime.copy(focusDays = emptySet()), at(21, 12)))
        assertFalse(FocusSchedule.isActive(daytime.copy(focusEndMinute = 10 * 60), at(21, 12)))
    }
}
