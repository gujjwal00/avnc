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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.gaurav.avnc.ui.vnc.gl.Renderer

/**
 * FrameView renders the VNC framebuffer on screen.
 */
class FrameView(context: Context?, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private lateinit var frameState: FrameState
    private lateinit var dispatcher: Dispatcher
    private lateinit var touchHandler: TouchHandler

    /**
     * Should be called from [VncActivity.onCreate].
     */
    fun initialize(activity: VncActivity) {
        val viewModel = activity.viewModel

        frameState = viewModel.frameState
        dispatcher = activity.dispatcher
        touchHandler = activity.touchHandler

        setEGLContextClientVersion(2)
        setRenderer(Renderer(viewModel))
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        frameState.setViewportSize(w.toFloat(), h.toFloat())
    }


    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
        return FrameInputConnection(dispatcher, this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler.onTouchEvent(event)
    }
}