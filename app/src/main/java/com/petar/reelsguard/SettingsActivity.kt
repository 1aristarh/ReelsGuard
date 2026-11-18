package com.petar.reelsguard

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * Settings behind the gear on the main screen. While the setup lock runs the levers can't go up (only stricter).
 * After it ends, loosening is a request that waits 24 h and then needs a press-and-hold to apply.
 */
class SettingsActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var footer: LinearLayout
    private var draft: Config? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shownPhase: Phase? = null
    private val tick = object : Runnable {
        override fun run() {
            // refresh when a pending change becomes ready, or the countdown text moves
            val lock = Store.lock(this@SettingsActivity)
            val ph = lock.phase(System.currentTimeMillis())
            if (ph != shownPhase || ph == Phase.PENDING) render()
            handler.postDelayed(this, 30_000)
        }
    }

    private fun d(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(22), d(4), d(22), d(8)) }
        footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(d(22), d(10), d(22), d(16)) }
        val back = Ui.text(this, 18f, Ui.WHITE).apply {
            text = "←"; background = Ui.pill(this@SettingsActivity, Ui.CARD, 18)
            setOnClickListener { finish() }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(d(22), d(14), d(22), d(10))
            addView(back, LinearLayout.LayoutParams(d(36), d(36)))
            addView(Ui.text(this@SettingsActivity, 22f, Ui.WHITE, true, false).apply { text = "Settings"; setPadding(d(10), 0, 0, 0) })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            fitsSystemWindows = true
            addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(ScrollView(this@SettingsActivity).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun add(v: View, top: Int = 0) = content.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = d(top) })

    private fun showVal(c: Config, k: String) = when (k) {
        "mode" -> if (c.blockAll) "Block completely" else "Sessions"
        "budget" -> Ui.fmtMin(c.budgetMin)
        "window" -> Ui.fmtMin(c.windowMin)
        "passes" -> Field.PASSES.fmt(c.passes)
        else -> Ui.fmtMin(c.passMin)
    }

    private fun diffRows(from: Config, to: Config): List<Pair<String, String>> {
        val r = mutableListOf<Pair<String, String>>()
        fun diff(k: String, label: String, a: Any, b: Any) { if (a != b) r += label to "${showVal(from, k)}  →  ${showVal(to, k)}" }
        diff("mode", "Mode", from.blockAll, to.blockAll)
        if (!to.blockAll) {
            diff("budget", "Scrolling per session", from.budgetMin, to.budgetMin)
            diff("window", "Session length", from.windowMin, to.windowMin)
            diff("passes", "\"I need to see something\"", from.passes, to.passes)
            if (to.passes > 0) diff("passLen", "Length of each pass", from.passMin, to.passMin)
        }
        return r
    }

    private fun render() {
        val t = Store.get(this)
        val lock = Store.lock(this)
        val now = System.currentTimeMillis()
        val phase = lock.phase(now)
        shownPhase = phase
        val cur = Store.config(t)
        content.removeAllViews(); footer.removeAllViews()
        val untilTxt = Ui.dateStr(lock.lockEnd())

        when (phase) {
            Phase.SETUP -> { finish(); return }
            Phase.PENDING -> {
                add(Ui.banner(this, "⏳", "Change pending · applies in ${Ui.fmtLeft(lock.waitLeftMs(now))}",
                    "Your current limits stay in force until then.", 0xFF2A2410.toInt(), 0xFF5A4A1A.toInt()))
                add(Ui.label(this, "REQUESTED CHANGE"))
                add(Ui.summary(this, diffRows(cur, lock.pending!!).ifEmpty { listOf("No changes" to "") }))
                add(Ui.button(this, "Cancel request", primary = false) { lock.cancelPending(); Store.saveLock(this, lock); render() }, 12)
            }
            Phase.READY -> {
                val p = lock.pending!!
                add(Ui.banner(this, "🔓", "Ready to apply", "The wait is over. Review the change, then hold to apply.", 0xFF0F2A17.toInt(), 0xFF1D5A31.toInt()))
                add(Ui.label(this, "BEFORE → AFTER"))
                add(Ui.summary(this, diffRows(cur, p).ifEmpty { listOf("No changes" to "") }))
                add(Ui.text(this, 13f, Ui.GREY, false, false).apply { text = "Applying starts a new ${lock.lockDays}-day lock."; setPadding(d(2), d(14), 0, 0) })
                footer.addView(Ui.button(this, "Cancel", primary = false) { lock.cancelPending(); Store.saveLock(this, lock); render() })
                footer.addView(HoldButton(this, "Hold to apply") {
                    lock.applyPending(System.currentTimeMillis())?.let { Store.applyConfig(t, it) }
                    Store.save(this, t); Store.saveLock(this, lock)
                    Toast.makeText(this, "Applied. Locked again until ${Ui.dateStr(lock.lockEnd())}", Toast.LENGTH_LONG).show()
                    draft = null; render()
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = d(10) })
            }
            Phase.LOCKED, Phase.UNLOCKED -> editor(t, lock, cur, phase == Phase.LOCKED, untilTxt)
        }
    }

    private fun editor(t: CooldownTimer, lock: SetupLock, cur: Config, locked: Boolean, untilTxt: String) {
        val dr = draft ?: cur
        draft = dr
        if (locked) add(Ui.banner(this, "🔒", "Locked until $untilTxt", "The levers can't go up. You can still pull them down.", 0xFF2A1011.toInt(), 0xFF5A1A1C.toInt()))
        else add(Ui.banner(this, "🔓", "Lock ended $untilTxt", "Pulling down applies now. Pushing up takes a 24 h wait.", 0xFF0F2A17.toInt(), 0xFF1D5A31.toInt()))

        add(Ui.label(this, "MODE"))
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (block in listOf(false, true)) {
            val disabled = locked && cur.blockAll && !block // can't go back to Sessions while locked
            modes.addView(Ui.text(this, 14f, if (dr.blockAll == block) 0xFF000000.toInt() else Ui.WHITE, dr.blockAll == block).apply {
                text = if (block) "Block completely" else "Sessions"
                background = Ui.pill(this@SettingsActivity, if (dr.blockAll == block) Ui.WHITE else Ui.CARD, 22)
                setPadding(d(16), d(10), d(16), d(10))
                alpha = if (disabled) 0.35f else 1f
                setOnClickListener { if (!disabled) { draft = dr.copy(blockAll = block); render() } }
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = d(8) })
        }
        add(modes)

        fun open(f: Field) {
            val curVal = f.get(cur)
            ThrottleDialog.show(this, f, f.get(dr), if (locked) curVal else null, untilTxt,
                "Up needs a 24 h wait. Down applies now.") { v -> draft = dr.with(f, v); render() }
        }
        fun row(f: Field) = Ui.row(this, f.label, f.fmt(f.get(dr)), locked) { open(f) }
        if (!dr.blockAll) {
            add(Ui.label(this, "LIMITS"))
            add(row(Field.BUDGET)); add(row(Field.WINDOW), 8); add(row(Field.PASSES), 8)
            if (dr.passes > 0) add(row(Field.PASS_LEN), 8)
        }

        val dirty = dr != cur && !(dr.blockAll && cur.blockAll)
        val needsWait = dirty && Config.isLoose(cur, dr)
        if (!dirty) {
            footer.addView(Ui.button(this, "No changes", enabled = false) {}, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            return
        }
        val label = if (needsWait) "Hold to request · 24 h wait" else if (locked) "Hold to apply (stricter)" else "Hold to apply"
        footer.addView(HoldButton(this, label) {
            val now = System.currentTimeMillis()
            if (needsWait) {
                lock.requestChange(dr, now); Store.saveLock(this, lock)
                Toast.makeText(this, "Requested. It can be applied in 24 h.", Toast.LENGTH_LONG).show()
            } else {
                Store.applyConfig(t, dr); Store.save(this, t)
                Toast.makeText(this, if (locked) "Made stricter. Lock end date unchanged." else "Applied.", Toast.LENGTH_SHORT).show()
            }
            draft = null; render()
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }
}
