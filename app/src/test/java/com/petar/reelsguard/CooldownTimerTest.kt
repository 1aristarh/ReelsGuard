package com.petar.reelsguard

import org.junit.Assert.*
import org.junit.Test

class CooldownTimerTest {
    private fun t() = CooldownTimer(budgetMs = 10_000, cooldownMs = 30_000, maxGapMs = 2_000)

    @Test fun accruesAndBlocksAtBudget() {
        val x = t(); var now = 1_000L
        assertFalse(x.onReelsVisible(now))
        repeat(9) { now += 1_000; assertFalse(x.onReelsVisible(now)) }
        now += 1_000
        assertTrue(x.onReelsVisible(now))
        assertTrue(x.isBlocked(now))
    }

    @Test fun gapNotCounted() {
        val x = t()
        x.onReelsVisible(1_000); x.onReelsVisible(2_000)
        x.onReelsVisible(60_000) // long gap: not counted
        assertEquals(9_000L, x.budgetLeftMs(0L))
    }

    @Test fun blockedDuringCooldownAndResetsAfter() {
        val x = CooldownTimer(1_000, 30_000, 5_000)
        x.onReelsVisible(0); assertTrue(x.onReelsVisible(1_000))
        assertTrue(x.onReelsVisible(20_000))
        assertFalse(x.onReelsVisible(31_001))
        assertEquals(1_000L, x.budgetLeftMs(0L))
    }

    @Test fun leavingStopsAccrual() {
        val x = t()
        x.onReelsVisible(0); x.onLeftReels(); x.onReelsVisible(1_500)
        assertEquals(10_000L, x.budgetLeftMs(0L))
    }

    @Test fun passAllowsReelsUncountedDuringCooldown() {
        val x = CooldownTimer(1_000, 600_000, 5_000, passMs = 10_000, maxPasses = 2)
        x.onReelsVisible(1_000); assertTrue(x.onReelsVisible(2_000))
        assertFalse(x.grantPass(1_500_000_000_000L)) // not in cooldown
        assertTrue(x.grantPass(3_000))
        assertFalse(x.onReelsVisible(5_000))  // pass: let through
        assertTrue(x.onReelsVisible(14_000))  // pass over: blocked again
        assertEquals(1_000L, x.budgetLeftMs(0L))
    }

    @Test fun passesLimitedPerDay() {
        val x = CooldownTimer(1_000, 600_000, 5_000, passMs = 1_000, maxPasses = 2)
        val t0 = 1_700_000_000_000L
        x.onReelsVisible(t0); x.onReelsVisible(t0 + 1_000)
        assertTrue(x.grantPass(t0 + 2_000))
        assertTrue(x.grantPass(t0 + 4_000))
        assertFalse(x.grantPass(t0 + 6_000))
        assertEquals(0, x.passesLeft(t0 + 6_000))
    }

    @Test fun dailyTotalCountsBudgetAndPassTimeButNotBlocked() {
        val x = CooldownTimer(5_000, 600_000, 5_000, passMs = 4_000, maxPasses = 2)
        val t0 = 1_700_000_000_000L
        x.onReelsVisible(t0); x.onReelsVisible(t0 + 5_000)
        assertEquals(5_000L, x.todayMs(t0 + 5_000))
        x.onReelsVisible(t0 + 6_000)
        assertTrue(x.grantPass(t0 + 7_000))
        x.onReelsVisible(t0 + 8_000); x.onReelsVisible(t0 + 10_000)
        assertEquals(7_000L, x.todayMs(t0 + 10_000))
        assertEquals(0L, x.todayMs(t0 + 2 * 86_400_000L))
    }

    @Test fun partialUsageResetsAfterAnHour() {
        val x = CooldownTimer(600_000, 1_800_000, maxGapMs = 5_000, windowMs = 3_600_000)
        val t0 = 1_700_000_000_000L
        var now = t0
        x.onReelsVisible(now)
        repeat(59) { now += 10_000; x.onReelsVisible(now - 5_000); x.onReelsVisible(now) } // ~9:50 used
        assertFalse(x.isBlocked(now))
        assertTrue(x.budgetLeftMs(now) < 20_000L)
        assertEquals(600_000L, x.budgetLeftMs(t0 + 3_600_000L))   // display shows full budget
        assertFalse(x.onReelsVisible(t0 + 3_700_000L))            // back later: fresh budget
        assertEquals(600_000L, x.budgetLeftMs(t0 + 3_700_000L))
    }

    @Test fun fullBudgetStillGivesNormalCooldown() {
        val x = CooldownTimer(10_000, 30_000, maxGapMs = 2_000, windowMs = 3_600_000)
        val t0 = 1_700_000_000_000L
        var now = t0
        x.onReelsVisible(now)
        repeat(9) { now += 1_000; assertFalse(x.onReelsVisible(now)) }
        now += 1_000
        assertTrue(x.onReelsVisible(now))
        assertEquals(30_000L, x.cooldownLeftMs(now))
        assertFalse(x.onReelsVisible(now + 30_001L))
    }
}
