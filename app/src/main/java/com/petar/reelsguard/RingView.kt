package com.petar.reelsguard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Circular progress ring: a dim track plus a coloured arc that shrinks as time runs out. */
class RingView(c: Context) : View(c) {
    var progress = 1f
    var color = 0xFFFFFFFF.toInt()
    private val stroke = 14 * resources.displayMetrics.density
    private val rect = RectF()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = 0xFF242424.toInt()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
    }

    fun set(progress: Float, color: Int) {
        if (progress != this.progress || color != this.color) {
            this.progress = progress; this.color = color; invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(rect, track)
        arc.color = color
        canvas.drawArc(rect, -90f, 360f * progress.coerceIn(0f, 1f), false, arc)
    }
}

