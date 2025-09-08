/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20.GL_BLEND
import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA
import android.opengl.GLES20.GL_SRC_ALPHA
import android.opengl.GLES20.glBlendFunc
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glDisable
import android.opengl.GLES20.glEnable
import android.opengl.GLES20.glViewport
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.gaurav.avnc.viewmodel.VncViewModel
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Frame renderer.
 */
class Renderer(val viewModel: VncViewModel) : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val drawCursor = !viewModel.pref.input.hideRemoteCursor
    private val client = viewModel.client
    private lateinit var program: Program
    private lateinit var frame: Frame
    private lateinit var cursor: Cursor

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0f, 0f, 0f, 1f)

        frame = Frame()
        cursor = Cursor()
        program = Program()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
    }

    /**
     * Draws frame on screen according to current state.
     *
     * Y-axis of coordinate system used by OpenGL is in opposite direction (upwards)
     * relative to Y-axis in screen coordinates (downwards).
     *
     * To compensate for this, we invert the Y-coordinates of drawn frame. This is
     * achieved by:
     *              1. Using clipping region [-height, 0] instead of [0, height] for Y-axis
     *              2. Inverting sign of Y-axis position
     *              3. Inverting sign of Y-axis scale (to flip the frame)
     *
     * So the frame is drawn as follows in OpenGL:
     *
     *              +Y ^
     *                 |
     *                 |[0,0]            [width,0]
     *        -X <-----|---------------------+-------------> +X
     *                 |                     |
     *                 |                     |
     *                 |       Frame         |
     *                 |                     |
     *                 |                     |
     *                 +---------------------+
     *                 |[0,-height]    [width,-height]
     *                 |
     *              -Y v
     *
     * Doing this inversion here allows rest of the code to not worry about difference
     * in Y-axis direction.
     *
     */
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_BLEND)

        if (!client.connected || client.frameBufferUpdatesPaused.get())
            return

        val state = viewModel.frameState.getSnapshot()
        if (state.vpWidth == 0f || state.vpHeight == 0f)
            return

        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.orthoM(projectionMatrix, 0, 0f, state.vpWidth, -state.vpHeight, 0f, -1f, 1f)
        Matrix.translateM(projectionMatrix, 0, state.frameX, -state.frameY, 0f)
        Matrix.scaleM(projectionMatrix, 0, state.scale, -state.scale, 1f)

        program.useProgram()
        program.setUniforms(projectionMatrix)

        frame.updateFbSize(state.fbWidth, state.fbHeight)
        frame.bind(program)
        client.uploadFrameTexture()
        frame.draw()

        program.validate()

        if (drawCursor) {
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            val ci = client.cursorInfo

            cursor.update(client.pointerX.toFloat(), client.pointerY.toFloat(), ci, frame)
            cursor.bind(program)
            client.uploadCursorTexture()
            cursor.draw()

            glDisable(GL_BLEND)
        }
    }
}