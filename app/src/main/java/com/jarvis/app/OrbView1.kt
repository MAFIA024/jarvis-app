package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Rainbow dots wala ghoomta gola. mode: 0 shaant, 1 sun raha, 2 soch raha, 3 bol raha
class OrbView(context: Context) : View(context) {

    private val count = 5000
    private val buckets = 20

    private val px = FloatArray(count)
    private val py = FloatArray(count)
    private val pz = FloatArray(count)
    private val ph = FloatArray(count)
    private val bufs = Array(buckets * 2) { FloatArray(count * 2) }
    private val cnt = IntArray(buckets * 2)
    private val paints = Array(buckets * 2) { Paint(Paint.ANTI_ALIAS_FLAG) }

    private var mode = 0
    private var target = 0f
    private var energy = 0f
    private var yaw = 0f
    private var tick = 0f
    private var animator: ValueAnimator? = null

    init {
        val rnd = Random(7)
        for (i in 0 until count) {
            val u = rnd.nextFloat() * 2f - 1f
            val a = rnd.nextFloat() * 6.2831855f
            val rr = sqrt(1f - u * u)
            px[i] = rr * cos(a)
            py[i] = u
            pz[i] = rr * sin(a)
            ph[i] = rnd.nextFloat() * 6.2831855f
        }
        val dot = context.resources.displayMetrics.density * 0.95f
        for (b in 0 until buckets) {
            val t = (b + 0.5f) / buckets
            val hue = 6f + 232f * t
            val sat = 0.30f + 0.60f * abs(t - 0.5f) * 2f
            for (side in 0..1) {
                val p = paints[side * buckets + b]
                val alpha = if (side == 0) 255 else 120
                p.color = Color.HSVToColor(alpha, floatArrayOf(hue, sat, 1f))
                p.strokeWidth = dot
                p.strokeCap = Paint.Cap.ROUND
            }
        }
    }

    fun setMode(m: Int) {
        mode = m
    }

    fun setLevel(v: Float) {
        target = v.coerceIn(0f, 1f)
    }

    private fun step() {
        tick += 0.016f
        yaw += 0.006f * (1f + 4f * energy)
        val t = when (mode) {
            1 -> maxOf(0.10f, target)
            2 -> 0.45f + 0.10f * sin(tick * 3f)
            3 -> 0.50f + 0.30f * sin(tick * 8f) * sin(tick * 2.3f)
            else -> 0.05f
        }
        energy += (t - energy) * 0.1f
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1000
        a.repeatCount = ValueAnimator.INFINITE
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { step() }
        a.start()
        animator = a
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val a = animator
        if (a != null && a.isStarted) {
            if (visibility == VISIBLE) a.resume() else a.pause()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f * 0.9f
        val e = energy.coerceIn(0f, 1f)
        val cosY = cos(yaw)
        val sinY = sin(yaw)
        val ct = cos(0.35f)
        val st = sin(0.35f)
        val amp = 0.02f + 0.10f * e

        for (k in 0 until buckets * 2) cnt[k] = 0

        for (i in 0 until count) {
            val x0 = px[i]
            val y0 = py[i]
            val z0 = pz[i]
            val x1 = x0 * cosY + z0 * sinY
            val z1 = -x0 * sinY + z0 * cosY
            val y2 = y0 * ct - z1 * st
            val z2 = y0 * st + z1 * ct
            val k = 1f + amp * sin(tick * 5f + ph[i])
            val sx = cx + x1 * r * k
            val sy = cy + y2 * r * k
            var b = ((x1 + 1f) * 0.5f * buckets).toInt()
            if (b < 0) b = 0 else if (b >= buckets) b = buckets - 1
            if (z2 < 0f) b += buckets
            val c = cnt[b]
            val arr = bufs[b]
            arr[c * 2] = sx
            arr[c * 2 + 1] = sy
            cnt[b] = c + 1
        }

        for (k in buckets * 2 - 1 downTo 0) {
            canvas.drawPoints(bufs[k], 0, cnt[k] * 2, paints[k])
        }
    }
}

// Mic ka naya line-icon (neele/laal button ke andar)
class MicIcon(context: Context) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.strokeWidth = w * 0.075f
        rect.set(w * 0.40f, h * 0.18f, w * 0.60f, h * 0.54f)
        c.drawRoundRect(rect, w * 0.10f, w * 0.10f, p)
        rect.set(w * 0.30f, h * 0.30f, w * 0.70f, h * 0.66f)
        c.drawArc(rect, 0f, 180f, false, p)
        c.drawLine(w * 0.50f, h * 0.66f, w * 0.50f, h * 0.76f, p)
        c.drawLine(w * 0.40f, h * 0.76f, w * 0.60f, h * 0.76f, p)
    }
}
