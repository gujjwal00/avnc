/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.os.Build
import android.view.*
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Handler for touch events. It detects various gestures and notifies [dispatcher].
 */
class TouchHandler(private val viewModel: VncViewModel, private val dispatcher: Dispatcher)
    : ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {

    //Extension to easily access touch position
    private fun MotionEvent.point() = PointF(x, y)

    /****************************************************************************************
     * Touch Event receivers
     *
     * Note: On some devices, 'Source' property for Stylus events is set to both
     * [InputDevice.SOURCE_STYLUS] & [InputDevice.SOURCE_MOUSE]. Hence, we should
     * pass the events to [handleStylusEvent] before [handleMouseEvent].
     ****************************************************************************************/

    fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = handleStylusEvent(event) || handleMouseEvent(event) || handleGestureEvent(event)
        handleGestureStartStop(event)
        return handled
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return onHoverEvent(event) || handleStylusEvent(event) || handleMouseEvent(event)
    }

    fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            lastHoverPoint = event.point()
            dispatcher.onMouseMove(lastHoverPoint)
            return true
        }
        return false
    }

    private fun handleGestureStartStop(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> dispatcher.onGestureStart()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> dispatcher.onGestureStop(event.point())
        }
    }

    // Used for back-press interception
    private var lastHoverPoint = PointF()
    fun onMouseBack() {
        dispatcher.onMouseBack(lastHoverPoint)
    }

    /****************************************************************************************
     * Mouse
     ****************************************************************************************/
    private val mousePassthrough = viewModel.pref.input.mousePassthrough

    private fun handleMouseEvent(e: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT < 23 || !mousePassthrough || !e.isFromSource(InputDevice.SOURCE_MOUSE))
            return false

        val p = e.point()

        when (e.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> dispatcher.onMouseButtonDown(convertButton(e.actionButton), p)
            MotionEvent.ACTION_BUTTON_RELEASE -> dispatcher.onMouseButtonUp(convertButton(e.actionButton), p)
            MotionEvent.ACTION_MOVE -> dispatcher.onMouseMove(p)

            MotionEvent.ACTION_SCROLL -> {
                val hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
                val vs = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                dispatcher.onMouseScroll(p, hs, vs)
            }
        }

        // Allow touchpad gestures to be passed on to GestureDetector
        if (e.buttonState == 0 && e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE)
            return false

        return true
    }

    /**
     * Convert from [MotionEvent] button to [PointerButton]
     */
    private fun convertButton(button: Int) = when (button) {
        MotionEvent.BUTTON_PRIMARY -> PointerButton.Left
        MotionEvent.BUTTON_SECONDARY -> PointerButton.Right
        MotionEvent.BUTTON_TERTIARY -> PointerButton.Middle
        else -> PointerButton.None
    }


    /****************************************************************************************
     * Stylus
     ****************************************************************************************/
    private val stylusGestureDetector = GestureDetector(viewModel.app, StylusGestureListener())

    private fun handleStylusEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_STYLUS)) {
            stylusGestureDetector.onTouchEvent(event)
            return true
        }
        return false
    }

    inner class StylusGestureListener : GestureDetector.SimpleOnGestureListener() {
        private var scrolling = false

        override fun onDown(e: MotionEvent?): Boolean {
            scrolling = false
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            dispatcher.onStylusTap(e.point())
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            dispatcher.onStylusDoubleTap(e.point())
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            viewModel.frameViewRef.get()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            dispatcher.onStylusLongPress(e.point())
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // Scrolling with stylus button pressed is currently used for scale gesture
            if (e2.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY == 0) {

                // When scrolling starts, we need to send the first event at initial touch-point.
                // Otherwise, we will loose the small distance (touch-slope) required by onScroll().
                if (!scrolling) {
                    scrolling = true
                    dispatcher.onStylusScroll(e1.point())
                }
                dispatcher.onStylusScroll(e2.point())
            }
            return true
        }
    }


    /****************************************************************************************
     * Finger Gestures
     *
     * In addition to gestures supported by [GestureDetector] & [ScaleGestureDetector],
     * we use [DragDetector] & [MultiFingerTapDetector] for more gestures.
     *
     ****************************************************************************************/
    private val scaleDetector = ScaleGestureDetector(viewModel.app, this).apply { isQuickScaleEnabled = false }
    private val gestureDetector = GestureDetector(viewModel.app, this)
    private val multiFingerTapDetector = MultiFingerTapDetector()
    private val dragDetector = DragDetector()
    private val swipeVsScale = SwipeVsScale()
    private val dragEnabled = viewModel.pref.input.gesture.dragEnabled
    private val swipeSensitivity = viewModel.pref.input.gesture.swipeSensitivity


    private fun handleGestureEvent(event: MotionEvent): Boolean {
        swipeVsScale.onTouchEvent(event)
        dragDetector.onTouchEvent(event)
        multiFingerTapDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (swipeVsScale.shouldScale())
            dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    override fun onDown(e: MotionEvent) = true

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        dispatcher.onTap1(e.point())
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        dispatcher.onDoubleTap(e.point())
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        viewModel.frameViewRef.get()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        if (dragEnabled)
            dragDetector.onLongPress(e)
        else
            dispatcher.onLongPress(e.point())
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vX: Float, vY: Float): Boolean {
        dispatcher.onFling(vX, vY)
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dX: Float, dY: Float): Boolean {
        val startPoint = e1.point()
        val currentPoint = e2.point()
        val normalizedDx = -dX * swipeSensitivity
        val normalizedDy = -dY * swipeSensitivity

        when (e2.pointerCount) {
            1 -> dispatcher.onSwipe1(startPoint, currentPoint, normalizedDx, normalizedDy)
            2 -> if (swipeVsScale.shouldSwipe())
                dispatcher.onSwipe2(startPoint, currentPoint, normalizedDx, normalizedDy)
        }

        multiFingerTapDetector.reset()
        return true
    }


    /**
     * Utility class for detecting drag gesture.
     *
     * Detection:
     *
     *  1. Wait for the long-press from [gestureDetector] before doing anything.
     *  2. Once long-press is detected, start looking at incoming events.
     *  3. If [MotionEvent.ACTION_UP] is received next, send long-press.
     *  4. If scroll event is received from [scrollDetector], start dragging.
     *  5. Reset, if gesture is finished, or another finger goes down.
     *
     * But, if long-press detection is enabled for a [GestureDetector] object, it will not report
     * scroll events after long-press. So, we rely on [gestureDetector] for long-press detection,
     * and use a separate object [scrollDetector] for scroll events.
     *
     * Left mouse button pressed during dragging is automatically released
     * by [Dispatcher.onGestureStop].
     */
    private inner class DragDetector {
        private var longPressDetected = false
        private var isDragging = false
        private var startPoint = PointF()
        private val scrollDetector = GestureDetector(viewModel.app, ScrollListener()).apply {
            setIsLongpressEnabled(false)
        }

        private inner class ScrollListener : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (longPressDetected) {
                    if (!isDragging) {
                        isDragging = true

                        //Send first drag event at startPoint, otherwise drag gesture
                        //will start off by (at-least) touch-slope used by GestureDetector
                        dispatcher.onDrag(startPoint, startPoint, 0f, 0f)
                    }

                    dispatcher.onDrag(startPoint, e2.point(), -dx, -dy)
                }
                return true
            }
        }

        fun onLongPress(e: MotionEvent) {
            longPressDetected = true
            startPoint = e.point()
        }

        fun onTouchEvent(event: MotionEvent) {
            if (!dragEnabled)
                return

            scrollDetector.onTouchEvent(event)

            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP && longPressDetected && !isDragging)
                dispatcher.onLongPress(event.point())

            when (action) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_CANCEL -> {
                    longPressDetected = false
                    isDragging = false
                }
            }
        }
    }

    /**
     * Detects 'tapping' by two or more fingers.
     *
     * Detection:
     *
     *  1. First finger goes down. We start tracking by updating [startEvent]
     *  2. More fingers go down. [fingerCount] is used to track them
     *  3. Fingers start going up
     *  4. Last finger goes up. Timestamps are checked to ensure the gesture
     *     was finished within a  timeout, and if more than 1 finger went down,
     *     appropriate handler is invoked.
     *
     * If fingers are moved after going down, user probably intends to pan/scale,
     * so tap detection is stopped if we receive [onScroll].
     */
    private inner class MultiFingerTapDetector {
        private var startEvent: MotionEvent? = null
        private var fingerCount = 0

        fun onTouchEvent(e: MotionEvent) {
            when (e.actionMasked) {

                MotionEvent.ACTION_DOWN -> startEvent = MotionEvent.obtain(e)
                MotionEvent.ACTION_POINTER_DOWN -> fingerCount = max(fingerCount, e.pointerCount)
                MotionEvent.ACTION_CANCEL -> reset()

                MotionEvent.ACTION_UP -> startEvent?.let { startEvent ->
                    if ((e.eventTime - startEvent.eventTime) <= ViewConfiguration.getDoubleTapTimeout())
                        when (fingerCount) {
                            2 -> dispatcher.onTap2(startEvent.point())
                            // Taps by 3+ fingers are not exposed yet
                        }
                    reset()
                }
            }
        }

        fun reset() {
            startEvent?.recycle()
            startEvent = null
            fingerCount = 0
        }
    }

    /**
     * Swipe vs Scale detector.
     *
     * Many two-finger gestures are detected as both swipe & scale gestures because
     * [GestureDetector] & [ScaleGestureDetector] work independently. This works
     * very well when two-finger swipe pref is set to 'pan' (default value).
     * But when the pref is set to 'remote-scroll', this independent detection
     * becomes an issue. When user tries to scale, it frequently triggers remote
     * scrolling. And when user tries to scroll, it triggers scaling.
     *
     * This class tries to clearly differentiate between these two gestures.
     * It works by tracking the fingers, and calculating the angle between two paths.
     * Then [decide] between two gestures by comparing the angle to some thresholds.
     *
     * This class can mis-detect some gestures because fingers don't always move
     * perfectly, but it does provide huge improvement over existing situation.
     */
    private inner class SwipeVsScale {
        private val enabled = viewModel.pref.input.gesture.swipe2 == "remote-scroll"
        private var detecting = false
        private var scaleDetected = false
        private var swipeDetected = false

        private var f1Id = 0 // Finger 1
        private var f2Id = 0 // Finger 2
        private val f1Start = PointF()
        private val f2Start = PointF()
        private val f1Current = PointF()
        private val f2Current = PointF()


        fun onTouchEvent(e: MotionEvent) {
            if (!enabled)
                return

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP -> {
                    detecting = false
                    scaleDetected = false
                    swipeDetected = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    detecting = e.pointerCount == 2
                    if (detecting) {
                        f1Id = e.getPointerId(0)
                        f2Id = e.getPointerId(1)
                        f1Start.set(e.getX(f1Id), e.getY(f1Id))
                        f2Start.set(e.getX(f2Id), e.getY(f2Id))
                        f1Current.set(f1Start)
                        f2Current.set(f2Start)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (detecting) {
                        f1Current.set(e.getX(f1Id), e.getY(f1Id))
                        f2Current.set(e.getX(f2Id), e.getY(f2Id))
                    }
                }
            }
        }

        fun shouldScale(): Boolean {
            decide()
            return !enabled || (detecting && scaleDetected)
        }

        fun shouldSwipe(): Boolean {
            decide()
            return !enabled || (detecting && swipeDetected)
        }

        /**
         * Decides if gesture can be considered a swipe/scale
         */
        private fun decide() {
            if (!detecting)
                return

            val t1 = theta(f1Start, f1Current)
            val t2 = theta(f2Start, f2Current)
            val diff = abs(t1 - t2)

            scaleDetected = diff > 45
            swipeDetected = diff < 30
        }

        /**
         * Returns the angle made by line [p1]->[p2] with the positive x-axis.
         * Returned angle will be in range [0, 360]
         */
        private fun theta(p1: PointF, p2: PointF): Double {
            val theta = atan2(p2.y - p1.y, p2.x - p1.x)
            val degree = (theta / PI) * 180
            return (degree + 360) % 360 // Map [-180, 180] to [0, 360]
        }
    }
}
