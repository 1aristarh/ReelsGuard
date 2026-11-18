package com.petar.reelsguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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

/** Main screen: just the timer. The settings live behind the gear in the top right corner. */
class MainActivity : Activity() {
    private lateinit var ring: RingView
    private lateinit var big: TextView
    private lateinit var label: TextView
    private lateinit var resets: TextView
    private lateinit var seen: TextView
    private lateinit var today: TextView
    private lateinit var peek: TextView
    private lateinit var guard: TextView
    private lateinit var lockInfo: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1000) }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        ring = RingView(this)
        big = Ui.text(this, 48f, Color.WHITE, true)
        label = Ui.text(this, 12f, Ui.MUTED, true).apply { letterSpacing = 0.15f }
        resets = Ui.text(this, 13f, Ui.MUTED)
        seen = Ui.text(this, 12f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) }
        lockInfo = Ui.text(this, 12f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) }
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(big); addView(label)
            addView(resets, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }
        val ringBox = FrameLayout(this).apply {
            addView(ring, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(center, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        val todayLabel = Ui.text(this, 11f, Ui.MUTED, true).apply { text = "TODAY ON REELS"; letterSpacing = 0.15f }
        today = Ui.text(this, 26f, Color.WHITE, true)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = Ui.pill(this@MainActivity, Ui.CARD, 20)
            setPadding(dp(24), dp(18), dp(24), dp(18))
            addView(todayLabel); addView(today); addView(seen)
        }

        peek = Ui.text(this, 16f, Color.WHITE, true).apply {
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

        guard = Ui.text(this, 14f, Ui.MUTED).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val gear = Ui.text(this, 18f, Ui.GREY).apply {
            text = "⚙"; background = Ui.pill(this@MainActivity, Ui.CARD, 18)
            contentDescription = "Settings"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        val header = FrameLayout(this).apply {
            addView(Ui.text(this@MainActivity, 14f, Ui.MUTED, true).apply { text = "REELSGUARD"; letterSpacing = 0.25f },
                FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER_VERTICAL))
            addView(gear, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.END or Gravity.CENTER_VERTICAL))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true // targetSdk 35 is edge-to-edge
            setPadding(dp(24), dp(24), dp(24), dp(16))
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, dp(36)))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(ringBox, LinearLayout.LayoutParams(dp(270), dp(270)))
            addView(lockInfo)
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(peek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(guard, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Ui.BG); addView(root) })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.lock(this).approved) { // the guard stays off until the setup is approved
            startActivity(Intent(this, SetupActivity::class.java)); finish(); return
        }
        refresh.run()
    }

    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    private fun render() {
        val t = Store.get(this)
        val lock = Store.lock(this)
        val now = System.currentTimeMillis()
        today.text = fmtLong(t.todayMs(now))
        val hit = Store.lastHit
        seen.text = if (hit == null) "Nothing detected yet"
        else "Last detected: $hit · ${(now - Store.lastHitAt) / 1000}s ago"
        val resetIn = t.windowLeftMs(now)
        resets.text = if (resetIn > 0 && !t.isBlocked(now)) "Resets in ${fmt(resetIn)}" else ""
        lockInfo.text = when (lock.phase(now)) {
            Phase.LOCKED -> "🔒  Settings locked until ${Ui.dateStr(lock.lockEnd())}"
            Phase.PENDING -> "⏳  Change pending · ${Ui.fmtLeft(lock.waitLeftMs(now))} left"
            Phase.READY -> "Change ready to apply in Settings"
            else -> ""
        }
        big.textSize = 48f
        when {
            t.blockAll -> {
                big.textSize = 34f
                big.text = "BLOCKED"; label.text = "REELS OFF"
                ring.set(1f, Ui.RED)
                peek.visibility = View.GONE
            }
            t.passActive(now) -> {
                big.text = fmt(t.passLeftMs(now)); label.text = "PASS ACTIVE"
                ring.set(t.passLeftMs(now).toFloat() / t.passMs, Ui.GREY)
                peek.visibility = View.GONE
            }
            t.isBlocked(now) -> {
                big.text = fmt(t.cooldownLeftMs(now)); label.text = "COOLDOWN"
                ring.set(t.cooldownLeftMs(now).toFloat() / t.cooldownSpanMs(), Ui.RED)
                val left = t.passesLeft(now)
                peek.visibility = View.VISIBLE
                peek.text = "I need to see something  ·  $left left"
                peek.background = Ui.pill(this, if (left > 0) Ui.RED else 0xFF2E2E2E.toInt(), 28)
                peek.isEnabled = left > 0
                peek.alpha = if (left > 0) 1f else 0.6f
            }
            else -> {
                big.text = fmt(t.budgetLeftMs(now)); label.text = "TIME LEFT"
                ring.set(t.budgetLeftMs(now).toFloat() / t.budgetMs, Ui.WHITE)
                peek.visibility = View.GONE
            }
        }
        val on = Ui.guardOn(this)
        guard.text = if (on) "●  Guard is on" else "●  Guard is off · tap to enable"
        guard.setTextColor(if (on) Ui.GREY else Ui.RED)
    }

    private fun fmtLong(ms: Long): String {
        val m = ms / 60000
        return if (m >= 60) "%dh %02dm".format(m / 60, m % 60) else "%dm %02ds".format(m, ms / 1000 % 60)
    }

    private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
}
