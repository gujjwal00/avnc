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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Handles different input events.
 */
class InputHandler(private val context: Context, private val dispatcher: Dispatcher)
    : ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, this)

    /**
     * Touch event receiver.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent) = true

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {}
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        return false
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        dispatcher.onScroll(-distanceX, -distanceY)
        return true
    }

    override fun onLongPress(e: MotionEvent) {
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
}
