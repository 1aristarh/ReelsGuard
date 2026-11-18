package com.petar.reelsguard

import android.content.Context

/** One shared timer per process, so the service and the activity see the same state. */
object Store {
    private const val P = "s"
    private var timer: CooldownTimer? = null

    /** Last Reels/Shorts surface the service detected, shown in the app so detection can be checked. */
    @Volatile var lastHit: String? = null
    @Volatile var lastHitAt = 0L

    fun get(c: Context): CooldownTimer = timer ?: synchronized(this) {
        timer ?: load(c.applicationContext).also { timer = it }
    }

    private fun load(c: Context): CooldownTimer {
        val p = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        return CooldownTimer(
            budgetMs = p.getLong("budgetMs", BuildConfig.BUDGET_MS),
            cooldownMs = 0L, // cooldown lasts until the session window ends
            windowMs = p.getLong("windowMs", BuildConfig.WINDOW_MS),
            blockAll = p.getBoolean("blockAll", false),
            maxPasses = p.getInt("maxPasses", CooldownTimer.MAX_PASSES),
            passMs = p.getLong("passMs", CooldownTimer.PASS_MS),
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
