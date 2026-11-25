package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * The lock above the floating button while it records: arrows leading up to an open lock
 * ("slide here for hands-free"), and a closed lock alone once the recording is locked. Drawn
 * in the button's blue with a light halo, no plate under it.
 */
class LockBadge(
    context: Context,
) : View(context) {
    companion object {
        const val WIDTH_DP = 44
        const val HEIGHT_DP = 76
        const val SHADOW_DP = 4
    }

    private val density = resources.displayMetrics.density

    var locked = false
        set(value) {
            field = value
            invalidate()
        }

    /** Below the button (no room above): the arrows point down and the lock is at the bottom. */
    var pointsDown = false
        set(value) {
            field = value
            invalidate()
        }

    /** How far the finger went toward the lock, 0..1: the arrows brighten as it does. */
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val halo = 0xF2FFFFFF.toInt()
    private val icon =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B6FA8.toInt()
            setShadowLayer(2.5f * density, 0f, 0f, halo)
        }
    private val keyhole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B6FA8.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(2.5f * density, 0f, 0f, halo)
        }
    private val arrow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1FA3D6.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2.6f * density
            setShadowLayer(2.5f * density, 0f, 0f, halo)
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Shadow layers under shapes need a software layer on older systems.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = SHADOW_DP * density
        val cx = width / 2f
        // The lock is at the end farthest from the button.
        val lockY = if (pointsDown) height - pad - 16 * density else pad + 18 * density
        if (locked) {
            drawLock(canvas, cx, lockY, open = false, scale = 1.15f)
            return
        }
        drawLock(canvas, cx, lockY, open = true, scale = 1f + 0.15f * progress)

        // Three chevrons from the button toward the lock, brightest at the button.
        val dir = if (pointsDown) 1f else -1f
        val start = if (pointsDown) pad + 6 * density else height - pad - 6 * density
        val a = 6.5f * density
        for (i in 0..2) {
            val y = start + dir * i * 13 * density
            arrow.alpha = (255 * ((0.95f - 0.32f * i) + 0.35f * progress).coerceIn(0.15f, 1f)).toInt()
            canvas.drawLine(cx - a, y - dir * a * 0.5f, cx, y + dir * a * 0.5f, arrow)
            canvas.drawLine(cx, y + dir * a * 0.5f, cx + a, y - dir * a * 0.5f, arrow)
        }
    }

    private fun drawLock(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        open: Boolean,
        scale: Float,
    ) {
        val d = density * scale
        val body = RectF(cx - 7 * d, cy - 2 * d, cx + 7 * d, cy + 9 * d)
        canvas.drawRoundRect(body, 2.5f * d, 2.5f * d, icon)
        stroke.strokeWidth = 2.4f * d
        val lift = if (open) 4 * d else 0f
        val arc = RectF(cx - 4.5f * d, cy - 10 * d - lift, cx + 4.5f * d, cy - 1 * d - lift)
        canvas.drawArc(arc, 180f, 180f, false, stroke)
        // Left leg always; the right one only when closed (the open shackle swings free).
        canvas.drawLine(arc.left, arc.centerY(), arc.left, body.top, stroke)
        if (!open) canvas.drawLine(arc.right, arc.centerY(), arc.right, body.top, stroke)
        canvas.drawCircle(cx, cy + 3.5f * d, 1.6f * d, keyhole)
    }
}
