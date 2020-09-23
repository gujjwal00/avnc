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
    private val showZoomLevel = viewModel.pref.zoom.showLevel
    private val frameScroller = FrameScroller(viewModel) //Should it be in Dispatcher?

    init {
        scaleDetector.isQuickScaleEnabled = viewModel.pref.zoom.quick
    }

    /**
     * Touch event receiver.
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        frameScroller.stop()
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector) = true
    override fun onScaleEnd(detector: ScaleGestureDetector) {
        viewModel.zoomLevelText.value = ""
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        dispatcher.onScale(detector.scaleFactor, detector.focusX, detector.focusY)

        if (showZoomLevel) {
            val zoomPercent = (viewModel.frameState.zoomScale * 100).toInt()
            viewModel.zoomLevelText.value = "${zoomPercent}%"
        }
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
