package com.petar.reelsguard

/** Every number the user can set with the throttle lever, with its allowed stops. */
enum class Field(val label: String, private val base: IntArray, private val debugExtra: Int? = null) {
    BUDGET("Scrolling per session", intArrayOf(5, 10, 15, 20, 30, 45, 60), 1),
    WINDOW("Session length", intArrayOf(30, 60, 120, 180, 240, 360, 480, 720, 960, 1200, 1440), 3),
    PASSES("\"I need to see something\"", intArrayOf(0, 1, 2, 3, 4, 5, 6, 8, 10)),
    PASS_LEN("Length of each pass", intArrayOf(1, 2, 3, 5, 10)),
    LOCK_DAYS("Lock settings for", intArrayOf(1, 3, 7, 14, 30));

    /** Debug builds get one extra short stop so the limits can be tested quickly. */
    val stops: IntArray
        get() = if (BuildConfig.DEBUG && debugExtra != null) intArrayOf(debugExtra) + base else base

    fun fmt(v: Int): String = when (this) {
        PASSES -> if (v == 0) "None" else "$v per day"
        LOCK_DAYS -> if (v == 1) "1 day" else "$v days"
        else -> Ui.fmtMin(v)
    }

    fun get(c: Config, lockDays: Int = 0): Int = when (this) {
        BUDGET -> c.budgetMin
        WINDOW -> c.windowMin
        PASSES -> c.passes
        PASS_LEN -> c.passMin
        LOCK_DAYS -> lockDays
    }

    /** Index of the stop nearest to [v]. */
    fun nearest(v: Int): Int = stops.indices.minByOrNull { Math.abs(stops[it] - v) } ?: 0

    /** Index of the highest stop that is <= [v]. */
    fun floorIdx(v: Int): Int = stops.indices.lastOrNull { stops[it] <= v } ?: 0
}

/** Sets one field, keeping the scrolling budget shorter than the session. */
fun Config.with(f: Field, v: Int): Config {
    var c = when (f) {
        Field.BUDGET -> copy(budgetMin = v)
        Field.WINDOW -> copy(windowMin = v)
        Field.PASSES -> copy(passes = v)
        Field.PASS_LEN -> copy(passMin = v)
        Field.LOCK_DAYS -> this
    }
    val b = Field.BUDGET.stops
    val w = Field.WINDOW.stops
    if (f == Field.WINDOW && c.budgetMin >= c.windowMin) c = c.copy(budgetMin = b.lastOrNull { it < c.windowMin } ?: b.first())
    if (f == Field.BUDGET && c.budgetMin >= c.windowMin) c = c.copy(windowMin = w.firstOrNull { it > c.budgetMin } ?: w.last())
    return c
}
