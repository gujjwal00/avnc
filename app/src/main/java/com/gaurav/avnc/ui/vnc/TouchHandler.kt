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
            dispatcher.onMouseMove(event.point())
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
        override fun onDown(e: MotionEvent?) = true

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
            if (e2.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY == 0)
                dispatcher.onStylusScroll(e2.point())
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
    private val dragEnabled = viewModel.pref.input.gesture.dragEnabled


    private fun handleGestureEvent(event: MotionEvent): Boolean {
        dragDetector.onTouchEvent(event)
        multiFingerTapDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
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

        when (e2.pointerCount) {
            1 -> dispatcher.onSwipe1(startPoint, currentPoint, -dX, -dY)
            2 -> dispatcher.onSwipe2(startPoint, currentPoint, -dX, -dY)
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
}
