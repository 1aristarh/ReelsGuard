package com.petar.reelsguard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared colours and small view builders. The whole app is built in code, no XML layouts. */
object Ui {
    const val BG = 0xFF000000.toInt()
    const val CARD = 0xFF161616.toInt()
    const val CARD2 = 0xFF1F1F1F.toInt()
    const val MUTED = 0xFF8C8C8C.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RED = 0xFFE5252A.toInt()
    const val GREY = 0xFFB0B0B0.toInt()
    const val GREEN = 0xFF3ECF6E.toInt()
    const val AMBER = 0xFFFFB02E.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
    fun dpf(c: Context, v: Float) = v * c.resources.displayMetrics.density

    fun fmtMin(m: Int) = if (m >= 60 && m % 60 == 0) "${m / 60} h" else "$m min"

    fun dateStr(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

    fun fmtLeft(ms: Long): String {
        val m = ms / 60_000
        return "%dh %02dm".format(m / 60, m % 60)
    }

    fun guardOn(c: Context): Boolean =
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(c.packageName) == true

    fun text(c: Context, size: Float, color: Int, bold: Boolean = false, center: Boolean = true) = TextView(c).apply {
        textSize = size; setTextColor(color)
        gravity = if (center) Gravity.CENTER else Gravity.START
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun pill(c: Context, color: Int, radiusDp: Int, stroke: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(c, radiusDp).toFloat()
        if (stroke != 0) setStroke(dp(c, 2), stroke)
    }

    /** Small caps section label. */
    fun label(c: Context, s: String) = text(c, 11f, MUTED, true, false).apply {
        text = s; letterSpacing = 0.15f
        setPadding(dp(c, 2), dp(c, 16), 0, dp(c, 8))
    }

    fun button(c: Context, label: String, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) =
        text(c, 16f, if (enabled) WHITE else 0xFF777777.toInt(), true).apply {
            text = label
            setPadding(dp(c, 22), dp(c, 16), dp(c, 22), dp(c, 16))
            background = pill(c, if (!enabled) 0xFF2E2E2E.toInt() else if (primary) RED else CARD, 28)
            if (!primary && enabled) setTextColor(GREY)
            isEnabled = enabled
            if (enabled) setOnClickListener { onClick() }
        }

    /** A card row showing a setting; tap to open the throttle. */
    fun row(c: Context, label: String, value: String, capped: Boolean, onClick: () -> Unit): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = pill(c, CARD, 16)
            setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
            addView(text(c, 14f, MUTED, false, false).apply { text = label },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(text(c, 15f, WHITE, true).apply {
                text = (if (capped) "🔒  " else "") + value + "  ›"
            })
            setOnClickListener { onClick() }
        }

    fun infoCard(c: Context, html: CharSequence) = text(c, 14f, GREY, false, false).apply {
        text = html; background = pill(c, CARD, 18)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
    }

    fun banner(c: Context, icon: String, title: String, body: String, bg: Int, border: Int): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = pill(c, bg, 16, border)
            setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
            addView(text(c, 22f, WHITE).apply { text = icon })
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(c, 12), 0, 0, 0)
                addView(text(c, 14f, WHITE, true, false).apply { text = title })
                addView(text(c, 13f, GREY, false, false).apply { text = body })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

    /** Rows of "label ... value" inside one card. */
    fun summary(c: Context, rows: List<Pair<String, String>>): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = pill(c, CARD, 20)
        setPadding(dp(c, 16), dp(c, 6), dp(c, 16), dp(c, 6))
        rows.forEach { (a, b) ->
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(c, 12), 0, dp(c, 12))
                addView(text(c, 14f, MUTED, false, false).apply { text = a }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(text(c, 14f, WHITE, true, false).apply { text = b; gravity = Gravity.END })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    fun gap(c: Context, dpv: Int) = View(c).apply { layoutParams = LinearLayout.LayoutParams(1, dp(c, dpv)) }
}
