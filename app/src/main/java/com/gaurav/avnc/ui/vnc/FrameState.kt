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
 * Represents current 'view' state of the frame.
 *
 * Terminology
 * ===========
 *
 * Viewport: This is the area of screen where frame is rendered. Currently,
 * for all intent & purpose it is equal to the size of [FrameView].
 *
 * Framebuffer: This is the buffer holding pixel data. It resides in native memory.
 *
 * Frame: This is the actual content rendered on screen. It can be thought of as
 * 'scaled framebuffer'. Its size changes based on current [scale] and its position
 * is stored in [frameX] & [frameY].
 *
 *
 * State & Coordinates
 * ===================
 *
 * Both frame & viewport are in same coordinate space. Viewport is assumed to be fixed
 * in its place with [0,0] represented by top-left corner. Only frame is scaled/moved.
 * To make sure that frame does not move off screen, after each state change, values
 * are coerced within range by [coerceValues].
 *
 * Rendering is done by [com.gaurav.avnc.ui.vnc.gl.Renderer] based on these values.
 *
 *
 * Scaling
 * =======
 *
 * Scaling controls the 'size' of rendered frame. Conceptually, scaling is done in two steps:
 *
 * 1. Base Scale [baseScale] :
 *
 * This is used to manage the difference between framebuffer size & viewport size.
 * At this scale frame will be completely visible inside viewport (in landscape mode),
 * and at least one side of frame will match the corresponding side of viewport.
 *
 * 2. Zoom Scale [zoomScale] :
 *
 * This value represents 'zoom level' requested by the user (by pinch gestures).
 * It works 'on top of' base scale.
 *
 * Effective scale [scale] is calculated as the product of these two values.
 *
 * So:      FrameSize = (FramebufferSize * BaseScale) * ZoomScale
 *
 * Thread safety
 * =============
 *
 * Frame state is accessed from multiple threads. Its properties are updated in
 * UI thread and consumed by the renderer thread. There is a "slight" chance that
 * Renderer thread may see half-updated state. But it should "eventually" settle down
 * because any change in frame state is usually followed by a new render request.
 */
class FrameState(private val minZoomScale: Float = 0.5F, private val maxZoomScale: Float = 5F) {

    constructor(prefs: AppPreferences) : this(prefs.display.zoomMin, prefs.display.zoomMax)

    var baseScale = 0F; private set
    var zoomScale = 1.0F; private set
    val scale get() = baseScale * zoomScale

    //Frame position, relative to top-left corner (0,0)
    var frameX = 0F; private set
    var frameY = 0F; private set

    //VNC framebuffer size
    var fbWidth = 0F; private set
    var fbHeight = 0F; private set

    //Viewport/FrameView size
    var vpWidth = 0F; private set
    var vpHeight = 0F; private set


    fun setFramebufferSize(w: Float, h: Float) {
        fbWidth = w
        fbHeight = h
        calculateBaseScale()
        coerceValues()
    }

    fun setViewportSize(w: Float, h: Float) {
        vpWidth = w
        vpHeight = h
        calculateBaseScale()
        coerceValues()
    }

    /**
     * Adjust zoom scale according to give [scaleFactor].
     *
     * Returns 'how much' scale factor is actually applied (after coercing).
     */
    fun updateZoom(scaleFactor: Float): Float {
        val oldScale = zoomScale

        zoomScale *= scaleFactor
        coerceValues()

        return zoomScale / oldScale //Applied scale factor
    }

    fun resetZoom() {
        zoomScale = 1F
        coerceValues()
    }

    /**
     * Shift frame by given delta.
     */
    fun pan(deltaX: Float, deltaY: Float) {
        frameX += deltaX
        frameY += deltaY
        coerceValues()
    }

    /**
     * Move frame to given position.
     */
    fun moveTo(x: Float, y: Float) {
        frameX = x
        frameY = y
        coerceValues()
    }

    /**
     * Checks if given point is inside of framebuffer.
     */
    fun isValidFbPoint(x: Float, y: Float) = (x >= 0F && x < fbWidth) && (y >= 0F && y < fbHeight)

    /**
     * Converts given viewport point to corresponding framebuffer point.
     * Returns null if given point lies outside of framebuffer.
     */
    fun toFb(vpPoint: PointF): PointF? {
        val fbX = (vpPoint.x - frameX) / scale
        val fbY = (vpPoint.y - frameY) / scale

        if (isValidFbPoint(fbX, fbY))
            return PointF(fbX, fbY)
        else
            return null
    }

    private fun calculateBaseScale() {
        if (fbHeight == 0F || fbWidth == 0F || vpHeight == 0F)
            return  //Not enough info yet

        //Base scale is calculated as-if we are in 'Landscape' mode.
        //So larger viewport side is treated as width and smaller as height
        val wRatio = max(vpWidth, vpHeight) / fbWidth
        val hRatio = min(vpWidth, vpHeight) / fbHeight

        baseScale = min(wRatio, hRatio)
    }

    /**
     * Makes sure that state values are within constraints.
     */
    private fun coerceValues() {
        zoomScale = zoomScale.coerceIn(minZoomScale, maxZoomScale)
        frameX = coercePosition(frameX, vpWidth, fbWidth)
        frameY = coercePosition(frameY, vpHeight, fbHeight)
    }

    /**
     * Coerce position value in a direction (horizontal/vertical).
     */
    private fun coercePosition(current: Float, vp: Float, fb: Float): Float {
        val scaledFb = (fb * scale)
        val diff = vp - scaledFb

        return if (diff >= 0) diff / 2   //Frame will be smaller than viewport, so center it
        else current.coerceIn(diff, 0F)  //otherwise, make sure viewport is completely filled.
    }
}