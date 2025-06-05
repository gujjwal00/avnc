/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.gaurav.avnc.ui.vnc.gl.Renderer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncClient

/**
 * This class renders the VNC framebuffer on screen.
 *
 * It derives from [GLSurfaceView], which creates an EGL Display, where we can
 * render the framebuffer using OpenGL ES. See [GLSurfaceView] for more details.
 *
 * Actual rendering is done by [Renderer], which is executed on a dedicated
 * thread by [GLSurfaceView].
 *
 *
 *-   +-------------------+          +--------------------+         +--------------------+
 *-   |   [FrameView]     |          |  [VncViewModel]    |         |   [VncClient]      |
 *-   +--------+----------+          +----------+---------+         +----------+---------+
 *-            |                                |                              |
 *-            |                                |                              |
 *-            | Render Request                 | [FrameState]                 | Framebuffer
 *-            |                                v                              |
 *-            |                     +----------+---------+                    |
 *-            +-------------------> |     [Renderer]     | <------------------+
 *-                                  +--------------------+

 */
class FrameView(context: Context?, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private lateinit var touchHandler: TouchHandler
    private lateinit var keyHandler: KeyHandler
    internal var mRenderer: com.gaurav.avnc.ui.vnc.gl.Renderer? = null // Field to store renderer

    /**
     * Input connection used for intercepting key events
     */
    inner class InputConnection : BaseInputConnection(this, false) {
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            return keyHandler.onKeyEvent(event) || super.sendKeyEvent(event)
        }
    }

    /**
     * Should be called from [VncActivity.onCreate].
     */
    fun initialize(activity: VncActivity) {
        val viewModel = activity.viewModel

        setEGLContextClientVersion(2)
        val rendererInstance = com.gaurav.avnc.ui.vnc.gl.Renderer(viewModel)
        setRenderer(rendererInstance)
        mRenderer = rendererInstance // Store the instance
        renderMode = RENDERMODE_WHEN_DIRTY

        // Hide local cursor if requested and supported
        if (Build.VERSION.SDK_INT >= 24 && viewModel.pref.input.hideLocalCursor)
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    }

    fun setInputHandlers(keyHandler: KeyHandler, touchHandler: TouchHandler) {
        this.keyHandler = keyHandler
        this.touchHandler = touchHandler
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
        return InputConnection()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return touchHandler.onGenericMotionEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return touchHandler.onHoverEvent(event)
    }

    /**
     * Called by VncActivity to pan the camera in the renderer.
     */
    fun panFrameRenderer(deltaYaw: Float, deltaPitch: Float) {
        // Ensure renderer is of the correct type, though it should be by construction
        mRenderer?.panCamera(deltaYaw, deltaPitch)
    }

    /**
     * Called by VncActivity to zoom the camera in the renderer.
     */
    fun zoomFrameRenderer(deltaZ: Float) {
        mRenderer?.zoomCamera(deltaZ)
    }

    /**
     * Called by VncActivity to reset the camera and surface in the renderer.
     * This typically happens when display-related XR preferences change.
     */
    fun resetRendererCameraAndSurface() {
        // The mRenderer is of type com.gaurav.avnc.ui.vnc.gl.Renderer
        // as set in initialize().
        // Queue the event to run on the GL thread, as resetCameraAndSurface might
        // make GL calls (e.g., frame.bind).
        queueEvent {
            mRenderer?.resetCameraAndSurface()
        }
    }
}