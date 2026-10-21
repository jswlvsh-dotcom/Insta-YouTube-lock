package com.jaivish.gowork.engine

import com.jaivish.gowork.core.AppRule
import com.jaivish.gowork.core.KnownApps.INSTAGRAM
import com.jaivish.gowork.core.KnownApps.YOUTUBE
import com.jaivish.gowork.core.MemoryStatsRepository
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.core.dayKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusEngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    // Wednesday 21 Oct 2026, 12:00.
    private var now = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    private var settings = Settings(onboarded = true, mindfulOpen = false)
    private val stats = MemoryStatsRepository()
    private val host = FakeHost()
    private val engine = FocusEngine(host, stats, { settings }, { now }, { zone })

    private val today get() = dayKey(now, zone)

    private class FakeHost : EngineHost {
        var current: LockRequest? = null
        var shownCount = 0
        var homeCount = 0
        var backCount = 0

        override fun showLock(request: LockRequest, fresh: Boolean) {
            current = request
            shownCount++
        }

        override fun hideLock() {
            current = null
        }

        override fun goHome() {
            homeCount++
        }

        override fun goBack() {
            backCount++
        }

        override fun labelFor(pkg: String) = pkg
    }

    private fun advance(seconds: Int) = repeat(seconds) {
        now += 1_000
        engine.tick()
    }

    private fun open(pkg: String) {
        engine.tick()
        engine.onForegroundChanged(pkg)
    }

    @Test
    fun reminderAppearsAfterTwoMinutesOfUse() {
        open(INSTAGRAM)
        advance(119)
        assertNull(host.current)
        advance(1)
        assertEquals(LockKind.NUDGE, host.current?.kind)
        assertEquals(1, stats.get(today, INSTAGRAM).nudges)
    }

    @Test
    fun timeIsNotCountedWhileTheLockIsUp() {
        open(INSTAGRAM)
        advance(120)
        val usage = stats.get(today, INSTAGRAM).usageMs
        advance(45)
        assertEquals(usage, stats.get(today, INSTAGRAM).usageMs)
    }

    @Test
    fun continueNeedsTheWaitThenTimerRestartsWithLongerWait() {
        open(INSTAGRAM)
        advance(120)
        val first = host.current!!
        engine.onSecondary(first.id)
        assertNotNull("continue must not work before the countdown ends", host.current)

        advance(first.waitSeconds)
        engine.onSecondary(first.id)
        assertNull(host.current)

        advance(119)
        assertNull(host.current)
        advance(1)
        val second = host.current!!
        assertEquals(LockKind.NUDGE, second.kind)
        assertEquals(2, second.nudgeNumber)
        assertEquals(first.waitSeconds + FocusEngine.ESCALATE_STEP_SECONDS, second.waitSeconds)
    }

    @Test
    fun goingToWorkSendsUserHomeAndCountsIt() {
        open(INSTAGRAM)
        advance(120)
        engine.onPrimary(host.current!!.id)
        assertNull(host.current)
        assertEquals(1, host.homeCount)
        assertEquals(1, stats.get(today, INSTAGRAM).listened)
    }

    @Test
    fun comingStraightBackAfterGoingToWorkShowsTheReminderAgain() {
        open(INSTAGRAM)
        advance(120)
        engine.onPrimary(host.current!!.id)
        assertNull(host.current)

        now += 30_000
        engine.onForegroundChanged(INSTAGRAM)
        assertEquals(LockKind.NUDGE, host.current?.kind)

        engine.onPrimary(host.current!!.id)
        engine.onForegroundChanged("com.android.launcher")
        now += FocusEngine.SESSION_BREAK_MS
        engine.tick()
        engine.onForegroundChanged(INSTAGRAM)
        assertNull("a real break clears the reminder", host.current)
    }

    @Test
    fun switchingAwayDoesNotEscapeAPendingReminder() {
        open(INSTAGRAM)
        advance(120)
        engine.onForegroundChanged("com.android.launcher")
        assertNull(host.current)
        now += 20_000
        engine.onForegroundChanged(INSTAGRAM)
        assertEquals(LockKind.NUDGE, host.current?.kind)
    }

    @Test
    fun timerIsSharedAcrossWatchedAppsAndSurvivesShortBreaks() {
        open(INSTAGRAM)
        advance(90)
        engine.onForegroundChanged("com.android.launcher")
        now += 60_000
        engine.tick()
        engine.onForegroundChanged(YOUTUBE)
        advance(29)
        assertNull(host.current)
        advance(1)
        assertEquals(LockKind.NUDGE, host.current?.kind)
        assertEquals(YOUTUBE, host.current?.packageName)
    }

    @Test
    fun longBreakStartsNewSessionWithMindfulPause() {
        settings = settings.copy(mindfulOpen = true)
        open(INSTAGRAM)
        val pause = host.current!!
        assertEquals(LockKind.OPEN_PAUSE, pause.kind)
        advance(pause.waitSeconds)
        engine.onSecondary(pause.id)
        assertNull(host.current)

        advance(60)
        engine.onForegroundChanged("com.android.launcher")
        now += 4 * 60_000
        engine.tick()
        engine.onForegroundChanged(INSTAGRAM)
        assertEquals(LockKind.OPEN_PAUSE, host.current?.kind)
    }

    @Test
    fun dailyLimitLocksAndEmergencyGivesFiveMinutes() {
        settings = settings.copy(
            nudgeIntervalMinutes = 30,
            rules = listOf(AppRule(INSTAGRAM, dailyLimitMinutes = 1)),
        )
        open(INSTAGRAM)
        advance(60)
        val lock = host.current!!
        assertEquals(LockKind.LIMIT, lock.kind)
        assertTrue(stats.get(today, INSTAGRAM).limitHit)

        engine.onSecondary(lock.id)
        assertEquals("emergency needs the 30 s wait", LockKind.LIMIT, host.current?.kind)
        advance(FocusEngine.EMERGENCY_WAIT_SECONDS)
        engine.onSecondary(lock.id)
        assertNull(host.current)

        advance(5 * 60 - 1)
        assertNull(host.current)
        advance(1)
        assertEquals(LockKind.LIMIT, host.current?.kind)
        assertEquals(FocusEngine.MAX_EMERGENCIES_PER_DAY - 1, host.current?.emergenciesLeft)
    }

    @Test
    fun phoneScreenTimeCountsTowardsTheLimit() {
        settings = settings.copy(rules = listOf(AppRule(INSTAGRAM, dailyLimitMinutes = 30)))
        engine.updateSystemUsage(today, mapOf(INSTAGRAM to 31 * 60_000L))
        open(INSTAGRAM)
        assertEquals(LockKind.LIMIT, host.current?.kind)
    }

    @Test
    fun focusHoursLockOnlyInsideTheWindow() {
        settings = settings.copy(
            focusEnabled = true,
            focusStartMinute = 11 * 60,
            focusEndMinute = 13 * 60,
            focusDays = DayOfWeek.values().toSet(),
        )
        open(INSTAGRAM)
        assertEquals(LockKind.FOCUS, host.current?.kind)
        engine.onPrimary(host.current!!.id)
        assertEquals(1, host.homeCount)

        now += 61 * 60_000 // 13:01
        engine.onForegroundChanged(INSTAGRAM)
        assertNull(host.current)
    }

    @Test
    fun sprintLocksUntilItEnds() {
        settings = settings.copy(sprintEndsAt = now + 10 * 60_000)
        open(YOUTUBE)
        assertEquals(LockKind.SPRINT, host.current?.kind)
        advance(10 * 60)
        assertNull(host.current)
    }

    @Test
    fun reelsAreBlockedOnlyWhenTurnedOn() {
        open(INSTAGRAM)
        engine.onShortFormChanged(INSTAGRAM, true)
        assertNull(host.current)

        settings = settings.copy(rules = listOf(AppRule(INSTAGRAM, blockShortForm = true)))
        engine.onShortFormChanged(INSTAGRAM, false)
        engine.onShortFormChanged(INSTAGRAM, true)
        assertEquals(LockKind.SHORT_FORM, host.current?.kind)

        engine.onPrimary(host.current!!.id)
        assertEquals(1, host.backCount)
        assertNull(host.current)

        engine.onShortFormChanged(INSTAGRAM, true)
        assertNull("detection is muted while the back animation runs", host.current)
        now += 2_000
        engine.onShortFormChanged(INSTAGRAM, true)
        assertEquals(LockKind.SHORT_FORM, host.current?.kind)
    }

    @Test
    fun reelTimeIsTrackedSeparately() {
        open(YOUTUBE)
        engine.onShortFormChanged(YOUTUBE, true)
        advance(30)
        engine.onShortFormChanged(YOUTUBE, false)
        advance(30)
        val counters = stats.get(today, YOUTUBE)
        assertEquals(60_000L, counters.usageMs)
        assertEquals(30_000L, counters.shortFormMs)
    }

    @Test
    fun screenOffStopsCountingAndHidesTheLock() {
        open(INSTAGRAM)
        advance(60)
        engine.onScreenInteractive(false)
        advance(600)
        assertEquals(60_000L, stats.get(today, INSTAGRAM).usageMs)

        engine.onScreenInteractive(true)
        advance(120)
        assertEquals(LockKind.NUDGE, host.current?.kind)
        engine.onScreenInteractive(false)
        assertNull(host.current)
        now += 10_000
        engine.onScreenInteractive(true)
        assertEquals("a pending reminder survives a quick screen off", LockKind.NUDGE, host.current?.kind)
    }

    @Test
    fun protectionOffOnlyCountsTime() {
        settings = settings.copy(protectionEnabled = false)
        open(INSTAGRAM)
        advance(300)
        assertNull(host.current)
        assertEquals(0, host.shownCount)
        assertEquals(300_000L, stats.get(today, INSTAGRAM).usageMs)
    }

    @Test
    fun opensAreCountedButQuickReturnsAreNot() {
        open(INSTAGRAM)
        engine.onForegroundChanged("com.android.launcher")
        now += 3_000
        engine.onForegroundChanged(INSTAGRAM)
        assertEquals(1, stats.get(today, INSTAGRAM).opens)

        engine.onForegroundChanged("com.android.launcher")
        now += 60_000
        engine.onForegroundChanged(INSTAGRAM)
        assertEquals(2, stats.get(today, INSTAGRAM).opens)
    }

    @Test
    fun appsThatAreNotWatchedAreIgnored() {
        open("com.whatsapp")
        advance(600)
        assertNull(host.current)
        assertEquals(0L, stats.get(today, "com.whatsapp").usageMs)
    }
}
