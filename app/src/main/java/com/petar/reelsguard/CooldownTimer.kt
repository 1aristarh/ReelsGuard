package com.petar.reelsguard

import java.util.TimeZone

/** Pure timer logic: no Android dependencies, so it is unit-testable. */
class CooldownTimer(
    var budgetMs: Long,
    /** Cooldown length after the budget is used up; 0 or less = until the current session window ends. */
    var cooldownMs: Long,
    private val maxGapMs: Long = 10_000L,
    var usedMs: Long = 0L,
    var cooldownUntil: Long = 0L,
    var passUntil: Long = 0L,
    var passDay: Long = -1L,
    var passesUsed: Int = 0,
    var totalMs: Long = 0L,
    var totalDay: Long = -1L,
    var windowStart: Long = -1L,
    var windowMs: Long = WINDOW_MS,
    var passMs: Long = PASS_MS,
    var maxPasses: Int = MAX_PASSES,
    /** No sessions: Reels/Shorts are blocked outright. */
    var blockAll: Boolean = false,
) {
    private var last = -1L

    /** Call while the Reels/Shorts surface is visible. Returns true if it must be blocked now. */
    fun onReelsVisible(now: Long): Boolean {
        if (blockAll) { last = -1L; return true }
        var delta = if (last >= 0L && now - last in 0..maxGapMs) now - last else 0L
        if (cooldownUntil > 0L) {
            if (now < cooldownUntil) {
                if (now >= passUntil) { last = -1L; return true }
                addToday(now, delta); last = now // pass: let through, counted in the daily total only
                return false
            }
            cooldownUntil = 0L; usedMs = 0L; windowStart = -1L; delta = 0L
        }
        // Partial usage expires one hour after it started; a full budget still triggers the cooldown.
        if (windowStart < 0L || now - windowStart >= windowMs) { usedMs = 0L; windowStart = now }
        usedMs += delta
        addToday(now, delta)
        last = now
        if (usedMs >= budgetMs) {
            cooldownUntil = if (cooldownMs > 0L) now + cooldownMs else maxOf(windowStart + windowMs, now + MIN_COOLDOWN_MS)
            usedMs = 0L; windowStart = -1L; last = -1L
            return true
        }
        return false
    }

    private fun addToday(now: Long, delta: Long) {
        val day = dayOf(now)
        if (day != totalDay) { totalDay = day; totalMs = 0L }
        totalMs += delta
    }

    /** Total time spent on Reels/Shorts today, including passes. */
    fun todayMs(now: Long) = if (dayOf(now) == totalDay) totalMs else 0L

    fun onLeftReels() { last = -1L }

    /** True while a session cooldown is running (settings are locked then). */
    fun inCooldown(now: Long) = cooldownUntil > now

    fun isBlocked(now: Long) = blockAll || cooldownUntil > now

    /** Full length of a cooldown, for progress display. */
    fun cooldownSpanMs() = if (cooldownMs > 0L) cooldownMs else windowMs

    fun cooldownLeftMs(now: Long) = (cooldownUntil - now).coerceAtLeast(0L)

    fun budgetLeftMs(now: Long): Long {
        val expired = windowStart >= 0L && now - windowStart >= windowMs
        return if (expired) budgetMs else (budgetMs - usedMs).coerceAtLeast(0L)
    }

    /** Time until partial usage is wiped (0 when nothing is pending). */
    fun windowLeftMs(now: Long): Long =
        if (windowStart < 0L || usedMs <= 0L) 0L else (windowStart + windowMs - now).coerceAtLeast(0L)

    fun passActive(now: Long) = isBlocked(now) && now < passUntil

    fun passLeftMs(now: Long) = (passUntil - now).coerceAtLeast(0L)

    fun passesLeft(now: Long) = if (dayOf(now) != passDay) maxPasses else (maxPasses - passesUsed).coerceAtLeast(0)

    /** Starts a short pass during a cooldown, if any are left today. */
    fun grantPass(now: Long): Boolean {
        if (blockAll || !isBlocked(now) || passActive(now) || passesLeft(now) <= 0) return false
        val day = dayOf(now)
        if (day != passDay) { passDay = day; passesUsed = 0 }
        passesUsed++
        passUntil = now + passMs
        return true
    }

    private fun dayOf(now: Long) = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

    companion object {
        const val WINDOW_MS = 3_600_000L
        const val MIN_COOLDOWN_MS = 1_000L
        const val PASS_MS = 120_000L
        const val MAX_PASSES = 3
    }
}

