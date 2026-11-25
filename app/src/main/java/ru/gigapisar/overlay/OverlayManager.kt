package ru.gigapisar.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.roundToInt

class OverlayManager(
    private val service: AccessibilityService,
    onRecordingStart: () -> Unit,
    onRecordingStop: () -> Unit,
    private val onRecordingCancel: () -> Unit,
    private val onLocked: () -> Unit,
) {
    companion object {
        private const val POSITION_PREFERENCES = "overlay_position"
        private const val POSITION_X_KEY = "x"
        private const val POSITION_Y_KEY = "y"
        private const val HANDLE_RIGHT_KEY = "handle_right"
        private const val HANDLE_Y_KEY = "handle_y"

        /** Lower than the middle: under the thumb, and below where other edge panels usually sit. */
        private const val HANDLE_DEFAULT_Y = 0.58f

        private const val GEOMETRY_KEY = "geometry"

        /** The window before the small button: saved positions were measured for it. */
        private const val OLD_BUTTON_SIZE_DP = 120

        /** Tight around the small button at rest, so it blocks no more of the screen than it shows. */
        private const val IDLE_SIZE_DP = 64

        /** Roomier while recording: the halos and the time under the circle have to fit. */
        private const val ACTIVE_SIZE_DP = 104
        private const val EDGE_MARGIN_DP = 0

        /** The arrows-and-bin hint next to the button. */
        private const val BAR_MAX_DP = 150
        private const val BAR_MIN_DP = 100

        /*
         * The edge works as a magnet. A button dragged with the finger this close to a screen
         * edge is caught by it; a caught button lets go once the finger is this far from the edge.
         * The gap between the two keeps it from flickering at the border.
         */
        private const val CATCH_DP = 20
        private const val RELEASE_DP = 46

        /** A touch on the strip first brings out this much of the drop, under the finger. */
        private const val PEEK_DP = 9

        /** A dragged button trails the finger by about this much: it is heavy and never jumps. */
        private const val FOLLOW_MS = 120f

        /** The lock and the bin wait this long: a touch that turns into a drag never shows them. */
        private const val HINT_DELAY_MS = RecordingButton.DRAG_WINDOW_MS
        private const val HINT_FADE_IN_MS = 220L
        private const val HINT_FADE_OUT_MS = 150L
    }

    private val density =
        service.resources.displayMetrics.density

    private var scale = 1f

    /** The button's window is bigger while a recording or a recognition is under way. */
    private var expanded = false

    /** The window is as big while the edge holds a dragged button: the drop needs the room. */
    private var stretched = false

    private val buttonSize: Int
        get() = ((if (expanded || stretched) ACTIVE_SIZE_DP else IDLE_SIZE_DP) * density * scale).roundToInt()

    /** Transparent room between the window's edge and the circle while recording. */
    private val activeInset: Int
        get() = ((ACTIVE_SIZE_DP / 2f - RecordingButton.ACTIVE_RADIUS_DP) * density * scale).roundToInt()

    private val edgeMargin =
        (EDGE_MARGIN_DP * density).roundToInt()

    /** Where the small window was before it grew, to put it back exactly. */
    private var idleX = 0
    private var idleY = 0

    /** The button was moved while its window was big: its place is where it is now. */
    private var movedWhileExpanded = false

    /** The button went into the edge or came out of it by a gesture; the owner saves the setting. */
    var onTuckedChange: ((Boolean) -> Unit)? = null

    /** A short tick when the edge catches the button or lets it go. */
    var onTick: (() -> Unit)? = null

    private val windowManager =
        service.getSystemService(
            WindowManager::class.java,
        )

    private val button =
        RecordingButton(service)

    private val params =
        WindowManager
            .LayoutParams(
                buttonSize,
                buttonSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity =
                    Gravity.TOP or Gravity.START
            }

    private var attached = false

    /*
     * Edge handle mode: at rest the button is hidden and a narrow strip sits at a screen edge.
     * Holding the strip slides the button out next to it and dictates as holding the button
     * does; when the dictation is over the button goes back in.
     */
    private val handle = EdgeHandle(service)

    private val handleParams =
        WindowManager
            .LayoutParams(
                (EdgeHandle.WIDTH_DP * density).roundToInt(),
                (EdgeHandle.HEIGHT_DP * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

    private var handleRight = true
    private var handleY = HANDLE_DEFAULT_Y

    /** A finger is moving the button right now. */
    private var dragging = false

    /** The dragged button is held by a screen edge (see [CATCH_DP]), and by which one. */
    private var caught = false
    private var caughtRight = true

    /** A drag that starts with the finger already at the edge must leave it once before it can be caught. */
    private var catchArmed = true

    /** Where the dragged button is headed, before rounding to pixels. */
    private var dragX = 0f
    private var dragY = 0f
    private var followFrameAt = 0L

    /** What the service last asked for: the button (or its strip) on screen or not. */
    private var wanted = false

    /** A recording or a recognition is under way. */
    private var active = false

    /** The button is out of the strip. */
    private var buttonOut = false

    var edgeHandle = false
        set(value) {
            if (field == value) return
            field = value
            hideChrome()
            // A button that is on screen now counts as "out": it goes in unless a recording runs.
            buttonOut = value && button.visibility == android.view.View.VISIBLE
            if (value) {
                loadHandlePosition()
            } else {
                freeButton()
                removeWindow(handle)
                if (attached) {
                    restorePosition()
                    syncTargets()
                    updateWindow(button, params)
                }
            }
            setButtonVisible(wanted)
        }

    /*
     * While recording: the strip with the time and "‹ ‹ Cancel" next to the button and the
     * lock above it, each in its own window that takes no touches (the strip takes a tap on
     * "Cancel" once hands-free). The button window was added first, so these never cover it:
     * they are placed beside it, not over it.
     */
    private val bar = RecordingBar(service)
    private val lock = LockBadge(service)
    private var chromeShown = false

    /** The hints are on screen, not just placed and waiting for [HINT_DELAY_MS]. */
    private var chromeRevealed = false
    private val revealChrome =
        Runnable {
            if (!chromeShown || chromeRevealed) return@Runnable
            chromeRevealed = true
            bar.animate().alpha(1f).setDuration(HINT_FADE_IN_MS).start()
            lock.animate().alpha(1f).setDuration(HINT_FADE_IN_MS).start()
        }
    private var homeX = 0
    private var homeY = 0

    /** Where the hint's window starts when nothing is sliding. */
    private var barHomeX = 0
    private var barMargin = (8 * density).roundToInt()

    /** The strip's width at rest, before a slide shortens it. */
    private var barBase = 0

    private val barParams =
        WindowManager
            .LayoutParams(
                0,
                ((RecordingBar.HEIGHT_DP + 2 * RecordingBar.SHADOW_DP) * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

    private val lockParams =
        WindowManager
            .LayoutParams(
                ((LockBadge.WIDTH_DP + 2 * LockBadge.SHADOW_DP) * density).roundToInt(),
                ((LockBadge.HEIGHT_DP + 2 * LockBadge.SHADOW_DP) * density).roundToInt(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

/*
 * Position that should be displayed on the next frame.
 */
    private var targetX = 0
    private var targetY = 0

/*
 * Prevents scheduling multiple frame callbacks.
 */
    private var frameUpdateScheduled = false

    private var pendingFrameUpdate: Runnable? = null

    init {
        button.onRecordingStart =
            onRecordingStart

        button.onRecordingStop =
            onRecordingStop

        button.onDragStart =
            ::beginDrag

        button.onDrag =
            ::moveBy

        button.onDragEnd =
            ::savePosition

        button.onSwipe = ::followSwipe

        button.onLock = {
            revealChromeNow()
            lock.locked = true
            lock.progress = 0f
            bar.handsFree = true
            barParams.flags = barParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            updateWindow(bar, barParams)
            onLocked()
        }

        button.onCancel = {
            hideChrome()
            onRecordingCancel()
        }

        bar.onCancel = {
            hideChrome()
            onRecordingCancel()
        }

        handle.onTouch = { event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) slideButtonOut(peekAt = event.rawY)
            val taken = button.externalTouch(event)
            val over =
                event.actionMasked == android.view.MotionEvent.ACTION_UP ||
                    event.actionMasked == android.view.MotionEvent.ACTION_CANCEL
            // A touch that started nothing (not ready, already busy) must not leave the button out.
            if (over || !taken) button.post { applyHandleMode() }
            taken
        }
    }

    fun attach() {
        if (attached) {
            return
        }

        restorePosition()
        syncTargets()

        try {
            windowManager.addView(
                button,
                params,
            )

            attached = true
        } catch (_: Exception) {
            attached = false
        }
    }

    fun setButtonVisible(visible: Boolean) {
        wanted = visible

        if (!attached && visible) {
            attach()
        }

        if (!attached) {
            return
        }

        if (edgeHandle) {
            applyHandleMode()
            return
        }

        if (visible) {
            button.showAnimated()
        } else {
            hideChrome()
            button.hideAnimated()
        }
    }

    /** Resizes the button (the settings slider); keeps its centre where it was and on screen. */
    fun setScale(value: Float) {
        if (value == scale) return
        val oldSize = buttonSize
        scale = value
        button.sizeScale = value
        val newSize = buttonSize
        params.width = newSize
        params.height = newSize
        val bounds = getScreenBounds()
        params.x =
            (params.x + (oldSize - newSize) / 2).coerceIn(edgeMargin, (bounds.width - newSize - edgeMargin).coerceAtLeast(edgeMargin))
        params.y =
            (params.y + (oldSize - newSize) / 2).coerceIn(edgeMargin, (bounds.height - newSize - edgeMargin).coerceAtLeast(edgeMargin))
        if (edgeHandle) placeButtonAtHandle()
        if (attached) {
            try {
                windowManager.updateViewLayout(button, params)
            } catch (_: Exception) {
                // The view may be on its way out.
            }
        }
    }

    /**
     * The size slider is moving: in edge handle mode the button comes out so the size is visible,
     * and goes back behind the strip when the preview ends.
     */
    var previewing = false
        set(value) {
            if (field == value) return
            field = value
            if (!edgeHandle || !wanted) return
            if (value) slideButtonOut() else applyHandleMode()
        }

    /** Half-transparent while the app is not ready (no model yet): a tap then leads to setup. */
    fun setAvailable(available: Boolean) {
        button.dimmed = !available
        handle.dimmed = !available
    }

    /** Microphone level source for the pulse while recording. */
    fun setLevelSource(level: () -> Float) {
        button.level = level
    }

    fun setIdle() {
        hideChrome()
        keepScreenOn(false)
        button.setState(
            RecordingButton.State.IDLE,
        )
        active = false
        setExpanded(false)
        if (edgeHandle) applyHandleMode()
    }

    /**
     * Grows the button's window around its centre for a recording and shrinks it back after:
     * at rest the window is no bigger than the small button, so it swallows no stray touches.
     */
    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        val old = buttonSize
        if (value) {
            idleX = params.x
            idleY = params.y
            movedWhileExpanded = false
        }
        expanded = value
        val size = buttonSize
        // Already that size for a drag at the edge: nothing to move.
        if (size == old) return
        val shift = (size - old) / 2
        params.width = size
        params.height = size
        if (!value && !movedWhileExpanded) {
            params.x = idleX
            params.y = idleY
        } else {
            val bounds = getScreenBounds()
            params.x = (params.x - shift).coerceIn(0, (bounds.width - size).coerceAtLeast(0))
            params.y = (params.y - shift).coerceIn(0, (bounds.height - size).coerceAtLeast(0))
        }
        syncTargets()
        if (attached) updateWindow(button, params)
    }

    /** Same room as for a recording, around the same centre, while the edge holds a dragged button. */
    private fun setStretched(value: Boolean) {
        if (stretched == value) return
        val old = buttonSize
        stretched = value
        val size = buttonSize
        if (size == old) return
        val shift = (size - old) / 2
        val bounds = getScreenBounds()
        params.width = size
        params.height = size
        params.x = (params.x - shift).coerceIn(0, (bounds.width - size).coerceAtLeast(0))
        params.y = (params.y - shift).coerceIn(0, (bounds.height - size).coerceAtLeast(0))
        syncTargets()
        if (attached) updateWindow(button, params)
    }

    /** The circle back in the middle of its window, no drop: an ordinary floating button. */
    private fun freeButton() {
        button.gooTo(0f, instant = true)
        button.stickSide = 0
        button.setReach(null)
    }

    private fun stickButton(right: Boolean) {
        button.stickSide = if (right) 1 else -1
        button.gooReach = RELEASE_DP * density
    }

    /** Hints (strip and lock) only when the button itself started the recording. */
    fun setRecording(withHints: Boolean = true) {
        active = true
        letButtonOut()
        setExpanded(true)
        keepScreenOn(true)
        button.setState(
            RecordingButton.State.RECORDING,
        )
        if (withHints) showChrome()
    }

    /**
     * The strip and the lock draw around the floating button and make no sense without it: with the
     * button turned off a recording is shown by the pill instead.
     */
    val canShowStrip: Boolean
        get() = attached && button.visibility == android.view.View.VISIBLE

    val handsFree: Boolean
        get() = button.handsFree

    /**
     * A double press of the volume key: the recording already runs without anyone holding
     * anything, so the button says "Вставить" and a tap on it ends the recording. When the strip is
     * on screen it drops the lock hint and starts taking taps on "Cancel".
     */
    fun lockHandsFree() {
        // Hands-free ends with a tap on the button, so in edge handle mode it has to come out.
        if (edgeHandle && wanted && attached) {
            slideButtonOut()
            letButtonOut()
        }
        button.handsFree = true
        button.setState(RecordingButton.State.RECORDING)

        if (!chromeShown) return

        lock.locked = true
        lock.progress = 0f
        bar.handsFree = true
        barParams.flags = barParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        updateWindow(bar, barParams)
    }

    /** Off in the settings: hold, speak, let go, as before; no slides and no strip. */
    var gestures: Boolean
        get() = button.gestures
        set(value) {
            button.gestures = value
        }

    fun setProcessing() {
        active = true
        hideChrome()
        setExpanded(true)
        keepScreenOn(true)
        button.setState(
            RecordingButton.State.PROCESSING,
        )
    }

    /**
     * The button's window holds the screen awake while recording or recognizing, so a long
     * dictation is not cut off by the screen timeout. The pill does the same for the volume key.
     */
    private fun keepScreenOn(on: Boolean) {
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val flags = if (on) params.flags or flag else params.flags and flag.inv()
        if (flags == params.flags) return
        params.flags = flags
        if (attached) updateWindow(button, params)
    }

    /** Strip and lock next to the button; skipped while the button is hidden. */
    private fun showChrome() {
        if (!canShowStrip) return
        hideChrome()
        dropChromeWindows()
        homeX = params.x
        homeY = params.y

        val bounds = getScreenBounds()
        val size = buttonSize
        val centerX = homeX + size / 2
        val centerY = homeY + size / 2
        val left = centerX < bounds.width / 2
        // The middle of the screen is where "cancel" lies.
        button.inwardSign = if (left) 1f else -1f

        val barHeight = barParams.height
        // The hint starts right at the circle, over the transparent rim of the button's window,
        // and runs toward the middle of the screen.
        val reach = size / 2 - activeInset + (4 * density).roundToInt()
        val room = if (left) bounds.width - barMargin - (centerX + reach) else centerX - reach - barMargin
        val barWidth = minOf(room, (BAR_MAX_DP * density * scale.coerceAtLeast(1f)).roundToInt())
        if (barWidth >= (BAR_MIN_DP * density).roundToInt()) {
            bar.side = if (left) 1 else -1
            barBase = barWidth
            barParams.width = barWidth
            barHomeX = if (left) centerX + reach else centerX - reach - barWidth
            barParams.x = barHomeX
            barParams.y = centerY - barHeight / 2
            barParams.flags = barParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            bar.restart()
            bar.alpha = 0f
            addWindow(bar, barParams)
            button.barSide = if (left) 1 else -1
        }

        // The lock goes above the button, or below it when the button sits near the top.
        val gap = ((RecordingButton.ACTIVE_RADIUS_DP * scale + 8) * density).roundToInt()
        val above = centerY - gap - lockParams.height
        val below = lockParams.height + gap + centerY <= bounds.height
        val down = above < statusBarHeight() && below
        lock.locked = false
        lock.progress = 0f
        lock.pointsDown = down
        button.lockSign = if (down) 1f else -1f
        lockParams.x = centerX - lockParams.width / 2
        lockParams.y = if (down) centerY + gap else above.coerceAtLeast(0)
        lock.alpha = 0f
        addWindow(lock, lockParams)
        chromeShown = true
        // Placed but unseen for a moment: the slides work at once, the picture comes in softly.
        chromeRevealed = false
        button.postDelayed(revealChrome, HINT_DELAY_MS)
    }

    private fun revealChromeNow() {
        button.removeCallbacks(revealChrome)
        revealChrome.run()
    }

    private fun dropChromeWindows() {
        button.removeCallbacks(revealChrome)
        bar.animate().cancel()
        lock.animate().cancel()
        removeWindow(bar)
        removeWindow(lock)
    }

    /** Overlays are kept out of the status bar, so the lock must fit below it. */
    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else (24 * density).roundToInt()
    }

    private fun hideChrome() {
        if (!chromeShown) return
        chromeShown = false
        button.removeCallbacks(revealChrome)
        bar.handsFree = false
        moveButtonTo(homeX, homeY)
        if (!chromeRevealed) {
            dropChromeWindows()
            return
        }
        chromeRevealed = false
        // No taps on a hint that is fading away.
        barParams.flags = barParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        updateWindow(bar, barParams)
        for (view in listOf(bar, lock)) {
            view
                .animate()
                .alpha(0f)
                .setDuration(HINT_FADE_OUT_MS)
                .withEndAction { if (!chromeShown) removeWindow(view) }
                .start()
        }
    }

    /**
     * Follows a slide that started on the button: toward "cancel" the button goes along with
     * the finger and the strip shortens in front of it; toward the lock it rises a little and
     * the arrow climbs. (0, 0) puts everything back.
     */
    private fun followSwipe(
        inward: Float,
        up: Float,
    ) {
        if (!chromeShown) return
        // A slide that began before the hints came in brings them in now.
        if (inward > 0f || up > 0f) revealChromeNow()
        val shiftX = (inward * button.inwardSign).roundToInt()
        val rise = (minOf(up, 24 * density) * -button.lockSign).roundToInt()
        lock.progress = up / (80 * density * RecordingButton.gestureScale(scale))
        bar.cancelProgress = inward / (110 * density * RecordingButton.gestureScale(scale))
        button.barSide = if (rise != 0 || bar.parent == null) 0 else bar.side
        moveButtonTo(homeX + shiftX, homeY - rise)
        if (bar.parent != null) {
            if (bar.side < 0) {
                // The far end stays put; the button eats the strip as it slides toward "cancel".
                barParams.width = (barBase + shiftX).coerceAtLeast(1)
            } else {
                barParams.x = barHomeX + shiftX
                barParams.width = (barBase - shiftX).coerceAtLeast(1)
            }
            updateWindow(bar, barParams)
        }
    }

    private fun moveButtonTo(
        x: Int,
        y: Int,
    ) {
        if (!attached || (params.x == x && params.y == y)) return
        params.x = x
        params.y = y
        targetX = x
        targetY = y
        updateWindow(button, params)
    }

    private fun addWindow(
        view: android.view.View,
        layout: WindowManager.LayoutParams,
    ) {
        try {
            windowManager.addView(view, layout)
        } catch (_: Exception) {
            // No overlay right now: recording works without the hints.
        }
    }

    private fun updateWindow(
        view: android.view.View,
        layout: WindowManager.LayoutParams,
    ) {
        if (view.parent == null) return
        try {
            windowManager.updateViewLayout(view, layout)
        } catch (_: Exception) {
            // The view may be on its way out.
        }
    }

    private fun removeWindow(view: android.view.View) {
        if (view.parent == null) return
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
            // Already gone.
        }
    }

    fun remove() {
        cancelPendingFrameUpdate()
        hideChrome()
        dropChromeWindows()
        removeWindow(handle)

        if (!attached) {
            return
        }

        try {
            windowManager.removeView(button)
        } catch (_: Exception) {
            // View may already have been removed.
        }

        attached = false
    }

    private fun restorePosition() {
        laidOutFor = getScreenBounds()
        val preferences =
            service.getSharedPreferences(
                POSITION_PREFERENCES,
                Context.MODE_PRIVATE,
            )

        val bounds =
            getScreenBounds()

        val maxX =
            (bounds.width - buttonSize - edgeMargin)
                .coerceAtLeast(edgeMargin)

        val maxY =
            (bounds.height - buttonSize - edgeMargin)
                .coerceAtLeast(edgeMargin)

        // Positions saved for the old big window: the small one keeps the circle where it was.
        val moved =
            if (preferences.getInt(GEOMETRY_KEY, 1) < 2 && preferences.contains(POSITION_X_KEY)) {
                ((OLD_BUTTON_SIZE_DP - IDLE_SIZE_DP) / 2f * density * scale).roundToInt()
            } else {
                0
            }

        params.x =
            (
                preferences
                    .getInt(
                        POSITION_X_KEY,
                        maxX,
                    ) + moved
            ).coerceIn(
                edgeMargin,
                maxX,
            )

        // A new button starts just below the middle, where the thumb is.
        params.y =
            (
                preferences
                    .getInt(
                        POSITION_Y_KEY,
                        (bounds.height * 0.52f).roundToInt(),
                    ) + moved
            ).coerceIn(
                edgeMargin,
                maxY,
            )

        if (preferences.getInt(GEOMETRY_KEY, 1) < 2) {
            val edit = preferences.edit().putInt(GEOMETRY_KEY, 2)
            if (moved != 0) edit.putInt(POSITION_X_KEY, params.x).putInt(POSITION_Y_KEY, params.y)
            edit.apply()
        }
    }

    private fun syncTargets() {
        targetX = params.x
        targetY = params.y
    }

    /** Edge handle mode: who is on screen right now, the strip or the button next to it. */
    private fun applyHandleMode() {
        if (!edgeHandle || !attached) return
        if (!wanted) {
            hideChrome()
            buttonOut = false
            button.hideAnimated()
            removeWindow(handle)
            return
        }
        if (handle.parent == null) {
            layoutHandle()
            addWindow(handle, handleParams)
        }
        if (previewing) {
            slideButtonOut()
        } else if (!active && !dragging && buttonOut) {
            buttonOut = false
            val edgeX = if (handleRight) (getScreenBounds().width - buttonSize).coerceAtLeast(0) else 0
            if (params.x == edgeX) {
                // The drop is drawn back into the edge, and only then gone.
                stickButton(handleRight)
                button.gooTo(1f)
                button.reachTo(button.hiddenReach) { if (!buttonOut) button.hideNow() }
            } else {
                // Somewhere on the screen (the switch in the settings): no edge next to it to go into.
                button.hideAnimated()
            }
        }
        handle.resting = !buttonOut
    }

    /**
     * The button comes out of the edge as a drop, level with the strip. With [peekAt] (where the
     * finger touched the strip) only a little of it shows, under the finger, until the touch
     * turns into a recording or a pull.
     */
    private fun slideButtonOut(peekAt: Float? = null) {
        if (buttonOut || !attached) return
        placeButtonAtHandle()
        if (peekAt != null) {
            val size = buttonSize
            params.y = (peekAt - size / 2f).roundToInt().coerceIn(0, (getScreenBounds().height - size).coerceAtLeast(0))
            syncTargets()
        }
        updateWindow(button, params)
        buttonOut = true
        handle.resting = false
        stickButton(handleRight)
        if (button.visibility != android.view.View.VISIBLE) {
            button.setReach(button.hiddenReach)
            button.gooTo(1f, instant = true)
        }
        button.showAnimated()
        if (peekAt != null) {
            button.gooTo(1f)
            button.reachTo(PEEK_DP * density)
        } else {
            letButtonOut()
        }
    }

    /** All the way out, and the neck lets go. */
    private fun letButtonOut() {
        if (!edgeHandle || !buttonOut || button.stickSide == 0) return
        button.reachTo(null)
        button.gooTo(0f)
    }

    private fun placeButtonAtHandle() {
        val bounds = getScreenBounds()
        val size = buttonSize
        params.x = if (handleRight) bounds.width - size else 0
        params.y =
            (handleParams.y + handleParams.height / 2 - size / 2)
                .coerceIn(0, (bounds.height - size).coerceAtLeast(0))
        syncTargets()
    }

    private fun layoutHandle() {
        val bounds = getScreenBounds()
        handle.onRight = handleRight
        handleParams.x = if (handleRight) bounds.width - handleParams.width else 0
        handleParams.y = clampHandleY((bounds.height * handleY).roundToInt() - handleParams.height / 2, bounds)
    }

    private fun clampHandleY(
        y: Int,
        bounds: ScreenBounds,
    ): Int {
        val top = statusBarHeight()
        return y.coerceIn(top, (bounds.height - handleParams.height - top).coerceAtLeast(top))
    }

    private fun loadHandlePosition() {
        val preferences = service.getSharedPreferences(POSITION_PREFERENCES, Context.MODE_PRIVATE)
        handleRight = preferences.getBoolean(HANDLE_RIGHT_KEY, true)
        handleY = preferences.getFloat(HANDLE_Y_KEY, HANDLE_DEFAULT_Y).coerceIn(0f, 1f)
    }

    /** A finger took the button (or its strip) to move it. In the edge it starts out caught. */
    private fun beginDrag() {
        cancelPendingFrameUpdate()
        dragging = true
        val bounds = getScreenBounds()
        val start = button.touchX
        catchArmed = minOf(start, bounds.width - start) >= CATCH_DP * density
        followFrameAt = 0L
        if (edgeHandle) {
            slideButtonOut()
            catchButton(handleRight, bounds)
        } else {
            caught = false
            dragX = params.x.toFloat()
            dragY = params.y.toFloat()
            syncTargets()
        }
    }

    /** The edge takes hold: the window is pinned to it and the circle hangs from it as a drop. */
    private fun catchButton(
        right: Boolean,
        bounds: ScreenBounds,
    ) {
        // Where the circle is now, so that it does not jump when its window changes.
        val centre = params.x + buttonSize / 2f
        val from = if (right) bounds.width - centre else centre
        caught = true
        caughtRight = right
        setStretched(true)
        val size = buttonSize
        params.x = if (right) (bounds.width - size).coerceAtLeast(0) else 0
        syncTargets()
        updateWindow(button, params)
        dragX = params.x.toFloat()
        dragY = params.y.toFloat()
        val already = button.stickSide == (if (right) 1 else -1)
        stickButton(right)
        if (!already) button.setReach(from)
        button.gooTo(1f)
    }

    private fun moveBy(
        dx: Float,
        dy: Float,
    ) {
        if (!attached) {
            return
        }

        if (expanded) movedWhileExpanded = true

        val bounds = getScreenBounds()
        val fingerX = button.touchX
        val fingerY = button.touchY

        if (caught) {
            val size = buttonSize
            val maxY = (bounds.height - size).coerceAtLeast(0).toFloat()
            val away = if (caughtRight) bounds.width - fingerX else fingerX
            if (away > RELEASE_DP * density) {
                // The neck snaps: from here on it is a free button that catches up with the finger.
                caught = false
                onTick?.invoke()
                // Where the circle is on the screen right now: its window is put around that
                // very spot, so nothing jumps, and then trails to the finger like any drag.
                val circleX = if (caughtRight) bounds.width - button.reach else button.reach
                val circleY = params.y + size / 2f
                freeButton()
                setStretched(false)
                val free = buttonSize
                val maxFreeX = (bounds.width - free).coerceAtLeast(0)
                val maxFreeY = (bounds.height - free).coerceAtLeast(0)
                params.x = (circleX - free / 2f).roundToInt().coerceIn(0, maxFreeX)
                params.y = (circleY - free / 2f).roundToInt().coerceIn(0, maxFreeY)
                updateWindow(button, params)
                dragX = (fingerX - free / 2f).coerceIn(0f, maxFreeX.toFloat())
                dragY = (fingerY - free / 2f).coerceIn(0f, maxFreeY.toFloat())
                targetX = dragX.roundToInt()
                targetY = dragY.roundToInt()
                scheduleFrameUpdate()
                return
            }
            // Held by the edge: the circle goes where the finger is, the neck stretches after it.
            dragX = if (caughtRight) (bounds.width - size).coerceAtLeast(0).toFloat() else 0f
            dragY = (fingerY - size / 2f).coerceIn(0f, maxY)
            button.reachTo(away.coerceAtLeast(4 * density))
        } else {
            val size = buttonSize
            dragX = (dragX + dx).coerceIn(0f, (bounds.width - size).coerceAtLeast(0).toFloat())
            dragY = (dragY + dy).coerceIn(0f, (bounds.height - size).coerceAtLeast(0).toFloat())
            val right = fingerX > bounds.width / 2f
            val away = if (right) bounds.width - fingerX else fingerX
            if (!catchArmed) {
                catchArmed = away >= (CATCH_DP + 8) * density
            } else if (away < CATCH_DP * density) {
                onTick?.invoke()
                // Put the window where it was headed first, so the circle is caught from there.
                targetX = dragX.roundToInt()
                targetY = dragY.roundToInt()
                applyPendingPosition()
                catchButton(right, bounds)
                button.reachTo(away.coerceAtLeast(4 * density))
                return
            }
        }

        targetX = dragX.roundToInt()
        targetY = dragY.roundToInt()
        scheduleFrameUpdate()
    }

    private fun scheduleFrameUpdate() {
        if (frameUpdateScheduled || !attached) {
            return
        }

        frameUpdateScheduled = true

        val update =
            Runnable {
                pendingFrameUpdate = null
                frameUpdateScheduled = false

                if (!attached) {
                    return@Runnable
                }

                if (
                    params.x == targetX &&
                    params.y == targetY
                ) {
                    followFrameAt = 0L
                    return@Runnable
                }

                // The button closes a share of the gap each frame: it trails the finger a little.
                val now = android.os.SystemClock.uptimeMillis()
                val dt = if (followFrameAt == 0L) 8f else (now - followFrameAt).toFloat().coerceIn(4f, 32f)
                followFrameAt = now
                val share = 1f - kotlin.math.exp(-dt / FOLLOW_MS)
                val gapX = targetX - params.x
                val gapY = targetY - params.y
                params.x = if (kotlin.math.abs(gapX) <= 2) targetX else params.x + (gapX * share).roundToInt().let { if (it == 0) gapX.coerceIn(-1, 1) else it }
                params.y = if (kotlin.math.abs(gapY) <= 2) targetY else params.y + (gapY * share).roundToInt().let { if (it == 0) gapY.coerceIn(-1, 1) else it }

                try {
                    windowManager.updateViewLayout(
                        button,
                        params,
                    )
                } catch (_: Exception) {
                    attached = false
                }

                if (params.x != targetX || params.y != targetY) scheduleFrameUpdate()
            }

        pendingFrameUpdate = update
        button.postOnAnimation(update)
    }

    /** The finger let go: a caught button stays in its edge, a free one stays where it is. */
    private fun savePosition() {
        dragging = false
        // The button may still be catching up with the finger: put it where it was headed.
        applyPendingPosition()

        val bounds = getScreenBounds()
        val preferences = service.getSharedPreferences(POSITION_PREFERENCES, Context.MODE_PRIVATE)

        if (caught) {
            caught = false
            val y =
                if (bounds.height > 0) {
                    ((params.y + buttonSize / 2f) / bounds.height).coerceIn(0f, 1f)
                } else {
                    HANDLE_DEFAULT_Y
                }
            setStretched(false)
            // The smaller window goes back against the edge it hangs from.
            params.x = if (caughtRight) (bounds.width - buttonSize).coerceAtLeast(0) else 0
            syncTargets()
            updateWindow(button, params)
            preferences
                .edit()
                .putBoolean(HANDLE_RIGHT_KEY, caughtRight)
                .putFloat(HANDLE_Y_KEY, y)
                .apply()
            if (edgeHandle) {
                handleRight = caughtRight
                handleY = y
                layoutHandle()
                updateWindow(handle, handleParams)
                // Not from inside the touch that is still being delivered.
                button.post { applyHandleMode() }
            } else {
                button.post { tuck() }
            }
            return
        }

        preferences
            .edit()
            .putInt(POSITION_X_KEY, params.x)
            .putInt(POSITION_Y_KEY, params.y)
            .putInt(GEOMETRY_KEY, 2)
            .apply()
        // Pulled out of the edge and left on the screen: from now on it is an ordinary button.
        if (edgeHandle) button.post { untuck() }
    }

    /** The edge caught the button and the finger let go: from now on it lives in the edge. */
    private fun tuck() {
        if (edgeHandle) return
        edgeHandle = true
        onTuckedChange?.invoke(true)
    }

    /** The button was pulled out of the edge and dropped: it stays where it was dropped. */
    private fun untuck() {
        if (!edgeHandle) return
        edgeHandle = false
        onTuckedChange?.invoke(false)
    }

    private fun applyPendingPosition() {
        if (!attached) {
            return
        }

        params.x = targetX
        params.y = targetY

        try {
            windowManager.updateViewLayout(
                button,
                params,
            )
        } catch (_: Exception) {
            attached = false
        }

        frameUpdateScheduled = false
    }

    private fun cancelPendingFrameUpdate() {
        if (!frameUpdateScheduled) {
            return
        }

        pendingFrameUpdate?.let(button::removeCallbacks)
        pendingFrameUpdate = null
        frameUpdateScheduled = false
    }

    private fun getScreenBounds(): ScreenBounds {
        val size = ScreenSize.of(service, windowManager)
        return ScreenBounds(width = size.x, height = size.y)
    }

    /** The screen the button was last laid out on, to keep its edge when the screen changes. */
    private var laidOutFor: ScreenBounds? = null

    /**
     * The screen changed (a foldable opened or closed, rotation): the button keeps its
     * distance from the nearer side edge and its share of the height, and stays on screen.
     */
    fun onScreenChanged() {
        if (!attached) return
        if (edgeHandle) {
            laidOutFor = getScreenBounds()
            layoutHandle()
            updateWindow(handle, handleParams)
            if (buttonOut && !chromeShown) {
                buttonOut = false
                slideButtonOut()
            }
            return
        }
        val bounds = getScreenBounds()
        val before = laidOutFor
        var x = params.x
        var y = params.y
        if (before != null && before != bounds && before.width > 0 && before.height > 0) {
            val onRight = x + buttonSize / 2 > before.width / 2
            if (onRight) x = bounds.width - (before.width - x)
            y = (y.toLong() * bounds.height / before.height).toInt()
        }
        laidOutFor = bounds
        val maxX = (bounds.width - buttonSize - edgeMargin).coerceAtLeast(edgeMargin)
        val maxY = (bounds.height - buttonSize - edgeMargin).coerceAtLeast(edgeMargin)
        x = x.coerceIn(edgeMargin, maxX)
        y = y.coerceIn(edgeMargin, maxY)
        if (x != params.x || y != params.y) {
            params.x = x
            params.y = y
            syncTargets()
            updateWindow(button, params)
            if (chromeShown) {
                hideChrome()
                showChrome()
            }
        }
    }

    private data class ScreenBounds(
        val width: Int,
        val height: Int,
    )
}
