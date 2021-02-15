/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.gaurav.avnc.viewmodel.VncViewModel

/**
 * Handles different input events.
 *
 * TODO: Reduce [PointF] garbage
 */
class InputHandler(private val viewModel: VncViewModel, private val dispatcher: Dispatcher)
    : ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {

    private val scaleDetector = ScaleGestureDetector(viewModel.getApplication(), this)
    private val gestureDetector = GestureDetector(viewModel.getApplication(), this)
    private val frameScroller = FrameScroller(viewModel) //Should it be in Dispatcher?
    private val dragDetector = DragDetector()

    init {
        scaleDetector.isQuickScaleEnabled = false
    }

    /**
     * Touch event receiver.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        dragDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        frameScroller.stop()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        frameScroller.fling(velocityX, velocityY)
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        val startPoint = PointF(e1.x, e1.y)

        when (e2.pointerCount) {
            1 -> dispatcher.onSwipe1(startPoint, -distanceX, -distanceY)
            2 -> dispatcher.onSwipe2(startPoint, -distanceX, -distanceY)
        }

        return true
    }

    override fun onLongPress(e: MotionEvent) {
        viewModel.frameViewRef.get()?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        if (dragDetector.onLongPress(e))
            return

        dispatcher.onLongPress(PointF(e.x, e.y))
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        dispatcher.onDoubleTap(PointF(e.x, e.y))
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        dispatcher.onTap(PointF(e.x, e.y))
        return true
    }

    /**
     * Small utility class for handling drag gesture (Long Press followed by Swipe/Move).
     */
    private inner class DragDetector {
        private val dragEnabled = viewModel.pref.input.gesture.dragEnabled
        private var longPressDetected = false
        private var isDragging = false
        private var lastX = 0F
        private var lastY = 0F

        fun onLongPress(e: MotionEvent): Boolean {
            if (!dragEnabled)
                return false

            longPressDetected = true
            lastX = e.x
            lastY = e.y
            return true
        }

        fun onTouchEvent(event: MotionEvent) {
            if (!longPressDetected)
                return

            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    isDragging = true
                    val x = event.x
                    val y = event.y

                    dispatcher.onDrag(PointF(x, y), x - lastX, y - lastY)

                    lastX = x
                    lastY = y
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging)
                        dispatcher.onDragEnd(PointF(event.x, event.y))
                    else
                        dispatcher.onLongPress(PointF(event.x, event.y))

                    longPressDetected = false
                    isDragging = false
                }

                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging)
                        dispatcher.onDragEnd(PointF(event.x, event.y))

                    longPressDetected = false
                    isDragging = false
                }
            }
        }
    }
}
