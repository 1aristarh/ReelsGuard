package com.petar.reelsguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private companion object {
        const val BG = 0xFF000000.toInt()
        const val CARD = 0xFF161616.toInt()
        const val MUTED = 0xFF8C8C8C.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RED = 0xFFE5252A.toInt()
        const val GREY = 0xFFB0B0B0.toInt()
        const val MIN = 60_000L
        val BUDGETS = listOf(5, 10, 15, 20, 30).map { it * MIN }
        val WINDOWS = listOf(30, 60, 120, 180).map { it * MIN }
    }

    private lateinit var ring: RingView
    private lateinit var big: TextView
    private lateinit var label: TextView
    private lateinit var resets: TextView
    private lateinit var seen: TextView
    private lateinit var today: TextView
    private lateinit var peek: TextView
    private lateinit var guard: TextView
    private lateinit var modeRow: TextView
    private lateinit var budgetRow: TextView
    private lateinit var windowRow: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun pill(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        ring = RingView(this)
        big = text(48f, Color.WHITE, true)
        label = text(12f, MUTED, true).apply { letterSpacing = 0.15f }
        resets = text(13f, MUTED)
        seen = text(12f, MUTED).apply { setPadding(0, dp(8), 0, 0) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(big); addView(label)
            addView(resets, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        val ringBox = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(center, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        val todayLabel = text(11f, MUTED, true).apply { text = "TODAY ON REELS"; letterSpacing = 0.15f }
        today = text(26f, Color.WHITE, true)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = pill(CARD, 20)
            setPadding(dp(24), dp(18), dp(24), dp(18))
            addView(todayLabel); addView(today); addView(seen)
        }

        peek = text(16f, Color.WHITE, true).apply {
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setOnClickListener {
                val t = Store.get(this@MainActivity)
                if (t.grantPass(System.currentTimeMillis())) {
                    Store.save(this@MainActivity, t)
                    moveTaskToBack(true) // back to whatever app (chat) the reel was sent in
                }
                render()
            }
        }

        guard = text(14f, MUTED).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        // Settings: tap a row to change it. Locked while a session cooldown is running.
        modeRow = settingRow { t -> t.blockAll = !t.blockAll }
        budgetRow = settingRow { t -> t.budgetMs = nextOf(BUDGETS.filter { it < t.windowMs }, t.budgetMs) }
        windowRow = settingRow { t -> t.windowMs = nextOf(WINDOWS.filter { it > t.budgetMs }, t.windowMs) }
        val settings = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(CARD, 20)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(modeRow); addView(budgetRow); addView(windowRow)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(BG)
            fitsSystemWindows = true // targetSdk 35 is edge-to-edge
            setPadding(dp(24), dp(24), dp(24), dp(16))
            addView(text(14f, MUTED, true).apply { text = "REELSGUARD"; letterSpacing = 0.25f })
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(ringBox, LinearLayout.LayoutParams(dp(270), dp(270)))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(settings, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
            addView(peek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(guard, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true; setBackgroundColor(BG); addView(root)
        })
    }

    private fun settingRow(change: (CooldownTimer) -> Unit) = TextView(this).apply {
        textSize = 15f; setTextColor(Color.WHITE)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setOnClickListener {
            val t = Store.get(this@MainActivity)
            if (t.inCooldown(System.currentTimeMillis())) return@setOnClickListener
            change(t)
            Store.save(this@MainActivity, t)
            render()
        }
    }

    /** Next value in [options] after [cur], wrapping around; the first option if [cur] isn't in it. */
    private fun nextOf(options: List<Long>, cur: Long): Long =
        if (options.isEmpty()) cur else options[(options.indexOf(cur) + 1) % options.size]

    private fun mins(ms: Long) = if (ms % 3_600_000L == 0L) "${ms / 3_600_000L} h" else "${ms / MIN} min"

    override fun onResume() { super.onResume(); refresh.run() }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun render() {
        val t = Store.get(this)
        val now = System.currentTimeMillis()
        today.text = fmtLong(t.todayMs(now))
        val hit = Store.lastHit
        seen.text = if (hit == null) "Nothing detected yet"
        else "Last detected: $hit · ${(now - Store.lastHitAt) / 1000}s ago"
        val resetIn = t.windowLeftMs(now)
        resets.text = if (resetIn > 0 && !t.isBlocked(now)) "Resets in ${fmt(resetIn)}" else ""
        val locked = t.inCooldown(now)
        modeRow.text = "Mode:  " + if (t.blockAll) "Block completely" else "Sessions"
        budgetRow.text = "Scrolling per session:  ${mins(t.budgetMs)}"
        windowRow.text = "Session length:  ${mins(t.windowMs)}"
        budgetRow.visibility = if (t.blockAll) View.GONE else View.VISIBLE
        windowRow.visibility = budgetRow.visibility
        for (r in listOf(modeRow, budgetRow, windowRow)) r.alpha = if (locked) 0.4f else 1f
        big.textSize = 48f
        when {
            t.blockAll -> {
                big.textSize = 34f
                big.text = "BLOCKED"; label.text = "REELS OFF"
                ring.set(1f, RED)
                peek.visibility = View.GONE
            }
            t.passActive(now) -> {
                big.text = fmt(t.passLeftMs(now)); label.text = "PASS ACTIVE"
                ring.set(t.passLeftMs(now).toFloat() / t.passMs, GREY)
                peek.visibility = View.GONE
            }
            t.isBlocked(now) -> {
                big.text = fmt(t.cooldownLeftMs(now)); label.text = "COOLDOWN"
                ring.set(t.cooldownLeftMs(now).toFloat() / t.cooldownSpanMs(), RED)
                val left = t.passesLeft(now)
                peek.visibility = View.VISIBLE
                peek.text = "I need to see something  ·  $left left"
                peek.background = pill(if (left > 0) RED else 0xFF2E2E2E.toInt(), 28)
                peek.isEnabled = left > 0
                peek.alpha = if (left > 0) 1f else 0.6f
            }
            else -> {
                big.text = fmt(t.budgetLeftMs(now)); label.text = "TIME LEFT"
                ring.set(t.budgetLeftMs(now).toFloat() / t.budgetMs, WHITE)
                peek.visibility = View.GONE
            }
        }
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        guard.text = if (on) "●  Guard is on" else "●  Guard is off · tap to enable"
        guard.setTextColor(if (on) GREY else RED)
    }

    private fun fmtLong(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "%dh %02dm".format(m / 60, m % 60) else "%dm %02ds".format(m, ms / 1000 % 60)
    }

    private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
}

