package com.petar.reelsguard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.BlurMaskFilter
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A vertical throttle lever: drag the metal knob between detents. Up is "more".
 * With [limit] set, the knob can't go above that stop; a hatched barrier marks the blocked zone.
 */
class ThrottleView(c: Context) : View(c) {
    private val d = resources.displayMetrics.density

    var stops: IntArray = intArrayOf(0, 1)
    var fmt: (Int) -> String = { it.toString() }
    var idx = 0
    /** Highest allowed stop index, or -1 for no cap. */
    var limit = -1
    var onChange: (Int) -> Unit = {}
    var onBump: () -> Unit = {}

    private val pad = 8 * d
    private val hh = 32 * d
    private val hw = 56 * d
    private val slotW = 30 * d
    private var shake = 0f
    private var flash = 0f
    private var lastBump = 0L
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val slot = RectF()
    private val clip = Path()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // blur / shadow layers

    private fun cx() = width * 0.48f
    private fun trackH() = height - 2 * pad
    private fun centerY(i: Int) = pad + hh / 2 + (1f - i / (stops.size - 1).coerceAtLeast(1).toFloat()) * (trackH() - hh)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(getDefaultSize(0, w), (270 * d + 2 * pad).toInt())
    }

    fun bump() {
        flash = 1f; shake = 1f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300; interpolator = LinearInterpolator()
            addUpdateListener { shake = it.animatedValue as Float; flash = shake; invalidate() }
        }.start()
        performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); move(e.y) }
            MotionEvent.ACTION_MOVE -> move(e.y)
        }
        return true
    }

    private fun move(y: Float) {
        val pct = 1f - (y - pad - hh / 2) / (trackH() - hh)
        var i = Math.round(pct.coerceIn(0f, 1f) * (stops.size - 1))
        if (limit >= 0 && i > limit) {
            i = limit
            val now = System.currentTimeMillis()
            if (now - lastBump > 450) { lastBump = now; bump(); onBump() }
        }
        if (i != idx) {
            idx = i
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange(i)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = cx()
        // slot with its rings
        slot.set(cx - slotW / 2, pad, cx + slotW / 2, height - pad)
        p.reset(); p.isAntiAlias = true
        for ((grow, col) in listOf(7f to 0xFF26272B.toInt(), 6f to 0xFF111214.toInt(), 2f to 0xFF2B2C30.toInt())) {
            p.color = col
            rect.set(slot); rect.inset(-grow * d, -grow * d)
            canvas.drawRoundRect(rect, slotW / 2 + grow * d, slotW / 2 + grow * d, p)
        }
        p.shader = LinearGradient(slot.left, 0f, slot.right, 0f, intArrayOf(0xFF020202.toInt(), 0xFF0B0B0C.toInt(), 0xFF020202.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(slot, slotW / 2, slotW / 2, p)
        p.shader = null

        canvas.save()
        clip.reset(); clip.addRoundRect(slot, slotW / 2, slotW / 2, Path.Direction.CW)
        canvas.clipPath(clip)
        // blocked zone above the cap
        if (limit in 0 until stops.size - 1) {
            val bottom = centerY(limit) - hh / 2 + 6 * d
            p.color = 0xFF240A0B.toInt()
            canvas.drawRect(slot.left, slot.top, slot.right, bottom, p)
            p.color = 0xFF5A1A1C.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d
            canvas.save(); canvas.clipRect(slot.left, slot.top, slot.right, bottom)
            var x = slot.left - bottom
            while (x < slot.right) { canvas.drawLine(x, bottom, x + bottom, slot.top, p); x += 14 * d }
            canvas.restore()
            p.style = Paint.Style.FILL
            p.color = if (flash > 0f) 0xFFFF6B6B.toInt() else Ui.RED
            canvas.drawRect(slot.left, bottom - 2 * d, slot.right, bottom, p)
        }
        // thrust bar
        val top = centerY(idx)
        rect.set(cx - 5 * d, top, cx + 5 * d, slot.bottom - 3 * d)
        p.maskFilter = BlurMaskFilter(6 * d, BlurMaskFilter.Blur.NORMAL); p.color = 0x99FF601F.toInt()
        canvas.drawRoundRect(rect, 5 * d, 5 * d, p)
        p.maskFilter = null
        p.shader = LinearGradient(0f, rect.bottom, 0f, rect.top, intArrayOf(0xFFFF2A1A.toInt(), 0xFFFF7A1F.toInt(), 0xFFFFC52E.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 5 * d, 5 * d, p)
        p.shader = null
        canvas.restore()

        // scale: labels on the left, plain ticks on the right
        for (i in stops.indices) {
            val y = centerY(i)
            val hot = i == idx
            p.style = Paint.Style.FILL
            p.color = if (hot) Ui.AMBER else 0xFF3A3B40.toInt()
            canvas.drawRect(cx - 40 * d, y - 0.75f * d, cx - 30 * d, y + 0.75f * d, p)
            p.color = if (hot) Ui.AMBER else 0xFF2D2E32.toInt()
            canvas.drawRect(cx + 30 * d, y - 0.75f * d, cx + 40 * d, y + 0.75f * d, p)
            p.color = if (hot) Ui.WHITE else 0xFF6C6C72.toInt()
            p.textSize = 11 * d; p.textAlign = Paint.Align.RIGHT
            p.isFakeBoldText = hot
            canvas.drawText(fmt(stops[i]), cx - 46 * d, y + 4 * d, p)
            p.isFakeBoldText = false
        }
        p.color = 0xFF55565C.toInt(); p.textSize = 10 * d; p.textAlign = Paint.Align.RIGHT; p.letterSpacing = 0.12f
        canvas.drawText("MORE", width - 4 * d, centerY(stops.size - 1) + 4 * d, p)
        canvas.drawText("LESS", width - 4 * d, centerY(0) + 4 * d, p)
        p.letterSpacing = 0f

        drawKnob(canvas, cx + (Math.sin(shake * Math.PI * 3).toFloat() * 4 * d * shake), centerY(idx))
    }

    private fun drawKnob(canvas: Canvas, cx: Float, cy: Float) {
        rect.set(cx - hw / 2, cy - hh / 2, cx + hw / 2, cy + hh / 2)
        val r = 8 * d
        // drop shadow
        p.reset(); p.isAntiAlias = true
        p.color = 0x00000000; p.setShadowLayer(10 * d, 0f, 6 * d, 0xA6000000.toInt())
        canvas.drawRoundRect(rect, r, r, p)
        p.clearShadowLayer()
        // brushed metal body
        p.shader = LinearGradient(rect.left, 0f, rect.right, 0f,
            intArrayOf(0xFF26282D.toInt(), 0xFF4D5059.toInt(), 0xFF7B7F8A.toInt(), 0xFF3F424A.toInt(), 0xFF5B5F69.toInt(), 0xFF1F2125.toInt()),
            floatArrayOf(0f, .18f, .36f, .55f, .78f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, p)
        p.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            intArrayOf(0x38FFFFFF, 0x00FFFFFF, 0x59000000), floatArrayOf(0f, .18f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, r, r, p)
        p.shader = null
        // knurled grip
        val g = RectF(rect.left + 6 * d, rect.top + 9 * d, rect.right - 6 * d, rect.bottom - 6 * d)
        p.color = 0xFF0D0E10.toInt()
        canvas.drawRoundRect(g, 4 * d, 4 * d, p)
        p.color = 0xFF5A5D66.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d
        var y = g.top + 1.5f * d
        while (y < g.bottom - 1f * d) { canvas.drawLine(g.left + 2 * d, y, g.right - 2 * d, y, p); y += 3.2f * d }
        p.style = Paint.Style.FILL
        // red cap
        canvas.save()
        clip.reset(); clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(clip)
        p.shader = LinearGradient(0f, rect.top, 0f, rect.top + 6 * d, 0xFFFF5A4A.toInt(), 0xFFC4191F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + 6 * d, p)
        p.shader = null
        canvas.restore()
        // edge
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0xFF0A0A0B.toInt()
        canvas.drawRoundRect(rect, r, r, p)
        p.style = Paint.Style.FILL
    }
}
