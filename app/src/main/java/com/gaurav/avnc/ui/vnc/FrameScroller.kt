/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import com.gaurav.avnc.viewmodel.VncViewModel

/**
 * Implements fling animation for frame.
 */
class FrameScroller(val viewModel: VncViewModel) {

    private val fs = viewModel.frameState
    private val xAnimator = FlingAnimation(FloatValueHolder())
    private val yAnimator = FlingAnimation(FloatValueHolder())

    init {
        xAnimator.addUpdateListener { _, x, _ ->
            viewModel.moveFrameTo(x, fs.translateY)
        }

        yAnimator.addUpdateListener { _, y, _ ->
            viewModel.moveFrameTo(fs.translateX, y)
        }
    }

    /**
     * Stop current animation
     */
    fun stop() {
        xAnimator.cancel()
        yAnimator.cancel()
    }

    /**
     * Starts fling animation according to given velocities
     */
    fun fling(vx: Float, vy: Float) {
        stop()

        val x = fs.translateX
        val y = fs.translateY

        /**
         * Fling limits.
         *
         * There are two cases:
         *
         * 1) x >= 0 : It means frame is completely visible and centered horizontally.
         *             In this case minX/maxX = x (ie. no movement possible).

         * 2) x < 0  : Here, 'scaled frame width' > 'viewport width'. In this case
         *             minX is negative and maxX = 0.
         *
         * minY/maxY are calculated similarly.
         */
        val minX = if (x >= 0) x else fs.vpWidth - (fs.fbWidth * fs.scale)
        val maxX = if (x >= 0) x else 0F
        val minY = if (y >= 0) y else fs.vpHeight - (fs.fbHeight * fs.scale)
        val maxY = if (y >= 0) y else 0F

        xAnimator.apply {
            setStartValue(x)
            setStartVelocity(vx)
            setMinValue(minX)
            setMaxValue(maxX)
            start()
        }

        yAnimator.apply {
            setStartValue(y)
            setStartVelocity(vy)
            setMinValue(minY)
            setMaxValue(maxY)
            start()
        }
    }
}