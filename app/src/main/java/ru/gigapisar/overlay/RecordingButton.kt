package ru.gigapisar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import ru.gigapisar.R
import ru.gigapisar.settings.AppLanguage
import kotlin.math.abs
import kotlin.math.hypot

class RecordingButton(
    context: Context,
) : View(context) {
    enum class State {
        IDLE,
        RECORDING,
        PROCESSING,
    }

    companion object {
        // The window is larger than the circle so the pulse rings fit around it.
        private const val BUTTON_SIZE_DP = 120
        private const val CIRCLE_RADIUS_DP = 43

        /** The circle is drawn on a 43 dp radius and scaled: small at rest, a little bigger in use. */
        const val IDLE_RADIUS_DP = 22f
        const val ACTIVE_RADIUS_DP = 29f

        /** Swipe distances grow with the button only a little: a long swipe is hard to finish. */
        fun gestureScale(sizeScale: Float) = sizeScale.coerceAtMost(1.25f)

        private const val RING_PERIOD_MS = 1600L

        /**
         * A move this soon after the touch moves the button; later the finger is sliding to the
         * lock or toward "cancel". The hints come in at the same moment, so what is on screen
         * says which of the two a move will be.
         */
        const val DRAG_WINDOW_MS = 450L

        /** A touch becomes a recording after this long, so pulling or moving the button records nothing. */
        private const val HOLD_DELAY_MS = 160L
        private const val VISIBILITY_ANIMATION_DURATION = 180L
        private const val SHAPE_ANIMATION_MS = 220L

        /** The circle waits this long before growing, so a quick drag does not make it flicker. */
        private const val GROW_DELAY_MS = 110L

        /** The spring that carries the circle out of a screen edge and back into it. */
        // Slow and without a bounce: the drop is thick, it does not twitch after the finger.
        private const val REACH_STIFFNESS = 170f
        private const val REACH_DAMPING = 27f

        /** The neck between the edge and the circle grows or thins out over this long. */
        private const val GOO_MS = 140f
        private const val LOCK_DISTANCE_DP = 80
        private const val CANCEL_DISTANCE_DP = 110
    }

    private val density = resources.displayMetrics.density

    private val buttonSize =
        (BUTTON_SIZE_DP * density).toInt()

    private val touchSlop =
        ViewConfiguration.get(context).scaledTouchSlop

    private var state = State.IDLE

/**
     * True while the current touch gesture is being used
     * to move the floating button.
     */
    private var dragging = false

/**
     * Previous raw pointer position.
     *
     * We use deltas instead of calculating the complete position
     * here because the owner of the overlay should decide where
     * the WindowManager.LayoutParams should be placed.
     */
    private var lastRawX = 0f
    private var lastRawY = 0f

    /** Where on the screen the finger was last seen, for the owner (a drop at the edge tucks the button in). */
    val touchX: Float
        get() = lastRawX

    val touchY: Float
        get() = lastRawY

    private var downRawX = 0f
    private var downRawY = 0f

/**
     * Whether recording was started for the current gesture.
     */
    private var recordingForCurrentGesture = false

    var onRecordingStart: (() -> Unit)? = null

    var onRecordingStop: (() -> Unit)? = null

/**
     * Called once when the finger moves far enough
     * to turn the gesture into a drag.
     */
    var onDragStart: (() -> Unit)? = null

/**
     * Receives movement delta in screen coordinates.
     *
     * dx/dy are relative to the previous MotionEvent,
     * not relative to ACTION_DOWN.
     */
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null

    var onDragEnd: (() -> Unit)? = null

    /**
     * Hands-free gestures, as in messengers: while holding, a slide up to the lock keeps the
     * recording going without the finger, a slide toward the middle of the screen throws it
     * away. [onSwipe] reports how far the finger went (inward and up, in pixels, only the
     * leading direction) so the owner can move the button and the hints along.
     */
    var onSwipe: ((inward: Float, up: Float) -> Unit)? = null
    var onLock: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    /** -1 when the middle of the screen is to the left of the button, +1 when to the right. */
    var inwardSign = -1f

    /** Slides to the lock and to "cancel"; off in the settings. */
    var gestures = true

    /** -1 when the lock is above the button, +1 when it had to go below. */
    var lockSign = -1f

    /** Locked by a slide up: the next tap on the button ends the recording. */
    var handsFree = false
        set(value) {
            field = value
            contentDescription = text(if (value) R.string.button_insert else currentDescription())
            invalidate()
        }

    /**
     * Side where the recording bar sits (-1 left, +1 right, 0 none): the button draws the bar's
     * end under itself, so the bar in its own window and the circle meet without a seam.
     */
    var barSide = 0
        set(value) {
            field = value
            invalidate()
        }

    /** What the current touch turned out to be. */
    private enum class Gesture { NONE, HOLD, TAP_HANDS_FREE, DONE }

    private var gesture = Gesture.NONE

    private val lockDistance get() = LOCK_DISTANCE_DP * density * gestureScale(sizeScale)
    private val cancelDistance get() = CANCEL_DISTANCE_DP * density * gestureScale(sizeScale)

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val iconPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

    private val circleRadius = CIRCLE_RADIUS_DP * density

    /** Size relative to the original; everything is drawn at the original size and scaled. */
    var sizeScale = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    /** Microphone level 0..1 while recording; drives the glow and the halos. */
    var level: () -> Float = { 0f }

    /** Dimmed while the app is not ready (no model yet). */
    var dimmed = false
        set(value) {
            field = value
            invalidate()
        }

    private val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val effectPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val arcPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
            color = 0xFF0B6FA8.toInt()
        }

    private var idleShader: Shader? = null

    /** How long the recording has run, under the button, in its blue with a light halo. */
    private val timePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0B6FA8.toInt()
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(2.5f * density, 0f, 0f, 0xF2FFFFFF.toInt())
        }
    private var smoothLevel = 0f
    private var animationStart = 0L
    private var recordingStartedAt = 0L

    /** Current size of the circle relative to its 43 dp drawing: see [IDLE_RADIUS_DP]. */
    private var shape = IDLE_RADIUS_DP / CIRCLE_RADIUS_DP
    private var shapeAnimator: android.animation.ValueAnimator? = null

    private val idleShape = IDLE_RADIUS_DP / CIRCLE_RADIUS_DP

    /** 0 at rest, 1 once the circle has grown: the glow, the halos and the time fade in with it. */
    private val liveness: Float
        get() {
            val full = (ACTIVE_RADIUS_DP - IDLE_RADIUS_DP) / sizeScale / CIRCLE_RADIUS_DP
            return ((shape - idleShape) / full).coerceIn(0f, 1f)
        }

    /*
     * Stuck to a screen edge, the button behaves like a drop: the circle sits [reach] pixels
     * from the edge (less than nothing when it is hidden behind it), a neck of the same colour
     * joins it to the edge, and a spring moves it. Not stuck, the circle is in the middle of
     * its window as usual.
     */

    /** +1 stuck to the right side of the window, -1 to the left, 0 free. */
    var stickSide = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** At this distance from the edge the neck is at its thinnest: the owner lets go there. */
    var gooReach = 0f

    var reach = 0f
        private set
    private var reachTarget = 0f

    /** The target is the middle of the window, wherever that is while the window changes size. */
    private var reachCentred = true
    private var reachVelocity = 0f
    private var reachEnd: (() -> Unit)? = null
    private var goo = 0f
    private var gooTarget = 0f
    private var dropFrameAt = 0L
    private var dropRunning = false
    private val gooPath = android.graphics.Path()
    private val gooPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Far enough behind the edge that neither the circle nor its shadow shows. */
    val hiddenReach: Float
        get() = -circleRadius * sizeScale * shape * 1.35f

    private val dropFrame =
        object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                val ms = (now - dropFrameAt).toFloat().coerceIn(4f, 32f)
                val dt = ms / 1000f
                dropFrameAt = now
                val target = if (reachCentred) width / 2f else reachTarget
                reachVelocity += (REACH_STIFFNESS * (target - reach) - REACH_DAMPING * reachVelocity) * dt
                reach += reachVelocity * dt
                goo =
                    if (goo < gooTarget) {
                        (goo + ms / GOO_MS).coerceAtMost(gooTarget)
                    } else {
                        (goo - ms / GOO_MS).coerceAtLeast(gooTarget)
                    }
                invalidate()
                val settled = abs(target - reach) < 0.5f && abs(reachVelocity) < 12f
                if (settled) {
                    reach = target
                    reachVelocity = 0f
                    val end = reachEnd
                    reachEnd = null
                    end?.invoke()
                }
                if (settled && goo == gooTarget) {
                    dropRunning = false
                    return
                }
                postOnAnimation(this)
            }
        }

    private fun runDrop() {
        if (dropRunning) return
        dropRunning = true
        dropFrameAt = SystemClock.uptimeMillis()
        postOnAnimation(dropFrame)
    }

    /** Springs the circle to [target] pixels from the edge, or to the middle of the window. */
    fun reachTo(
        target: Float? = null,
        onEnd: (() -> Unit)? = null,
    ) {
        reachCentred = target == null
        reachTarget = target ?: 0f
        reachEnd = onEnd
        if (visibility != VISIBLE || !isAttachedToWindow) {
            setReach(target)
            return
        }
        runDrop()
    }

    /** Without the spring. */
    fun setReach(value: Float?) {
        reachCentred = value == null
        reachTarget = value ?: 0f
        reach = value ?: (width / 2f)
        reachVelocity = 0f
        invalidate()
        val end = reachEnd
        reachEnd = null
        end?.invoke()
    }

    /** The neck: 1 joins the circle to the edge, 0 is none. */
    fun gooTo(
        target: Float,
        instant: Boolean = false,
    ) {
        gooTarget = target
        if (instant || visibility != VISIBLE || !isAttachedToWindow) {
            goo = target
            invalidate()
        } else {
            runDrop()
        }
    }

    /** The drop between the edge of the window and the circle at [cx]; [radius] is the circle's. */
    private fun drawGoo(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        alpha: Int,
    ) {
        val edge = if (stickSide > 0) width.toFloat() else 0f
        val inward = if (stickSide > 0) -1f else 1f
        val gap = abs(edge - cx)
        val far = (gooReach - radius).coerceAtLeast(1f)
        val stretch = ((gap - radius) / far).coerceIn(0f, 1f)
        // Wide where it leaves the edge, thinner the further the circle is pulled.
        val base = radius * (1f - 0.42f * stretch) * goo
        val waist = radius * (0.88f - 0.62f * stretch) * goo
        gooPath.rewind()
        gooPath.moveTo(edge, cy - base)
        gooPath.cubicTo(edge + inward * gap * 0.45f, cy - waist, cx - inward * gap * 0.5f, cy - radius, cx, cy - radius)
        gooPath.lineTo(cx, cy + radius)
        gooPath.cubicTo(cx - inward * gap * 0.5f, cy + radius, edge + inward * gap * 0.45f, cy + waist, edge, cy + base)
        gooPath.close()
        // The circle's gradient runs light to deep from left to right: the neck takes the near end.
        gooPaint.color = if (stickSide > 0) 0xFF1B9BD0.toInt() else 0xFF4FC3E4.toInt()
        gooPaint.alpha = alpha
        canvas.drawPath(gooPath, gooPaint)
    }

    private fun animateShape(
        to: Float,
        delay: Long = 0L,
    ) {
        if (shape == to) {
            shapeAnimator?.cancel()
            return
        }
        shapeAnimator?.cancel()
        shapeAnimator =
            android.animation.ValueAnimator.ofFloat(shape, to).apply {
                duration = SHAPE_ANIMATION_MS
                startDelay = delay
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    shape = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    private val frame =
        object : Runnable {
            override fun run() {
                if (state == State.IDLE || visibility != VISIBLE) return
                invalidate()
                postOnAnimation(this)
            }
        }

    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
        }

    init {
        visibility = GONE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f

        isClickable = true
        isFocusable = true

        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES

        contentDescription =
            context.getString(R.string.button_idle)

        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // The owner sizes the window: tight around the small button at rest, roomier while recording.
        val size = (buttonSize * sizeScale).toInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = handleTouch(event, external = false)

    /**
     * A touch that landed on the edge handle and is passed on from there: the same gestures,
     * measured in screen coordinates, without the "is the finger on the circle" check.
     */
    fun externalTouch(event: MotionEvent): Boolean = handleTouch(event, external = true)

    private fun handleTouch(
        event: MotionEvent,
        external: Boolean,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!external &&
                    hypot(event.x - width / 2f, event.y - height / 2f) >
                    maxOf(circleRadius * shape + 8 * density, 26 * density) * sizeScale
                ) {
                    return false
                }

                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false

                if (state == State.RECORDING && handsFree) {
                    // The tap that ends a hands-free recording (or a slide that cancels it).
                    gesture = Gesture.TAP_HANDS_FREE
                    pressIn()
                    return true
                }

                if (state != State.IDLE) {
                    return false
                }

                gesture = Gesture.HOLD
                holdPending = true
                recordingForCurrentGesture = false
                // A moment later, not at once: a finger that came to move the button records nothing.
                postDelayed(startHold, HOLD_DELAY_MS)
                pressIn()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY

                when (gesture) {
                    Gesture.HOLD -> {
                        if (dragging) {
                            dragBy(event)
                            return true
                        }
                        if (!recordingForCurrentGesture) {
                            // Still waiting to become a recording: a move makes it a drag instead.
                            if (holdPending && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                startDrag(event)
                            }
                            return true
                        }
                        if (state != State.RECORDING) return true

                        // Only a quick move right after the touch moves the button; later the
                        // finger is sliding to the lock or toward "cancel".
                        if (event.eventTime - event.downTime <= DRAG_WINDOW_MS) {
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                startDrag(event)
                            }
                            return true
                        }

                        if (!gestures) return true

                        val inward = (totalDx * inwardSign).coerceAtLeast(0f)
                        val up = (totalDy * lockSign).coerceAtLeast(0f)
                        when {
                            up >= lockDistance && up >= inward -> {
                                gesture = Gesture.DONE
                                onSwipe?.invoke(0f, 0f)
                                pressOut()
                                handsFree = true
                                onLock?.invoke()
                            }
                            inward >= cancelDistance && inward > up -> {
                                gesture = Gesture.DONE
                                recordingForCurrentGesture = false
                                onSwipe?.invoke(0f, 0f)
                                pressOut()
                                onCancel?.invoke()
                            }
                            else -> onSwipe?.invoke(if (inward > up) inward else 0f, if (up >= inward) up else 0f)
                        }
                        return true
                    }

                    Gesture.TAP_HANDS_FREE -> {
                        val inward = (totalDx * inwardSign).coerceAtLeast(0f)
                        if (inward >= cancelDistance) {
                            gesture = Gesture.DONE
                            onSwipe?.invoke(0f, 0f)
                            pressOut()
                            onCancel?.invoke()
                        } else {
                            onSwipe?.invoke(inward, 0f)
                        }
                        return true
                    }

                    else -> return true
                }
            }

            MotionEvent.ACTION_UP -> {
                finishGesture(cancelled = false, event = event)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                finishGesture(cancelled = true, event = event)
                return true
            }
        }

        return true
    }

    private var holdPending = false

    private val startHold =
        Runnable {
            holdPending = false
            if (gesture != Gesture.HOLD || dragging || state != State.IDLE) return@Runnable
            recordingForCurrentGesture = true
            setState(State.RECORDING)
            onRecordingStart?.invoke()
        }

    private fun startDrag(event: MotionEvent) {
        // The user wants to move the button: drop the recording that the touch started.
        dragging = true
        holdPending = false
        removeCallbacks(startHold)
        if (recordingForCurrentGesture) {
            recordingForCurrentGesture = false
            setState(State.IDLE)
            onRecordingStop?.invoke()
        }
        pressOut()
        onDragStart?.invoke()
        // The owner gets movement from this point on, not the whole distance from the touch.
        lastRawX = event.rawX
        lastRawY = event.rawY
    }

    private fun dragBy(event: MotionEvent) {
        // Deltas since the previous event; the owner decides where the window goes.
        val dx = event.rawX - lastRawX
        val dy = event.rawY - lastRawY
        // First, so the owner sees where the finger is now.
        lastRawX = event.rawX
        lastRawY = event.rawY
        if (dx != 0f || dy != 0f) {
            onDrag?.invoke(dx, dy)
        }
    }

    /** The press shows as the circle growing (see [animateShape]), not as the old shrink. */
    private fun pressIn() {
        animate().scaleX(1f).scaleY(1f).setDuration(80L).start()
    }

    private fun pressOut() {
        animate().scaleX(1f).scaleY(1f).setDuration(80L).start()
    }

    private fun finishGesture(
        cancelled: Boolean,
        event: MotionEvent,
    ) {
        if (holdPending) {
            // A tap shorter than the wait still counts as a press, as it always did.
            removeCallbacks(startHold)
            if (cancelled) holdPending = false else startHold.run()
        }
        val wasGesture = gesture
        val wasDragging = dragging
        val wasRecording = recordingForCurrentGesture
        gesture = Gesture.NONE
        dragging = false
        recordingForCurrentGesture = false
        pressOut()

        when {
            wasDragging -> onDragEnd?.invoke()

            wasGesture == Gesture.HOLD && wasRecording -> {
                onSwipe?.invoke(0f, 0f)
                // The recorder may have stopped by itself (5-minute limit) under the finger.
                if (state == State.RECORDING) {
                    setState(State.PROCESSING)
                    onRecordingStop?.invoke()
                }
            }

            wasGesture == Gesture.TAP_HANDS_FREE -> {
                onSwipe?.invoke(0f, 0f)
                val moved = abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop
                if (!cancelled && !moved && state == State.RECORDING) {
                    handsFree = false
                    setState(State.PROCESSING)
                    onRecordingStop?.invoke()
                }
            }
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        val c = w / 2f
        val r = circleRadius
        // Turquoise blue: light cyan to deep blue.
        idleShader =
            LinearGradient(
                c - r,
                c - r,
                c + r,
                c + r,
                intArrayOf(0xFF7FE3F0.toInt(), 0xFF1FA3D6.toInt(), 0xFF0B6FA8.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        // A soft shadow under the circle, a little lower than its centre.
        shadowPaint.shader =
            RadialGradient(
                c,
                c + 5 * density,
                r * 1.28f,
                intArrayOf(0x66000000, 0x40000000, 0x00000000),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val c = width / 2f
        val r = circleRadius
        val alpha = if (dimmed) 115 else 255
        val k = sizeScale * shape

        val live = liveness

        canvas.save()
        if (stickSide != 0) {
            val cx = if (stickSide > 0) width - reach else reach
            if (goo > 0f && reach > -r * k) drawGoo(canvas, cx, c, r * k, alpha)
            canvas.translate(cx - c, 0f)
        }
        canvas.save()
        canvas.scale(k, k, c, c)
        val t = (SystemClock.uptimeMillis() - animationStart).toFloat()

        if (state == State.RECORDING) {
            // Glow that follows the voice, on the same dB scale as the pill. The button stays blue.
            val raw = level()
            val loud = raw.coerceIn(0f, 1f)
            smoothLevel += (loud - smoothLevel) * 0.3f
            effectPaint.style = Paint.Style.FILL
            effectPaint.color = ((0x38 * live).toInt() shl 24) or 0x1FA3D6
            canvas.drawCircle(c, c, r * (1.08f + 0.3f * smoothLevel), effectPaint)

            // Two halos spreading out from the button, brighter while speaking.
            effectPaint.style = Paint.Style.STROKE
            effectPaint.strokeWidth = 4f * density
            for (i in 0..1) {
                val p = ((t / RING_PERIOD_MS) + i * 0.5f) % 1f
                val strength = 0.2f + 0.6f * smoothLevel
                effectPaint.color = ((strength * (1f - p) * live * 255).toInt() shl 24) or 0x1FA3D6
                canvas.drawCircle(c, c, r * (1f + 0.34f * p), effectPaint)
            }
        }

        // A drop joined to the edge casts no shadow of its own.
        shadowPaint.alpha = if (stickSide != 0) (alpha * (1f - goo)).toInt() else alpha
        canvas.drawCircle(c, c + 5 * density, r * 1.28f, shadowPaint)
        paint.shader = idleShader
        paint.alpha = alpha
        canvas.drawCircle(c, c, r, paint)

        when (state) {
            State.RECORDING ->
                if (handsFree) {
                    drawCheck(canvas = canvas, cx = c, cy = c)
                } else {
                    drawMicrophone(canvas = canvas, cx = c, cy = c)
                }
            State.PROCESSING -> {
                val sweepStart = (t / 900f * 360f) % 360f
                val o = r + 7 * density
                canvas.drawArc(RectF(c - o, c - o, c + o, c + o), sweepStart, 110f, false, arcPaint)
                drawMicrophone(canvas = canvas, cx = c, cy = c)
            }
            State.IDLE -> {
                iconPaint.alpha = alpha
                strokePaint.alpha = alpha
                drawMicrophone(canvas = canvas, cx = c, cy = c)
            }
        }
        canvas.restore()

        if (state == State.RECORDING) {
            // The time, under the circle.
            val seconds = (SystemClock.uptimeMillis() - recordingStartedAt) / 1000
            timePaint.textSize = 12.5f * density * sizeScale.coerceAtLeast(0.9f)
            timePaint.alpha = (255 * live).toInt()
            canvas.drawText(
                "%d:%02d".format(seconds / 60, seconds % 60),
                c,
                c + r * k + 15 * density * sizeScale,
                timePaint,
            )
        }
        canvas.restore()
    }

    /** Hands-free: a tap on the button now ends the recording and inserts the text. */
    private fun drawCheck(
        canvas: Canvas,
        cx: Float,
        cy: Float,
    ) {
        strokePaint.strokeWidth = 5f * density
        canvas.drawLine(cx - 15 * density, cy + 1 * density, cx - 4 * density, cy + 12 * density, strokePaint)
        canvas.drawLine(cx - 4 * density, cy + 12 * density, cx + 16 * density, cy - 11 * density, strokePaint)
    }

    /** In the language chosen in the app, not only the phone's. */
    private fun text(id: Int) = AppLanguage.wrap(context.applicationContext).getString(id)

    private fun currentDescription() =
        when (state) {
            State.IDLE -> R.string.button_idle
            State.RECORDING -> R.string.button_recording
            State.PROCESSING -> R.string.button_processing
        }

    fun setState(value: State) {
        if (value == State.RECORDING && state != State.RECORDING) {
            recordingStartedAt = SystemClock.uptimeMillis()
        }
        val fromRest = state == State.IDLE
        state = value
        // In use the circle grows by the same few dp at any size, so a big button does not balloon.
        val growth = if (value == State.IDLE) 0f else (ACTIVE_RADIUS_DP - IDLE_RADIUS_DP) / sizeScale
        animateShape(
            to = (IDLE_RADIUS_DP + growth) / CIRCLE_RADIUS_DP,
            delay = if (fromRest && value == State.RECORDING) GROW_DELAY_MS else 0L,
        )
        if (value != State.RECORDING) {
            handsFree = false
            barSide = 0
        }

        contentDescription = text(currentDescription())

        iconPaint.alpha = 255
        strokePaint.alpha = 255
        removeCallbacks(frame)
        if (value != State.IDLE) {
            animationStart = SystemClock.uptimeMillis()
            if (value == State.RECORDING) {
                smoothLevel = 0f
            }
            postOnAnimation(frame)
        }
        invalidate()
    }

    fun reset() {
        setState(State.IDLE)

        dragging = false
        recordingForCurrentGesture = false
        gesture = Gesture.NONE
        holdPending = false
        removeCallbacks(startHold)

        animate().cancel()

        scaleX = 1f
        scaleY = 1f
    }

    /** A fade-out is under way: the view is still visible but on its way out. */
    private var hiding = false

    fun showAnimated(onEnd: (() -> Unit)? = null) {
        if (visibility == VISIBLE && !hiding) {
            onEnd?.invoke()
            return
        }

        hiding = false
        animate().cancel()

        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f

        animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(VISIBILITY_ANIMATION_DURATION)
            .withEndAction {
                onEnd?.invoke()
            }.start()
    }

    fun hideAnimated(onEnd: (() -> Unit)? = null) {
        if (visibility != VISIBLE) {
            onEnd?.invoke()
            return
        }

        animate().cancel()
        hiding = true

        animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(VISIBILITY_ANIMATION_DURATION)
            .withEndAction {
                hiding = false
                visibility = GONE
                alpha = 0f
                scaleX = 0.9f
                scaleY = 0.9f

                onEnd?.invoke()
            }.start()
    }

    /** Gone at once: the button has already slid behind the screen edge. */
    fun hideNow() {
        animate().cancel()
        hiding = false
        visibility = GONE
        alpha = 0f
        scaleX = 0.9f
        scaleY = 0.9f
    }

    private fun drawMicrophone(
        canvas: Canvas,
        cx: Float,
        cy: Float,
    ) {
        // Drawn on a 24-unit grid centred on (12, 12), about 35 dp across.
        val u = 1.45f * density

        fun x(v: Float) = cx + (v - 12f) * u

        fun y(v: Float) = cy + (v - 12f) * u

        strokePaint.strokeWidth = 2.2f * u
        canvas.drawRoundRect(RectF(x(8.5f), y(2.5f), x(15.5f), y(14.5f)), 3.5f * u, 3.5f * u, iconPaint)
        canvas.drawArc(RectF(x(5f), y(4f), x(19f), y(18f)), 0f, 180f, false, strokePaint)
        canvas.drawLine(x(12f), y(18f), x(12f), y(21.5f), strokePaint)
        canvas.drawLine(x(8.5f), y(21.5f), x(15.5f), y(21.5f), strokePaint)
    }
}
