package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import ru.gigapisar.R
import ru.gigapisar.settings.AppLanguage

/**
 * The floating button tucked into a screen edge: a narrow strip that stays out of the way.
 * Holding it starts a dictation exactly as holding the button does, so its touches are handed
 * to the button, which slides out next to the strip for as long as the recording lasts.
 */
class EdgeHandle(
    context: Context,
) : View(context) {
    companion object {
        /** The window is wider than the strip: a finger has to be able to land on it. */
        const val WIDTH_DP = 22
        const val HEIGHT_DP = 88
        private const val STRIP_WIDTH_DP = 5
        private const val STRIP_HEIGHT_DP = 60
        private const val STRIP_INSET_DP = 2
    }

    private val density = resources.displayMetrics.density

    /** Every touch, down to up; the owner passes it on to the button. */
    var onTouch: ((MotionEvent) -> Boolean)? = null

    /** Which screen edge the strip hugs. */
    var onRight = true
        set(value) {
            field = value
            invalidate()
        }

    /** Dimmed while the app is not ready (no model yet), as the button is. */
    var dimmed = false
        set(value) {
            field = value
            invalidate()
        }

    /** Invisible while the button is out: the window stays, so a touch in progress is not cut off. */
    var resting = true
        set(value) {
            if (field == value) return
            field = value
            animate().alpha(if (value) 1f else 0f).setDuration(120L).start()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1FA3D6.toInt() }
    private val outline =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
    private val strip = RectF()

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = AppLanguage.wrap(context.applicationContext).getString(R.string.button_idle)
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension((WIDTH_DP * density).toInt(), (HEIGHT_DP * density).toInt())
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        // The screen edge belongs to the system "back" gesture; on the strip a slide is ours.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = onTouch?.invoke(event) ?: false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = STRIP_WIDTH_DP * density
        val h = STRIP_HEIGHT_DP * density
        val inset = STRIP_INSET_DP * density
        val x = if (onRight) width - inset - w else inset
        strip.set(x, (height - h) / 2f, x + w, (height + h) / 2f)
        val alpha = if (dimmed) 115 else 235
        fill.alpha = alpha
        outline.alpha = alpha * 2 / 3
        canvas.drawRoundRect(strip, w / 2f, w / 2f, fill)
        canvas.drawRoundRect(strip, w / 2f, w / 2f, outline)
    }
}
