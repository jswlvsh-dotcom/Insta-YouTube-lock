package com.jaivish.gowork.core

import com.jaivish.gowork.core.UsageAggregator.ACTIVITY_PAUSED
import com.jaivish.gowork.core.UsageAggregator.ACTIVITY_RESUMED
import com.jaivish.gowork.core.UsageAggregator.ACTIVITY_STOPPED
import com.jaivish.gowork.core.UsageAggregator.RawEvent
import com.jaivish.gowork.core.UsageAggregator.SCREEN_NON_INTERACTIVE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAggregatorTest {

    private val ig = KnownApps.INSTAGRAM
    private val yt = KnownApps.YOUTUBE

    private fun ev(pkg: String, cls: String, type: Int, at: Long) = RawEvent(pkg, cls, type, at)

    @Test
    fun sumsResumePausePairs() {
        val result = UsageAggregator.aggregate(
            listOf(
                ev(ig, "Main", ACTIVITY_RESUMED, 1_000),
                ev(ig, "Main", ACTIVITY_PAUSED, 61_000),
                ev(yt, "Watch", ACTIVITY_RESUMED, 70_000),
                ev(yt, "Watch", ACTIVITY_PAUSED, 100_000),
            ),
            startMs = 0,
            endMs = 200_000,
        )
        assertEquals(60_000L, result[ig])
        assertEquals(30_000L, result[yt])
    }

    @Test
    fun activitySwitchInsideTheSameAppStaysCounted() {
        val result = UsageAggregator.aggregate(
            listOf(
                ev(ig, "A", ACTIVITY_RESUMED, 0),
                ev(ig, "A", ACTIVITY_PAUSED, 10_000),
                ev(ig, "B", ACTIVITY_RESUMED, 10_100),
                ev(ig, "A", ACTIVITY_STOPPED, 10_500),
                ev(ig, "B", ACTIVITY_PAUSED, 20_000),
            ),
            startMs = 0,
            endMs = 30_000,
        )
        assertEquals(19_900L, result[ig])
    }

    @Test
    fun screenOffClosesOpenSessions() {
        val result = UsageAggregator.aggregate(
            listOf(
                ev(ig, "Main", ACTIVITY_RESUMED, 0),
                RawEvent("android", null, SCREEN_NON_INTERACTIVE, 5_000),
                ev(ig, "Main", ACTIVITY_PAUSED, 9_000),
            ),
            startMs = 0,
            endMs = 30_000,
        )
        assertEquals(5_000L, result[ig])
    }

    @Test
    fun appStillOnScreenIsCountedUntilNow() {
        val result = UsageAggregator.aggregate(
            listOf(ev(yt, "Watch", ACTIVITY_RESUMED, 5_000)),
            startMs = 0,
            endMs = 15_000,
        )
        assertEquals(10_000L, result[yt])
    }

    @Test
    fun pauseWithoutResumeIsIgnored() {
        val result = UsageAggregator.aggregate(
            listOf(ev(ig, "Main", ACTIVITY_PAUSED, 5_000)),
            startMs = 0,
            endMs = 15_000,
        )
        assertNull(result[ig])
    }

    @Test
    fun eventsAreSortedBeforeAggregating() {
        val result = UsageAggregator.aggregate(
            listOf(
                ev(ig, "Main", ACTIVITY_PAUSED, 8_000),
                ev(ig, "Main", ACTIVITY_RESUMED, 2_000),
            ),
            startMs = 0,
            endMs = 15_000,
        )
        assertEquals(6_000L, result[ig])
    }
}
