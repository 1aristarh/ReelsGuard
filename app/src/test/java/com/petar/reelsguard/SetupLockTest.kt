package com.petar.reelsguard

import org.junit.Assert.*
import org.junit.Test

class SetupLockTest {
    private val day = SetupLock.DAY_MS
    private val base = Config(false, 10, 60, 3, 2)

    @Test fun phasesFollowTheClock() {
        val l = SetupLock()
        assertEquals(Phase.SETUP, l.phase(0))
        l.approve(1_000L, 7)
        assertEquals(Phase.LOCKED, l.phase(1_000L + 6 * day))
        assertEquals(Phase.UNLOCKED, l.phase(1_000L + 7 * day))
    }

    @Test fun looseningIsOnlyUp() {
        assertFalse(Config.isLoose(base, base.copy(budgetMin = 5)))
        assertTrue(Config.isLoose(base, base.copy(budgetMin = 15)))
        assertTrue(Config.isLoose(base, base.copy(windowMin = 120)))
        assertTrue(Config.isLoose(base, base.copy(passes = 5)))
        assertTrue(Config.isLoose(base, base.copy(passMin = 5)))
        assertFalse(Config.isLoose(base, base.copy(blockAll = true)))
        assertTrue(Config.isLoose(base.copy(blockAll = true), base))
        assertFalse(Config.isLoose(base, base.copy(passes = 0, passMin = 10)))
    }

    @Test fun changeRequestNeedsEndedLockAndWaits() {
        val l = SetupLock(); l.approve(0L, 1)
        val want = base.copy(budgetMin = 20)
        assertFalse(l.requestChange(want, day / 2)) // still locked
        assertTrue(l.requestChange(want, day))
        assertEquals(Phase.PENDING, l.phase(day + 1))
        assertNull(l.applyPending(day + 1))          // too early
        assertEquals(Phase.READY, l.phase(2 * day))
        assertEquals(want, l.applyPending(2 * day))
        assertEquals(Phase.LOCKED, l.phase(2 * day + 1)) // a new lock started
    }

    @Test fun cancelPendingReturnsToUnlocked() {
        val l = SetupLock(); l.approve(0L, 1)
        l.requestChange(base, day)
        l.cancelPending()
        assertEquals(Phase.UNLOCKED, l.phase(day + 5))
    }

    @Test fun withKeepsBudgetBelowSession() {
        assertEquals(20, base.copy(budgetMin = 30, windowMin = 60).with(Field.WINDOW, 30).budgetMin)
        assertTrue(base.with(Field.BUDGET, 60).windowMin > 60)
    }
}
