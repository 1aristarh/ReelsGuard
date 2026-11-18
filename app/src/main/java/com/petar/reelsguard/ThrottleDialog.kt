package com.petar.reelsguard

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.LinearLayout

/** Bottom-sheet popup with the throttle lever, used for every number setting. */
object ThrottleDialog {
    /**
     * @param cap the value currently in force when the lever must not go above it (locked), else null
     * @param lockedUntil text shown while capped, e.g. "Mon 23 Nov"
     * @param hint shown when not capped
     */
    fun show(a: Activity, f: Field, current: Int, cap: Int?, lockedUntil: String?, hint: String, onSet: (Int) -> Unit) {
        val dlg = Dialog(a)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val stops = f.stops

        val title = Ui.text(a, 11f, Ui.MUTED, true).apply { text = f.label.uppercase(); letterSpacing = 0.2f }
        val readout = Ui.text(a, 44f, Ui.WHITE, true)
        val sub = Ui.text(a, 12.5f, Ui.MUTED).apply { minLines = 2; gravity = Gravity.CENTER }
        val lever = ThrottleView(a).apply {
            this.stops = stops; fmt = { f.fmt(it) }
            idx = f.nearest(current)
            limit = if (cap != null) f.floorIdx(cap) else -1
        }
        fun refresh(bump: Boolean = false) {
            readout.text = f.fmt(stops[lever.idx])
            sub.setTextColor(if (bump) 0xFFFF6B6B.toInt() else Ui.MUTED)
            sub.text = when {
                cap != null && bump -> "Locked until $lockedUntil. Can't go up."
                cap != null -> "Locked until $lockedUntil. Max ${f.fmt(cap)}."
                else -> hint
            }
        }
        lever.onChange = { refresh() }
        lever.onBump = { refresh(true) }
        refresh()

        val set = Ui.button(a, "Set") { onSet(stops[lever.idx]); dlg.dismiss() }
        val cancel = Ui.button(a, "Cancel", primary = false) { dlg.dismiss() }
        val buttons = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(cancel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            addView(set, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(a, 10) })
        }
        val grab = android.view.View(a).apply {
            background = Ui.pill(a, 0xFF3A3A3F.toInt(), 2)
        }
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val r = Ui.dpf(a, 28f)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF17181B.toInt(), 0xFF0C0C0E.toInt()))
                .apply { cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(Ui.dp(a, 22), Ui.dp(a, 12), Ui.dp(a, 22), Ui.dp(a, 22))
            addView(grab, LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 4)).apply { bottomMargin = Ui.dp(a, 12) })
            addView(title); addView(readout); addView(sub, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(lever, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = Ui.dp(a, 6); bottomMargin = Ui.dp(a, 10) })
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        dlg.setContentView(panel)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(MATCH_PARENT, WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.65f)
            attributes = attributes.apply { windowAnimations = android.R.style.Animation_InputMethod }
        }
        dlg.show()
    }
}
