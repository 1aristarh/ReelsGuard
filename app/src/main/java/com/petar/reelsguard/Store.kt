package com.petar.reelsguard

import android.content.Context

/** One shared timer per process, so the service and the activity see the same state. */
object Store {
    private const val P = "s"
    private var timer: CooldownTimer? = null
    private var setupLock: SetupLock? = null

    /** Last Reels/Shorts surface the service detected, shown in the app so detection can be checked. */
    @Volatile var lastHit: String? = null
    @Volatile var lastHitAt = 0L

    fun get(c: Context): CooldownTimer = timer ?: synchronized(this) {
        timer ?: load(c.applicationContext).also { timer = it }
    }

    fun lock(c: Context): SetupLock = setupLock ?: synchronized(this) {
        setupLock ?: loadLock(c.applicationContext).also { setupLock = it }
    }

    /** The limits currently in force. */
    fun config(t: CooldownTimer) = Config(t.blockAll, (t.budgetMs / 60_000L).toInt(), (t.windowMs / 60_000L).toInt(), t.maxPasses, (t.passMs / 60_000L).toInt())

    fun applyConfig(t: CooldownTimer, c: Config) {
        t.blockAll = c.blockAll
        t.budgetMs = c.budgetMin * 60_000L
        t.windowMs = c.windowMin * 60_000L
        t.maxPasses = c.passes
        t.passMs = c.passMin * 60_000L
    }

    private fun loadLock(c: Context): SetupLock {
        val p = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        val pending = if (p.getBoolean("hasPending", false)) Config(
            p.getBoolean("pBlock", false), p.getInt("pBudget", 10), p.getInt("pWindow", 60), p.getInt("pPasses", 3), p.getInt("pPassLen", 2),
        ) else null
        return SetupLock(
            approved = p.getBoolean("approved", false), lockedAt = p.getLong("lockedAt", 0L),
            lockDays = p.getInt("lockDays", 7), pending = pending, pendingAt = p.getLong("pendingAt", 0L),
        )
    }

    fun saveLock(c: Context, l: SetupLock) {
        val e = c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putBoolean("approved", l.approved).putLong("lockedAt", l.lockedAt)
            .putInt("lockDays", l.lockDays).putLong("pendingAt", l.pendingAt)
        val pc = l.pending
        e.putBoolean("hasPending", pc != null)
        if (pc != null) e.putBoolean("pBlock", pc.blockAll).putInt("pBudget", pc.budgetMin)
            .putInt("pWindow", pc.windowMin).putInt("pPasses", pc.passes).putInt("pPassLen", pc.passMin)
        e.apply()
    }

    private fun load(c: Context): CooldownTimer {
        val p = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        return CooldownTimer(
            budgetMs = p.getLong("budgetMs", Config.DEFAULT.budgetMin * 60_000L),
            cooldownMs = 0L, // cooldown lasts until the session window ends
            windowMs = p.getLong("windowMs", Config.DEFAULT.windowMin * 60_000L),
            blockAll = p.getBoolean("blockAll", false),
            maxPasses = p.getInt("maxPasses", Config.DEFAULT.passes),
            passMs = p.getLong("passMs", Config.DEFAULT.passMin * 60_000L),
            usedMs = p.getLong("used", 0L), cooldownUntil = p.getLong("until", 0L),
            passUntil = p.getLong("passUntil", 0L), passDay = p.getLong("passDay", -1L),
            passesUsed = p.getInt("passesUsed", 0),
            totalMs = p.getLong("totalMs", 0L), totalDay = p.getLong("totalDay", -1L),
            windowStart = p.getLong("windowStart", -1L),
        )
    }

    fun save(c: Context, t: CooldownTimer) {
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putLong("used", t.usedMs).putLong("until", t.cooldownUntil)
            .putLong("passUntil", t.passUntil).putLong("passDay", t.passDay)
            .putInt("passesUsed", t.passesUsed)
            .putLong("totalMs", t.totalMs).putLong("totalDay", t.totalDay)
            .putLong("windowStart", t.windowStart)
            .putLong("budgetMs", t.budgetMs).putLong("windowMs", t.windowMs)
            .putBoolean("blockAll", t.blockAll)
            .putInt("maxPasses", t.maxPasses).putLong("passMs", t.passMs).apply()
    }
}
