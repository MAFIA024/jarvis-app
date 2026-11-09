package com.jarvis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

// Neela baadal wala gol orb. mode: 0 shaant, 1 sun raha, 2 soch raha, 3 bol raha
class OrbView(context: Context) : View(context) {

    private var mode = 0
    private var target = 0f
    private var energy = 0f
    private var drift = 0f
    private var tick = 0f
    private var animator: ValueAnimator? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // x, y, radiusX, radiusY, ampX, ampY, speedX, speedY, offset, alpha
    private val blobs = arrayOf(
        floatArrayOf(0.00f, 0.05f, 1.10f, 0.30f, 0.18f, 0.10f, 1.0f, 1.3f, 0f, 0.95f),
        floatArrayOf(-0.50f, 0.40f, 0.80f, 0.30f, 0.20f, 0.08f, 0.8f, 1.1f, 2f, 0.85f),
        floatArrayOf(0.50f, -0.05f, 0.70f, 0.22f, 0.22f, 0.10f, 1.2f, 0.9f, 4f, 0.80f),
        floatArrayOf(0.10f, 0.65f, 1.00f, 0.38f, 0.15f, 0.06f, 0.7f, 1.0f, 1f, 0.90f),
        floatArrayOf(-0.30f, -0.35f, 0.60f, 0.18f, 0.20f, 0.08f, 1.1f, 1.4f, 3f, 0.45f),
        floatArrayOf(0.40f, -0.20f, 0.50f, 0.16f, 0.18f, 0.08f, 0.9f, 1.2f, 5f, 0.40f)
    )

    init {
        cloudPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(
                Color.argb(235, 255, 255, 255),
                Color.argb(120, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        maskPaint.color = Color.BLACK
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun setMode(m: Int) {
        mode = m
    }

    fun setLevel(v: Float) {
        target = v.coerceIn(0f, 1f)
    }

    private fun step() {
        tick += 0.016f
        drift += 0.012f * (1f + 3f * energy)
        val t = when (mode) {
            1 -> maxOf(0.12f, target)
            2 -> 0.30f + 0.15f * sin(tick * 3f)
            3 -> 0.50f + 0.25f * sin(tick * 8f) * sin(tick * 2.3f)
            else -> 0.04f
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f * 0.86f
        basePaint.shader = LinearGradient(
            0f, cy - r, 0f, cy + r,
            intArrayOf(
                Color.rgb(52, 98, 232),
                Color.rgb(72, 120, 240),
                Color.rgb(150, 178, 245),
                Color.rgb(200, 214, 250)
            ),
            floatArrayOf(0f, 0.42f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            cx, cy, r * 1.4f,
            intArrayOf(Color.argb(110, 70, 120, 255), Color.argb(0, 70, 120, 255)),
            floatArrayOf(0.62f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f * 0.86f
        val e = energy.coerceIn(0f, 1f)
        val s = 1f + 0.02f * e + 0.03f * e * sin(tick * 7f)

        canvas.save()
        canvas.scale(s, s, cx, cy)

        glowPaint.alpha = (60 + 195 * e).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r * 1.4f, glowPaint)

        val layer = canvas.saveLayer(cx - r - 2f, cy - r - 2f, cx + r + 2f, cy + r + 2f, null)
        canvas.drawRect(cx - r, cy - r, cx + r, cy + r, basePaint)
        for (b in blobs) {
            val x = cx + (b[0] + b[4] * sin(drift * b[6] + b[8])) * r
            val y = cy + (b[1] + b[5] * sin(drift * b[7] + b[8] * 1.7f)) * r
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(b[2] * r, b[3] * r)
            cloudPaint.alpha = (b[9] * 255).toInt()
            canvas.drawCircle(0f, 0f, 1f, cloudPaint)
            canvas.restore()
        }
        canvas.drawCircle(cx, cy, r, maskPaint)
        canvas.restoreToCount(layer)

        canvas.restore()
    }
}

// Neele button ke andar waveform icon
class WaveIcon(context: Context) : View(context) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        p.color = Color.WHITE
        p.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.strokeWidth = w * 0.075f
        val xs = floatArrayOf(0.26f, 0.38f, 0.50f, 0.62f, 0.74f)
        val hs = floatArrayOf(0.22f, 0.42f, 0.62f, 0.36f, 0.20f)
        for (i in 0..4) {
            c.drawLine(w * xs[i], h * (0.5f - hs[i] / 2f), w * xs[i], h * (0.5f + hs[i] / 2f), p)
        }
    }
}
