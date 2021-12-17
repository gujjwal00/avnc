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
 *
 * TODO: Reduce [PointF] garbage
 */
class TouchHandler(private val viewModel: VncViewModel, private val dispatcher: Dispatcher)
    : ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {

    private val scaleDetector = ScaleGestureDetector(viewModel.getApplication(), this)
    private val gestureDetector = GestureDetector(viewModel.getApplication(), this)
    private val multiFingerTapDetector = MultiFingerTapDetector()
    private val frameScroller = FrameScroller(viewModel) //Should it be in Dispatcher?
    private val dragDetector = DragDetector()
    private val dragEnabled = viewModel.pref.input.gesture.dragEnabled

    init {
        scaleDetector.isQuickScaleEnabled = false
    }

    //Extension to easily access touch position
    private fun MotionEvent.point() = PointF(x, y)

    /****************************************************************************************
     * Touch Event receivers
     ****************************************************************************************/

    fun onTouchEvent(event: MotionEvent): Boolean {
        // For events from stylus, Android sets their 'source' to both 'STYLUS' & 'MOUSE'.
        // So we check if it is a stylus event _before_ checking for mouse event.
        if (handleStylusEvent(event) || handleMouseEvent(event))
            return true

        if (dragEnabled)
            dragDetector.onTouchEvent(event)

        multiFingerTapDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Event could be from either a stylus (hovering) or a mouse
        return handleStylusEvent(event) || handleMouseEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        frameScroller.stop()
        return true
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
            MotionEvent.ACTION_BUTTON_PRESS ->
                dispatcher.onMouseButtonDown(convertButton(e.actionButton), p)

            MotionEvent.ACTION_BUTTON_RELEASE ->
                dispatcher.onMouseButtonUp(convertButton(e.actionButton), p)

            MotionEvent.ACTION_SCROLL -> {
                val hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
                val vs = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                dispatcher.onMouseScroll(p, hs, vs)
            }

            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_HOVER_MOVE -> dispatcher.onMouseMove(p)
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
    private var lastStylusScrollPoint: PointF? = null

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
            dispatcher.onStylusLongPress(e.point())
        }

        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // Scrolling with stylus button pressed is currently used for scale gesture
            if (e2.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY == 0) {
                lastStylusScrollPoint = e2.point()
                dispatcher.onStylusScroll(lastStylusScrollPoint!!)
            }
            return true
        }
    }

    private fun handleStylusEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_STYLUS))
            return false

        stylusGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                // Finish any scroll gesture
                lastStylusScrollPoint?.let {
                    dispatcher.onStylusScrollEnd(it)
                    lastStylusScrollPoint = null
                }

            MotionEvent.ACTION_HOVER_MOVE -> dispatcher.onMouseMove(event.point())
        }

        return true
    }


    /****************************************************************************************
     * Gestures
     ****************************************************************************************/

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

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
        frameScroller.fling(vX, vY)
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

    /***************************************************************************************
     * Small utility class for handling drag gesture (Long Press followed by Swipe/Move)
     **************************************************************************************/

    private inner class DragDetector {
        private var longPressDetected = false
        private var isDragging = false
        private var startPoint = PointF()
        private var lastPoint = PointF()

        fun onLongPress(e: MotionEvent): Boolean {
            longPressDetected = true
            startPoint = e.point()
            lastPoint = startPoint
            return true
        }

        fun onTouchEvent(event: MotionEvent) {
            if (!longPressDetected)
                return

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    isDragging = true

                    val cp = event.point()
                    dispatcher.onDrag(startPoint, cp, cp.x - lastPoint.x, cp.y - lastPoint.y)
                    lastPoint = cp
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging)
                        dispatcher.onDragEnd(event.point())
                    else
                        dispatcher.onLongPress(event.point())

                    longPressDetected = false
                    isDragging = false
                }

                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging)
                        dispatcher.onDragEnd(event.point())

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
     * If fingers are moved after going down, user probably intends to pan/scale.
     * So tap detection is stopped if we receive [onScroll].
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
