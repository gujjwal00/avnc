/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * FrameView renders the VNC framebuffer on screen.
 */
class FrameView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {

    constructor(context: Context?) : this(context, null)

    /**
     * Set by [VncActivity].
     */
    lateinit var activity: VncActivity

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        activity.viewModel.frameState.setViewportSize(w.toFloat(), h.toFloat())
    }


    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or (EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN)
        return FrameInputConnection(activity.dispatcher, this)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return activity.inputHandler.onTouchEvent(event)
    }
}