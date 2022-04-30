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
 * Framebuffer: This is the buffer holding pixel data. It resides in native memory.
 *
 * Frame: This is the actual content rendered on screen. It can be thought of as
 * 'rendered framebuffer'. Its size changes based on current [scale] and its position
 * is stored in [frameX] & [frameY].
 *
 * Window: Top-level window of the application/activity.
 *
 * Viewport: This is the area of screen where frame is rendered. It is denoted by [FrameView].
 *
 *     Window denotes the 'total' area available to our activity while viewport
 *     denotes the 'visible to user' area. Normally they would be same but
 *     viewport can be smaller than window (e.g. if soft keyboard is visible).
 *
 *     +---------------------------+
 *     |                           |
 *     |         ViewPort          |
 *     |                           |   Window
 *     +---------------------------+
 *     |      Soft Keyboard        |
 *     +---------------------------+
 *
 *     Differentiating between window & viewport allows us to handle layout changes more
 *     easily and cleanly. We use window size to calculate base scale because we don't want
 *     to change scale when keyboard is shown/hidden. And viewport size is used for coercing
 *     frame position so that user can pan the whole frame even if keyboard is visible.
 *
 *
 * State & Coordinates
 * ===================
 *
 * Both frame & viewport are in same coordinate space. Viewport is assumed to be fixed
 * in its place with [0,0] represented by top-left corner. Only frame is scaled/moved.
 * To make sure frame does not move off-screen, after each state change, values are
 * coerced within range by [coerceValues].
 *
 * Rendering is done by [com.gaurav.avnc.ui.vnc.gl.Renderer] based on these values.
 *
 *
 * Scaling
 * =======
 *
 * Scaling controls the 'size' of rendered frame. It involves multiple factors, like window size,
 * framebuffer size, user choice etc. To achieve best experience, we split scaling in two parts.
 * One automatic, and one user controlled.
 *
 * 1. Base Scale [baseScale] :
 * Motivation behind base scale is to start with the most optimal frame size. It is automatically
 * calculated (and updated) using window size & framebuffer size. When orientation of local
 * device is such that longer edge of the window is aligned with longer edge of the frame,
 * base scale will satisfy following constraints (see [calculateBaseScale]):
 *
 * - Frame is completely visible
 * - Frame's aspect ratio is maintained
 * - Maximum window space is utilized
 *
 * 2. Zoom Scale [zoomScale] :
 * This is the user controlled part. It always starts at 1 (100% zoom), and only updated in response
 * to pinch gestures. Conceptually, it works 'on top of' the base scale.
 *
 *
 * Effective scale [scale] is calculated as the product of these two parts, so:

 *      FrameSize = (FramebufferSize * BaseScale) * ZoomScale
 *
 *
 * Thread safety
 * =============
 *
 * Frame state is accessed from two threads: Its properties are updated in
 * UI thread and consumed by the renderer thread. There is a "slight" chance that
 * Renderer thread may see half-updated state. But it should "eventually" settle down
 * because any change in frame state is usually followed by a new render request.
 */
class FrameState(private val minZoomScale: Float = 0.5F, private val maxZoomScale: Float = 5F) {

    constructor(prefs: AppPreferences) : this(prefs.viewer.zoomMin, prefs.viewer.zoomMax)

    var baseScale = 1F; private set
    var zoomScale = 1F; private set
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

    //Size of activity window
    var windowWidth = 0F; private set
    var windowHeight = 0F; private set


    fun setFramebufferSize(w: Float, h: Float) {
        fbWidth = w
        fbHeight = h
        calculateBaseScale()
        coerceValues()
    }

    fun setViewportSize(w: Float, h: Float) {
        vpWidth = w
        vpHeight = h
        coerceValues()
    }

    fun setWindowSize(w: Float, h: Float) {
        windowWidth = w
        windowHeight = h
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

    /**
     * Converts given framebuffer point to corresponding point in viewport.
     */
    fun toVP(fbPoint: PointF): PointF {
        return PointF(fbPoint.x * scale + frameX, fbPoint.y * scale + frameY)
    }


    private fun calculateBaseScale() {
        if (fbHeight == 0F || fbWidth == 0F || windowHeight == 0F)
            return  //Not enough info yet

        val s1 = max(windowWidth, windowHeight) / max(fbWidth, fbHeight)
        val s2 = min(windowWidth, windowHeight) / min(fbWidth, fbHeight)

        baseScale = min(s1, s2)
    }

    /**
     * Makes sure state values are within constraints.
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