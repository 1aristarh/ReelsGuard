package com.petar.reelsguard

/** The user-editable limits, in minutes (passes: count per day). */
data class Config(
    val blockAll: Boolean,
    val budgetMin: Int,
    val windowMin: Int,
    val passes: Int,
    val passMin: Int,
) {
    companion object {
        val DEFAULT = Config(blockAll = false, budgetMin = 10, windowMin = 60, passes = 3, passMin = 2)

        /** True when [new] lets the user do more than [cur]. "Up" is the loosening direction for every lever. */
        fun isLoose(cur: Config, new: Config): Boolean {
            if (new.blockAll) return false
            if (cur.blockAll) return true
            return new.budgetMin > cur.budgetMin || new.windowMin > cur.windowMin ||
                new.passes > cur.passes || (new.passes > 0 && new.passMin > cur.passMin)
        }
    }
}

enum class Phase { SETUP, LOCKED, UNLOCKED, PENDING, READY }

/**
 * Setup lock: after the setup is approved the limits are locked for [lockDays]. While locked they can only
 * get stricter. Once the lock has ended, loosening is a request that takes [WAIT_MS] before it can be applied.
 * Pure logic, no Android dependencies.
 */
class SetupLock(
    var approved: Boolean = false,
    var lockedAt: Long = 0L,
    var lockDays: Int = 7,
    var pending: Config? = null,
    var pendingAt: Long = 0L,
) {
    fun lockEnd() = lockedAt + lockDays * DAY_MS

    fun phase(now: Long): Phase = when {
        !approved -> Phase.SETUP
        pending != null -> if (now >= pendingAt + WAIT_MS) Phase.READY else Phase.PENDING
        now < lockEnd() -> Phase.LOCKED
        else -> Phase.UNLOCKED
    }

    fun approve(now: Long, days: Int) {
        approved = true; lockedAt = now; lockDays = days; pending = null
    }

    /** Only possible once the lock has ended and nothing is pending. */
    fun requestChange(cfg: Config, now: Long): Boolean {
        if (phase(now) != Phase.UNLOCKED) return false
        pending = cfg; pendingAt = now
        return true
    }

    fun cancelPending() { pending = null }

    fun waitLeftMs(now: Long) = (pendingAt + WAIT_MS - now).coerceAtLeast(0L)

    /** Returns the requested config once the wait is over, and starts a new lock of the same length. */
    fun applyPending(now: Long): Config? {
        if (phase(now) != Phase.READY) return null
        val c = pending
        pending = null; lockedAt = now
        return c
    }

    companion object {
        const val DAY_MS = 86_400_000L
        const val WAIT_MS = DAY_MS
    }
}
