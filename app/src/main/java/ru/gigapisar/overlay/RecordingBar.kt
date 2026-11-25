package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/**
 * The hint next to the floating button while it records: arrows leading away from the button
 * to a bin, where a slide throws the recording away. No plate under it: the arrows and the bin
 * are drawn in the button's blue with a light halo, so they read on any background.
 */
class RecordingBar(
    context: Context,
) : View(context) {
    companion object {
        const val HEIGHT_DP = 48

        /** Room around the drawing for its halo. */
        const val SHADOW_DP = 4
        private const val BIN_BOX_DP = 44
        private const val ARROW_STEP_DP = 13
    }

    private val density = resources.displayMetrics.density

    /** -1: the hint is left of the button (the bin at its left end), +1: right of it. */
    var side = -1
        set(value) {
            field = value
            invalidate()
        }

    /** Hands-free: the arrows stop moving and a tap on the bin cancels. */
    var handsFree = false
        set(value) {
            field = value
            invalidate()
        }

    /** How far the finger went toward the bin, 0..1. */
    var cancelProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onCancel: (() -> Unit)? = null

    private var startedAt = SystemClock.uptimeMillis()

    private val halo = 0xF2FFFFFF.toInt()
    private val arrowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1FA3D6.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.6f * density
            setShadowLayer(2.5f * density, 0f, 0f, halo)
        }
    private val binPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B6FA8.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(2.5f * density, 0f, 0f, halo)
        }

    private var binRect = RectF()

    private val frame =
        object : Runnable {
            override fun run() {
                if (visibility != VISIBLE || windowToken == null) return
                invalidate()
                postOnAnimation(this)
            }
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Shadow layers under shapes need a software layer on older systems.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun restart() {
        startedAt = SystemClock.uptimeMillis()
        handsFree = false
        cancelProgress = 0f
        removeCallbacks(frame)
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val cy = height / 2f
        val box = BIN_BOX_DP * density
        // The bin sits at the far end, the arrows run from the button toward it.
        val binX = if (side < 0) box / 2f else w - box / 2f
        binRect = RectF(binX - box / 2f, 0f, binX + box / 2f, height.toFloat())
        drawBin(canvas, binX, cy, 1f + 0.3f * cancelProgress)

        val t = SystemClock.uptimeMillis() - startedAt
        val step = ARROW_STEP_DP * density
        val a = 6.5f * density
        val nearEnd = if (side < 0) w - 10 * density else 10 * density
        val limit = if (side < 0) binX + box / 2f else binX - box / 2f
        var i = 0
        while (true) {
            val x = nearEnd + side * i * step
            if (if (side < 0) x - a < limit else x + a > limit) break
            // Brightest at the button, fading toward the bin; a slow ripple runs along them.
            val base = (0.95f - 0.2f * i).coerceAtLeast(0.2f)
            val ripple = if (handsFree) 0f else 0.15f * sin(t / 1000f * 2f * Math.PI.toFloat() - i * 0.9f)
            arrowPaint.alpha = (255 * (base + ripple + 0.3f * cancelProgress).coerceIn(0.15f, 1f)).toInt()
            // A chevron pointing at the bin.
            canvas.drawLine(x - side * a * 0.5f, cy - a, x + side * a * 0.5f, cy, arrowPaint)
            canvas.drawLine(x + side * a * 0.5f, cy, x - side * a * 0.5f, cy + a, arrowPaint)
            i++
        }
    }

    private fun drawBin(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float,
    ) {
        val d = density * scale
        binPaint.strokeWidth = 2.2f * d
        // Lid, handle, body, two ribs.
        canvas.drawLine(cx - 9 * d, cy - 6 * d, cx + 9 * d, cy - 6 * d, binPaint)
        canvas.drawLine(cx - 3.5f * d, cy - 6 * d, cx - 3.5f * d, cy - 9.5f * d, binPaint)
        canvas.drawLine(cx - 3.5f * d, cy - 9.5f * d, cx + 3.5f * d, cy - 9.5f * d, binPaint)
        canvas.drawLine(cx + 3.5f * d, cy - 9.5f * d, cx + 3.5f * d, cy - 6 * d, binPaint)
        canvas.drawLine(cx - 6.5f * d, cy - 6 * d, cx - 5.5f * d, cy + 10 * d, binPaint)
        canvas.drawLine(cx - 5.5f * d, cy + 10 * d, cx + 5.5f * d, cy + 10 * d, binPaint)
        canvas.drawLine(cx + 5.5f * d, cy + 10 * d, cx + 6.5f * d, cy - 6 * d, binPaint)
        binPaint.strokeWidth = 1.8f * d
        canvas.drawLine(cx - 2.2f * d, cy - 1.5f * d, cx - 2.2f * d, cy + 5.5f * d, binPaint)
        canvas.drawLine(cx + 2.2f * d, cy - 1.5f * d, cx + 2.2f * d, cy + 5.5f * d, binPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!handsFree) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return binRect.contains(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (binRect.contains(event.x, event.y)) onCancel?.invoke()
                return true
            }
        }
        return true
    }
}
