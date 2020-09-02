/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 *
 * Image frame is represented as two triangles:
 *
 *     [0, fbHeight]  +-----------+  [fbWidth, fbHeight]
 *                    |          /|
 *                    |       /   |
 *                    |    /      |
 *                    | /         |
 *            [0, 0]  +-----------+  [fbWidth, 0]
 *
 * Frame texture is mapped onto these triangles.
 */
internal class Frame {

    companion object {
        const val FLOAT_SIZE = 4
        const val TRIANGLE_COMPONENT = 2    //[x,y]
        const val TEXTURE_COMPONENT = 2     //[x,y]
        const val STRIDE = (TEXTURE_COMPONENT + TEXTURE_COMPONENT) * FLOAT_SIZE
    }

    private var fbWidth = 0F
    private var fbHeight = 0F
    private var vertexData: FloatArray
    private var vertexBuffer: FloatBuffer

    init {
        vertexData = generateVertexData()
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
    }

    /**
     * Generates vertex data for frame.
     */
    private fun generateVertexData(): FloatArray {

        //Note: Textures have their own coordinate system. [0,0] represents bottom-left
        //      and [1,1] represents upper-right corner.

        return floatArrayOf(
                //@formatter:off
                //Triangle coordinates     //Texture coordinates
                0F, 0F,                    0F, 0F,
                fbWidth, 0F,               1F, 0F,
                fbWidth, fbHeight,         1F, 1F,

                0F, 0F,                    0F, 0F,
                fbWidth, fbHeight,         1F, 1F,
                0F, fbHeight,              0F, 1F
                //@formatter:on
        )
    }


    fun bind(program: FrameProgram) {
        setVertexAttributePointer(0, program.a_PositionLocation, TRIANGLE_COMPONENT, STRIDE)
        setVertexAttributePointer(TRIANGLE_COMPONENT, program.a_TextureCoordLocation, TEXTURE_COMPONENT, STRIDE)
    }

    private fun setVertexAttributePointer(dataOffset: Int, attributeLocation: Int, componentCount: Int, stride: Int) {
        vertexBuffer.position(dataOffset)
        glVertexAttribPointer(attributeLocation, componentCount, GL_FLOAT, false, stride, vertexBuffer)
        glEnableVertexAttribArray(attributeLocation)
        vertexBuffer.position(0)
    }

    /**
     * Set size of this underlying framebuffer.
     * It is used to calculates frame vertices.
     */
    fun updateSize(width: Float, height: Float) {
        if (width == fbWidth && height == fbHeight)
            return //Nothing to do

        fbWidth = width
        fbHeight = height
        vertexData = generateVertexData()
        vertexBuffer.position(0)
        vertexBuffer.put(vertexData)
    }

    fun draw() {
        glDrawArrays(GL_TRIANGLES, 0, 6)
    }
}