/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import com.gaurav.avnc.util.AppPreferences
import kotlin.math.max
import kotlin.math.min

/**
 * Represent current 'view' state of the frame.
 *
 * Thread safety: Frame state is accessed from multiple threads. Its properties are updated
 *                in UI thread and consumed by the renderer thread. There is a "slight"
 *                chance that Renderer thread may see half-updated state. But it should
 *                "eventually" settle down because any change in frame state is usually
 *                followed by a new render request.
 *
 *                We can make frame state immutable, like String, so that only fully
 *                updated instances are passed around. But we may generate a lot of
 *                outdated instances which may put pressure on garbage collector. So
 *                there should be some sort of caching.
 */
class FrameState(prefs: AppPreferences) {

    /**
     * We have two types of scaling.
     *   1. Base Scale: At this scale frame will be visible completely (in landscape)
     *            and at least one side (width or height) of frame will match the
     *            corresponding side of viewport.
     *
     *   2. Zoom Scale: This value represents 'zoom level' and is modified by the user
     *            during scaling gestures. It works 'on top of' base scale.
     *
     * Effective scale is calculated as the product of these two values.
     */
    var baseScale = 0F; private set
    var zoomScale = 1.0F; private set
    val scale get() = baseScale * zoomScale

    var translateX = 0F; private set
    var translateY = 0F; private set

    //VNC framebuffer size
    var fbWidth = 0F; private set
    var fbHeight = 0F; private set

    //Viewport/FrameView size
    var vpWidth = 0F; private set
    var vpHeight = 0F; private set

    private val minZoomScale = prefs.zoom.min
    private val maxZoomScale = prefs.zoom.max

    fun setFramebufferSize(w: Float, h: Float) {
        fbWidth = w
        fbHeight = h
        calculateBaseScale(true)
        coerceValues()
    }

    fun setViewportSize(w: Float, h: Float) {
        vpWidth = w
        vpHeight = h
        calculateBaseScale(false)
        coerceValues()
    }

    /**
     * Adjust zoom scale according to give [scaleFactor].
     *
     * @return How 'much' of the [scaleFactor] is applied (after coercing zoom scale).
     */
    fun updateZoom(scaleFactor: Float): Float {
        val oldScale = zoomScale

        zoomScale *= scaleFactor
        coerceValues()

        return zoomScale / oldScale //Applied scale factor
    }

    fun pan(deltaX: Float, deltaY: Float) {
        translateX += deltaX
        translateY += deltaY
        coerceValues()
    }

    /**
     * Checks if given point is inside of framebuffer.
     */
    fun isValidFbPoint(x: Float, y: Float) = (x >= 0F && x < fbWidth) && (y >= 0F && y < fbHeight)

    /**
     * Converts given viewport point to framebuffer coordinates.
     * Returns null if given point lies outside of framebuffer.
     */
    fun toFb(vpPoint: PointF): PointF? {
        val fbX = (vpPoint.x - translateX) / scale
        val fbY = (vpPoint.y - translateY) / scale

        if (isValidFbPoint(fbX, fbY))
            return PointF(fbX, fbY)
        else
            return null
    }

    /**
     * Calculates base scale.
     *
     * We are optimizing base scale for 'landscape mode'. In landscape mode,
     * full frame will be visible.
     *
     * TODO: Refactor: Allow increase in base scale if viewport size increase
     */
    private fun calculateBaseScale(force: Boolean) {
        if (baseScale > 0 && !force)
            return  //Already initialized

        if (fbHeight == 0F || fbWidth == 0F || vpHeight == 0F)
            return  //Not enough info yet

        val wRatio = max(vpWidth, vpHeight) / fbWidth
        val hRatio = min(vpWidth, vpHeight) / fbHeight
        baseScale = min(wRatio, hRatio)
    }

    /**
     * Makes sure that state values are within constraints.
     */
    private fun coerceValues() {
        zoomScale = zoomScale.coerceIn(minZoomScale, maxZoomScale)
        translateX = coerceTranslate(translateX, vpWidth, fbWidth)
        translateY = coerceTranslate(translateY, vpHeight, fbHeight)
    }

    /**
     * Coerce translation value in a direction (horizontal/vertical).
     */
    private fun coerceTranslate(current: Float, vp: Float, fb: Float): Float {
        val scaledFb = (fb * scale)
        val diff = vp - scaledFb

        return if (diff >= 0) diff / 2   //Frame will be smaller than viewport, so center it
        else current.coerceIn(diff, 0F)   //otherwise, make sure viewport is completely filled.
    }
}