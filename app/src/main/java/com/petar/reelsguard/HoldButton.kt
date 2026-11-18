package com.petar.reelsguard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

/** Press-and-hold button: fills while held and fires [onDone] after [HOLD_MS]. Letting go early resets it. */
class HoldButton(c: Context, var label: String, private val onDone: () -> Unit) : View(c) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private var progress = 0f
    private var anim: ValueAnimator? = null
    private var fired = false

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(getDefaultSize(0, w), (56 * d).toInt())
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); start() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun start() {
        if (fired) return
        anim?.cancel()
        anim = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = ((1f - progress) * HOLD_MS).toLong().coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
                if (progress >= 1f && !fired) {
                    fired = true
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone()
                }
            }
            start()
        }
    }

    private fun reset() {
        if (fired) return
        anim?.cancel()
        anim = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = 200
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        p.color = 0xFF3A0F10.toInt()
        canvas.drawRoundRect(rect, r, r, p)
        canvas.save()
        clip.reset(); clip.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(clip)
        p.color = Ui.RED
        canvas.drawRect(0f, 0f, width * progress, height.toFloat(), p)
        canvas.restore()
        p.color = Ui.WHITE; p.textSize = 16 * d; p.isFakeBoldText = true; p.textAlign = Paint.Align.CENTER
        canvas.drawText(label, width / 2f, height / 2f + 5.5f * d, p)
        p.isFakeBoldText = false
    }

    companion object { const val HOLD_MS = 1500L }
}
