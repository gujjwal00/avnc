/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.graphics.PointF
import android.os.Build
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.Toast
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Handler for touch events. It detects various gestures and notifies [dispatcher].
 */
class TouchHandler(
        private val frameView: FrameView,
        private val dispatcher: Dispatcher,
        private val viewModel: VncViewModel, // Added VncViewModel
        private val pref: AppPreferences
) : ScaleGestureDetector.OnScaleGestureListener { // Removed PanningTouchHandlerCallback

    private companion object {
        private const val TAG = "TouchHandler"
    }

    private enum class GestureMode {
        NONE,
        PAN,
        SCALE // Added for completeness, might be set in onScale
    }
    private var mGestureMode: GestureMode = GestureMode.NONE

    //Extension to easily access touch position
    private fun MotionEvent.point() = PointF(x, y)

    private val touchPanningInputDevice: TouchPanningInputDevice

    // Removed: lastX, lastY, isPanningCamera, cameraPanSensitivity
    // These were related to direct 3D pan handling in TouchHandler, which is now moved to Dispatcher.

    private val cameraZoomSensitivity = 2f // Adjust as needed. Larger means faster zoom.

    init {
        touchPanningInputDevice = TouchPanningInputDevice()
    }

    /****************************************************************************************
     * Touch Event receivers
     *
     * Note: On some devices, 'Source' property for Stylus events is set to both
     * [InputDevice.SOURCE_STYLUS] & [InputDevice.SOURCE_MOUSE]. Hence, we should
     * pass the events to [handleStylusEvent] before [handleMouseEvent].
     ****************************************************************************************/

    fun onTouchEvent(event: MotionEvent): Boolean {
        // Removed direct 3D camera panning logic for ACTION_DOWN/ACTION_MOVE/ACTION_UP.
        // This will now be handled by GestureDetectorEx -> Dispatcher -> viewModel.panCamera()
        // when the swipe action is configured to "pan".

        // Existing gesture detection logic:
        val handledByOthers = handleStylusEvent(event) || handleMouseEvent(event) || handleGestureEvent(event)

        handleGestureStartStop(event)
        return handledByOthers
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
    private val mousePassthrough = pref.input.mousePassthrough

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
                // Directly dispatch, TouchPanningInputDevice no longer handles discrete scroll
                dispatcher.onMouseScroll(p, hs, vs)
            }
        }

        // Allow touchpad gestures to be passed on to GestureDetector
        return !(e.buttonState == 0 && e.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE)
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
    private val stylusGestureDetector = GestureDetector(frameView.context, StylusGestureListener())

    private fun handleStylusEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_STYLUS) &&
            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            stylusGestureDetector.onTouchEvent(event)
            return true
        }
        return false
    }

    inner class StylusGestureListener : SimpleOnGestureListener() {
        private var scrolling = false

        override fun onDown(e: MotionEvent): Boolean {
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
            frameView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            dispatcher.onStylusLongPress(e.point())
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            // Scrolling with stylus button pressed is currently used for scale gesture
            if (e1 != null && e2.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY == 0) {

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
     * Finger Gestures (and everything else beside mouse & stylus)
     ****************************************************************************************/
    private val scaleDetector = ScaleGestureDetector(frameView.context, this).apply { isQuickScaleEnabled = false }
    private val gestureDetector = GestureDetectorEx(frameView.context, FingerGestureListener(), pref.input.gesture.longPressDetectionEnabled)
    private val swipeVsScale = SwipeVsScale()
    private val longPressSwipeEnabled = pref.input.gesture.longPressSwipeEnabled
    private val swipeSensitivity = pref.input.gesture.swipeSensitivity


    private fun handleGestureEvent(event: MotionEvent): Boolean {
        swipeVsScale.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        mGestureMode = GestureMode.SCALE
        Log.d(TAG, "Scale gesture began, mGestureMode set to SCALE")
        return true // Always start scale, decision in onScale
    }
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        if (mGestureMode == GestureMode.SCALE) {
            mGestureMode = GestureMode.NONE
            Log.d(TAG, "Scale gesture ended, mGestureMode reset to NONE")
        }
    }
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // detector.scaleFactor > 1 means pinch-out (zoom in for camera)
        // detector.scaleFactor < 1 means pinch-in (zoom out for camera)

        // The change in Z position or distance along view vector.
        // (1 - detector.scaleFactor) makes pinch-out (factor > 1) result in negative deltaZ (move closer/zoom in)
        // and pinch-in (factor < 1) result in positive deltaZ (move further/zoom out).
        val deltaZ = (1 - detector.scaleFactor) * cameraZoomSensitivity

        viewModel.zoomCamera(deltaZ) // Call new method in VncViewModel

        return true // Event handled
    }

    private inner class FingerGestureListener : GestureListenerEx {

        override fun onSingleTapConfirmed(e: MotionEvent) = dispatcher.onTap1(e.point())
        override fun onDoubleTapConfirmed(e: MotionEvent) = dispatcher.onDoubleTap(e.point())

        override fun onMultiFingerTap(e: MotionEvent, fingerCount: Int) {
            when (fingerCount) {
                2 -> dispatcher.onTap2(e.point())
                3 -> {
                    Toast.makeText(frameView.context, "3-finger tap in TouchHandler", Toast.LENGTH_SHORT).show() // DEBUG
                    dispatcher.onTap3(e.point())
                }
            }
        }

        override fun onLongPress(e: MotionEvent) {
            frameView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            // If long-press-swipe is disabled, we can dispatch long-press immediately
            if (!longPressSwipeEnabled) dispatcher.onLongPress(e.point())
        }

        override fun onLongPressConfirmed(e: MotionEvent) {
            if (longPressSwipeEnabled) dispatcher.onLongPress(e.point())
        }


        override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            // dx and dy from GestureDetector are total scroll distances, not deltas.
            // However, TouchPanningInputDevice expects deltas (distanceX, distanceY from SimpleOnGestureListener.onScroll).
            // The parameters dx, dy here are actually distanceX, distanceY for this specific call.
            // distanceX: The distance along the X axis that has been scrolled since the last call to onScroll. This is NOT the distance between e1 and e2.
            // distanceY: The distance along the Y axis that has been scrolled since the last call to onScroll. This is NOT the distance between e1 and e2.
            // So, dx and dy in this context are appropriate for TouchPanningInputDevice's distanceX, distanceY.

            // TouchPanningInputDevice.onScroll call removed.
            // Directly proceed with existing swipe logic.
            val startPoint = e1.point()
            val currentPoint = e2.point()
            // dx/dy from this listener are deltas, but dispatcher.onSwipe expects total delta from start.
            // The original code used dx/dy as normalizedDx/normalizedDy which are deltas for the swipe action.
            // This part might need careful review if swipe actions are mixed with panning.
            // For now, assume dx, dy are instantaneous deltas for swipe actions.
            val normalizedDx = dx * swipeSensitivity
            val normalizedDy = dy * swipeSensitivity

            when (e2.pointerCount) {
                1 -> dispatcher.onSwipe1(startPoint, currentPoint, normalizedDx, normalizedDy)
                2 -> if (swipeVsScale.shouldSwipe())
                    dispatcher.onSwipe2(startPoint, currentPoint, normalizedDx, normalizedDy)
                3 -> dispatcher.onSwipe3(startPoint, currentPoint, normalizedDx, normalizedDy)
            }
        }

        override fun onScrollAfterLongPress(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            dispatcher.onLongPressSwipe(e1.point(), e2.point(), dx, dy)
        }

        override fun onScrollAfterDoubleTap(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            dispatcher.onDoubleTapSwipe(e1.point(), e2.point(), dx, dy)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            dispatcher.onFling(velocityX, velocityY)
        }
    }

    /**
     * Stock [GestureDetector] only detects the most common gestures. But we need to
     * detect some more gestures to provide maximum flexibility to the user.
     *
     * [GestureDetectorEx] is used to for this purpose. It internally uses stock
     * [GestureDetector], and some custom event processing to detect more gestures.
     */
    interface GestureListenerEx { // <-- Moved here
        fun onSingleTapConfirmed(e: MotionEvent)
        fun onDoubleTapConfirmed(e: MotionEvent)
        fun onMultiFingerTap(e: MotionEvent, fingerCount: Int)

        fun onLongPress(e: MotionEvent)
        fun onLongPressConfirmed(e: MotionEvent)

        fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float)
        fun onScrollAfterLongPress(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float)
        fun onScrollAfterDoubleTap(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float)

        fun onFling(velocityX: Float, velocityY: Float)
    }

    private inner class GestureDetectorEx(context: Context, val listener: GestureListenerEx, val enableLongPress: Boolean) {

        /**
         * Stock [GestureDetector] has two unwanted behaviours:
         * - If long-press or double-tap is detected, scroll events will not be reported anymore.
         * - If you don't lift the finger after double-tap, a long-press will be triggered.
         *
         * Fortunately, [GestureDetector] lets us disable long-press detection, which allows us
         * to use a combination of multiple [GestureDetector]s to overcome the restrictions:
         *
         * -                                 +------------------+
         * -                              +->| [innerDetector1] |
         * -                              |  +------------------+
         * -                              |   (tap, long-press)
         * -   +----------------+  event  |
         * -   | [onTouchEvent] |---------+
         * -   +----------------+         |
         * -                              |
         * -                              |  +------------------+  double-tap event   +------------------+
         * -                              +->| [innerDetector2] |-------------------->| [innerDetector3] |
         * -                                 +------------------+                     +------------------+
         * -                                    (double-tap)                           (double-tap-swipe)
         *
         */
        private val innerDetector1 = GestureDetector(context, InnerListener1())
        private val innerDetector2 = GestureDetector(context, InnerListener2()).apply { setIsLongpressEnabled(false) }
        private val innerDetector3 = GestureDetector(context, InnerListener3()).apply { setIsLongpressEnabled(false) }

        private var longPressDetected = false
        private var doubleTapDetected = false
        private var scrolling = false
        private var maxFingerDown = 0
        private var currentDownEvent: MotionEvent? = null
        private var cumulatedX = 0f
        private var cumulatedY = 0f
        private val multiTapSlopSquare = 30 * 30


        private inner class InnerListener1 : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener.onSingleTapConfirmed(e)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!enableLongPress)
                    return

                if (doubleTapDetected)
                    return // Ignore long-press triggered during double-tap-swipe

                longPressDetected = true
                listener.onLongPress(e)
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                listener.onFling(velocityX, velocityY)
                return true
            }
        }

        private inner class InnerListener2 : SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                doubleTapDetected = true
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent) = innerDetector3.onTouchEvent(e)

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float) = handleScroll(e1, e2, dx, dy)
        }

        private inner class InnerListener3 : SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float) = handleScroll(e1, e2, dx, dy)
        }

        private fun handleScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            e1 ?: return false
            if (!scrolling) {
                scrolling = true
                // Send first scroll event on initial touch-down point, because GestureDetector
                // requires certain amount of finger movement before scroll is triggered, and
                // we don't want to 'loose' that small movement.
                callOnScroll(e1, e1, 0f, 0f)
            }

            callOnScroll(e1, e2, -dx, -dy)
            cumulatedX += dx
            cumulatedY += dy
            return true
        }

        private fun callOnScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float) {
            if (doubleTapDetected)
                listener.onScrollAfterDoubleTap(e1, e2, dx, dy)
            else if (longPressDetected)
                listener.onScrollAfterLongPress(e1, e2, dx, dy)
            else
                listener.onScroll(e1, e2, dx, dy)
        }

        /**
         * Event receiver
         */
        fun onTouchEvent(e: MotionEvent): Boolean {
            innerDetector1.onTouchEvent(e)
            innerDetector2.onTouchEvent(e)

            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    maxFingerDown = 1
                    currentDownEvent = MotionEvent.obtain(e)
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    maxFingerDown = max(maxFingerDown, e.pointerCount)
                }

                MotionEvent.ACTION_UP -> {
                    currentDownEvent?.let { downEvent ->
                        if (longPressDetected && !doubleTapDetected && !scrolling && maxFingerDown <= 1)
                            listener.onLongPressConfirmed(downEvent)

                        if (doubleTapDetected && !longPressDetected && !scrolling && maxFingerDown <= 1)
                            listener.onDoubleTapConfirmed(downEvent)

                        val gestureDuration = (e.eventTime - downEvent.eventTime)
                        val isWithinSlop = (cumulatedX * cumulatedX + cumulatedY * cumulatedY) < multiTapSlopSquare
                        if (maxFingerDown > 1 && (!scrolling || isWithinSlop) && gestureDuration < ViewConfiguration.getDoubleTapTimeout())
                            listener.onMultiFingerTap(downEvent, maxFingerDown)
                    }

                    reset()
                }

                MotionEvent.ACTION_CANCEL -> reset()
            }

            return true
        }

        private fun reset() {
            // touchPanningInputDevice.onGestureEnded() call removed

            longPressDetected = false
            doubleTapDetected = false
            scrolling = false
            maxFingerDown = 0
            currentDownEvent?.recycle()
            currentDownEvent = null
            cumulatedX = 0f
            cumulatedY = 0f
        }
    }

    fun getTouchPanningInputDevice(): TouchPanningInputDevice {
        return touchPanningInputDevice
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
        private val enabled = pref.input.gesture.swipe2 == "remote-scroll"
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
                        f1Start.set(e.getX(0), e.getY(0))
                        f2Start.set(e.getX(1), e.getY(1))
                        f1Current.set(f1Start)
                        f2Current.set(f2Start)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (detecting) {
                        val i1 = e.findPointerIndex(f1Id)
                        val i2 = e.findPointerIndex(f2Id)
                        if (i1 != -1 && i2 != -1) {
                            f1Current.set(e.getX(i1), e.getY(i1))
                            f2Current.set(e.getX(i2), e.getY(i2))
                        }
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
