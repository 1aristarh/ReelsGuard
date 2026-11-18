package com.petar.reelsguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * First-run setup: mode, limits, passes, permission, then review and a press-and-hold approval that locks the
 * settings. The guard does nothing until this is approved.
 */
class SetupActivity : Activity() {
    private var step = 0
    private var cfg = Config.DEFAULT
    private var lockDays = 7
    private lateinit var dots: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var footer: LinearLayout

    private fun steps() = if (cfg.blockAll) listOf("mode", "perm", "review") else listOf("mode", "limits", "passes", "perm", "review")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Store.lock(this).approved) { goMain(); return }

        dots = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(this@SetupActivity, 8), 0, Ui.dp(this@SetupActivity, 4)) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@SetupActivity, 22), Ui.dp(this@SetupActivity, 14), Ui.dp(this@SetupActivity, 22), Ui.dp(this@SetupActivity, 8))
        }
        footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(this@SetupActivity, 22), Ui.dp(this@SetupActivity, 10), Ui.dp(this@SetupActivity, 22), Ui.dp(this@SetupActivity, 16))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true // targetSdk 35 is edge-to-edge
            addView(Ui.text(this@SetupActivity, 14f, Ui.MUTED, true).apply {
                text = "REELSGUARD"; letterSpacing = 0.25f
                setPadding(0, Ui.dp(this@SetupActivity, 14), 0, 0)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(dots, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(ScrollView(this@SetupActivity).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(root)
        render()
    }

    override fun onResume() { super.onResume(); if (::content.isInitialized) render() } // permission may have changed

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun render() {
        val list = steps()
        if (step >= list.size) step = list.size - 1
        val cur = list[step]
        dots.removeAllViews()
        list.forEachIndexed { i, _ ->
            dots.addView(View(this).apply {
                background = Ui.pill(this@SetupActivity, if (i < step) Ui.RED else if (i == step) Ui.WHITE else 0xFF2A2A2A.toInt(), 2)
            }, LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 4)).apply { marginStart = Ui.dp(this@SetupActivity, 3); marginEnd = Ui.dp(this@SetupActivity, 3) })
        }
        content.removeAllViews(); footer.removeAllViews()
        var nextOk = true

        fun title(t: String, sub: String) {
            content.addView(Ui.text(this, 26f, Ui.WHITE, true, false).apply { text = t })
            content.addView(Ui.text(this, 14f, Ui.MUTED, false, false).apply { text = sub; setPadding(0, Ui.dp(this@SetupActivity, 6), 0, Ui.dp(this@SetupActivity, 18)) })
        }
        fun add(v: View, top: Int = 0) = content.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = Ui.dp(this@SetupActivity, top) })
        fun open(f: Field) = ThrottleDialog.show(this, f, f.get(cfg, lockDays), null, null, "Push up for more, pull down for less.") { v ->
            if (f == Field.LOCK_DAYS) lockDays = v else cfg = cfg.with(f, v)
            render()
        }
        fun row(f: Field) = Ui.row(this, f.label, f.fmt(f.get(cfg, lockDays)), false) { open(f) }

        when (cur) {
            "mode" -> {
                title("How strict?", "Pick how ReelsGuard treats Reels, Shorts and TikTok.")
                add(optCard("Sessions", "A scrolling budget per session, then blocked until the session ends.", !cfg.blockAll) { cfg = cfg.copy(blockAll = false); render() })
                add(optCard("Block completely", "Reels and Shorts close as soon as they open. No budget, no passes.", cfg.blockAll) { cfg = cfg.copy(blockAll = true); render() }, 10)
            }
            "limits" -> {
                title("Set your limits", "Tap a row to open the throttle.")
                add(row(Field.BUDGET)); add(row(Field.WINDOW), 8)
                add(Ui.infoCard(this, "${Ui.fmtMin(cfg.budgetMin)} of scrolling every ${Ui.fmtMin(cfg.windowMin)}. When it is used up you are blocked until the session ends."), 16)
            }
            "passes" -> {
                title("Emergency passes", "\"I need to see something\": a short pass during a block, e.g. for a reel a friend sent.")
                add(row(Field.PASSES))
                if (cfg.passes > 0) add(row(Field.PASS_LEN), 8)
                add(Ui.infoCard(this, if (cfg.passes == 0) "No passes: a block is a block."
                else "${cfg.passes} pass${if (cfg.passes > 1) "es" else ""} a day, ${Ui.fmtMin(cfg.passMin)} each. Pass time still counts in your daily total."), 16)
            }
            "perm" -> {
                val on = Ui.guardOn(this)
                title("Allow the guard", "ReelsGuard needs one permission to see when Reels or Shorts are open and to swipe out of them.")
                add(Ui.banner(this, if (on) "✅" else "♿", "Accessibility service",
                    if (on) "On. The guard can see Reels and Shorts." else "Reads the open screen and performs the swipe or Back. Tap to turn it on.",
                    Ui.CARD, if (on) 0xFF1D5A31.toInt() else Ui.CARD).apply {
                    setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                })
                add(Ui.infoCard(this, "Reads nothing outside Instagram, YouTube and TikTok. Nothing leaves your phone.\n\nIf the switch is greyed out: Settings > Apps > ReelsGuard > ⋮ > Allow restricted settings."), 14)
                nextOk = on
            }
            "review" -> {
                title("Review & lock", "Once you approve, these settings are locked and the guard starts.")
                add(Ui.summary(this, summaryRows()))
                add(row(Field.LOCK_DAYS), 10)
                add(Ui.text(this, 13f, Ui.GREY, false, false).apply {
                    text = "Until ${Ui.dateStr(System.currentTimeMillis() + lockDays * SetupLock.DAY_MS)} you can't raise any limit. Be sure."
                    setPadding(Ui.dp(this@SetupActivity, 2), Ui.dp(this@SetupActivity, 14), 0, 0)
                })
            }
        }

        if (cur == "review") {
            footer.addView(Ui.button(this, "Back", primary = false) { step--; render() })
            footer.addView(HoldButton(this, "Hold to approve & lock") { approve() },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(this@SetupActivity, 10) })
        } else {
            if (step > 0) footer.addView(Ui.button(this, "Back", primary = false) { step--; render() })
            footer.addView(Ui.button(this, "Continue", enabled = nextOk) { step++; render() },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (step > 0) marginStart = Ui.dp(this@SetupActivity, 10) })
        }
    }

    private fun summaryRows(): List<Pair<String, String>> {
        val r = mutableListOf("Mode" to if (cfg.blockAll) "Block completely" else "Sessions")
        if (!cfg.blockAll) {
            r += "Scrolling per session" to Ui.fmtMin(cfg.budgetMin)
            r += "Session length" to Ui.fmtMin(cfg.windowMin)
            r += "\"I need to see something\"" to if (cfg.passes == 0) "Off" else "${cfg.passes} per day × ${Ui.fmtMin(cfg.passMin)}"
        }
        return r
    }

    private fun optCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit): TextView =
        Ui.text(this, 14f, Ui.MUTED, false, false).apply {
            text = android.text.SpannableStringBuilder().apply {
                append(title, android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0)
                setSpan(android.text.style.ForegroundColorSpan(Ui.WHITE), 0, length, 0)
                setSpan(android.text.style.RelativeSizeSpan(1.15f), 0, length, 0)
                append("\n$desc")
            }
            background = Ui.pill(this@SetupActivity, Ui.CARD, 18, if (selected) Ui.WHITE else Ui.CARD)
            setPadding(Ui.dp(this@SetupActivity, 16), Ui.dp(this@SetupActivity, 14), Ui.dp(this@SetupActivity, 16), Ui.dp(this@SetupActivity, 14))
            setOnClickListener { onClick() }
        }

    private fun approve() {
        val t = Store.get(this)
        Store.applyConfig(t, cfg)
        val lock = Store.lock(this)
        lock.approve(System.currentTimeMillis(), lockDays)
        Store.save(this, t)
        Store.saveLock(this, lock)
        goMain()
    }
}
