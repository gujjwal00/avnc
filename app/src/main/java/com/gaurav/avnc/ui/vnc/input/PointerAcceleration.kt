/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.input

import android.view.MotionEvent
import android.view.VelocityTracker
import com.gaurav.avnc.viewmodel.VncViewModel
import kotlin.math.absoluteValue

/**
 * Implements pointer acceleration for Touchpad gesture style.
 *
 * This implementation is heavily inspired by libinput:
 * https://wayland.freedesktop.org/libinput/doc/latest/pointer-acceleration.html
 */
class PointerAcceleration(private val viewModel: VncViewModel) {
    private val displayMetrics = viewModel.frameViewRef.get()!!.context.resources.displayMetrics
    private val xdpi = displayMetrics.xdpi
    private val ydpi = displayMetrics.ydpi
    private val tracker by lazy { VelocityTracker.obtain() }

    fun addMovement(event: MotionEvent) {
        tracker.addMovement(event)
    }

    fun clear() {
        tracker.clear()
    }

    fun compute() {
        tracker.computeCurrentVelocity(1000 /*per second*/)
    }

    fun updateDx(dx: Float): Float {
        return dx * calculateAccelerationFactor(tracker.xVelocity, xdpi)
    }

    fun updateDy(dy: Float): Float {
        return dy * calculateAccelerationFactor(tracker.yVelocity, ydpi)
    }

    private fun calculateAccelerationFactor(vPixelPerSec: Float, dpi: Float): Float {
        if (vPixelPerSec == 0f)
            return 1f

        val vMmPerSec = pixelToMM(vPixelPerSec.absoluteValue, dpi)
        val zoomScale = viewModel.frameState.zoomScale

        if (viewModel.videoDisabled)
            return NoVideoTouchpadProfile.calculateAccelerationFactor(vMmPerSec)
        else
            return TouchpadProfile.calculateAccelerationFactor(vMmPerSec, zoomScale)
    }

    private fun pixelToMM(pixels: Float, dpi: Float): Float {
        return (pixels * 25.4f /*mm per inch*/) / dpi.coerceAtLeast(1f)
    }


    /***********************************************************************************
     * Acceleration Profiles
     ***********************************************************************************/

    /**
     * This profile implements a three-layered acceleration factor:
     *
     *   |                -
     * ^ |              --
     * | |  _________---
     * f | /
     *   |/
     *   |  T1      T2
     *   +-------------------------
     *             v ->
     */
    object NoVideoTouchpadProfile {
        private const val MIN_FACTOR = 0.3f
        private const val MAX_FACTOR = 3.5f
        private const val BASELINE_FACTOR = 1f
        private const val THRESHOLD1 = 10f
        private const val THRESHOLD2 = 80f
        private const val INITIAL_SLOPE = (BASELINE_FACTOR - MIN_FACTOR) / THRESHOLD1

        fun calculateAccelerationFactor(velocity: Float): Float {
            val f = when {
                // For very slow movement, acceleration factor increases linearly (y = mx + c).
                // In this phase, it decelerates the pointer, allowing precise control
                velocity < THRESHOLD1 -> INITIAL_SLOPE * velocity + MIN_FACTOR

                // For normal velocity, constant acceleration factor is used to allow normal
                // 1-to-1 pointer movement
                velocity < THRESHOLD2 -> BASELINE_FACTOR

                // When velocity starts increasing above T2, acceleration factor starts increasing
                // quadratically to move the pointer faster and faster.
                // Following equation is basically y = x^2, with a multiplier to reduce the y value.
                else -> 0.0025f * ((velocity * velocity) / THRESHOLD2) + BASELINE_FACTOR
            }
            return f.coerceIn(MIN_FACTOR, MAX_FACTOR)
        }
    }

    /**
     * This profile works similar to [NoVideoTouchpadProfile], but it dampens
     * the acceleration factor as zoom level increases.
     *
     * This dampening allows more precise pointer control on higher zoom levels.
     */
    object TouchpadProfile {
        fun calculateAccelerationFactor(velocity: Float, zoomScale: Float): Float {
            val f = NoVideoTouchpadProfile.calculateAccelerationFactor(velocity)
            if (zoomScale <= 1)
                return f

            // There are two main features of this dampening:
            // - Proportional to zoomScale: More dampening happens as zoom increases
            // - Proportional to acceleration factor: higher factors are affected more than the lower factors
            //
            // It can be represented as a "dynamic line", whose slope increases with the zoom level.
            val dampenerSlope = (zoomScale - 1) * .07f            // Maps [1-5] zoom to [0-.28] slope
            val dampener = (dampenerSlope * f).coerceIn(0f, 1f)   // At zoomScale = 5 & f = 3.5, dampener => 1
            val result = f - dampener

            return result
        }
    }
}