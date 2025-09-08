/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.graphics.RectF
import android.opengl.GLES20.GL_CLAMP_TO_EDGE
import android.opengl.GLES20.GL_FLOAT
import android.opengl.GLES20.GL_LINEAR
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TEXTURE_MAG_FILTER
import android.opengl.GLES20.GL_TEXTURE_MIN_FILTER
import android.opengl.GLES20.GL_TEXTURE_WRAP_S
import android.opengl.GLES20.GL_TEXTURE_WRAP_T
import android.opengl.GLES20.GL_TRIANGLES
import android.opengl.GLES20.glBindTexture
import android.opengl.GLES20.glDrawArrays
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glGenTextures
import android.opengl.GLES20.glTexParameteri
import android.opengl.GLES20.glVertexAttribPointer
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * This class handles rendering of a 2D image.
 *
 * The image is represented with two side-by-side triangles:
 *
 *       [0, height]  +-----------+  [width, height]
 *                    |  T2      /|
 *                    |       /   |
 *                    |    /      |
 *                    | /     T1  |
 *            [0, 0]  +-----------+  [width, 0]
 *
 * Image texture is mapped onto these two triangles.
 */
open class Image {

    companion object {
        const val FLOAT_SIZE = 4
        const val TRIANGLE_COMPONENTS = 2    //[x,y]
        const val TEXTURE_COMPONENTS = 2     //[x,y]
        const val VERTEX_COUNT = 6
    }

    private val drawVertexBuffer = allocateVertexBuffer(VERTEX_COUNT, TRIANGLE_COMPONENTS)
    private val textureVertexBuffer = allocateVertexBuffer(VERTEX_COUNT, TEXTURE_COMPONENTS)
    private val texture = allocateTexture()

    private fun allocateVertexBuffer(vertexCount: Int, vertexComponents: Int): FloatBuffer {
        return ByteBuffer.allocateDirect(vertexCount * vertexComponents * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
    }

    private fun allocateTexture(): Int {
        val texturesObjects = intArrayOf(0)
        glGenTextures(1, texturesObjects, 0)
        if (texturesObjects[0] == 0) {
            Log.e("Texture", "Could not generate texture.")
            return 0
        }

        glBindTexture(GL_TEXTURE_2D, texturesObjects[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
        return texturesObjects[0]
    }

    private fun updateVertexBuffer(buffer: FloatBuffer, vararg data: Float) {
        check(data.size == buffer.capacity())
        buffer.rewind()
        buffer.put(data)
        buffer.rewind()
    }

    private fun updateVertexBuffer(buffer: FloatBuffer, rect: RectF) {
        val l = rect.left
        val r = rect.right
        // Y-axis in GL is inverted w.r.t. Y-axis in UI
        val t = rect.bottom
        val b = rect.top

        updateVertexBuffer(
                buffer,

                // Triangle 1
                l, b,
                r, b,
                r, t,

                // Triangle 2
                l, b,
                r, t,
                l, t,
        )
    }

    /**
     * Updates drawing coordinates of the image
     */
    fun updateDrawRect(rect: RectF) {
        updateVertexBuffer(drawVertexBuffer, rect)
    }

    /**
     * Updates sampling area of the texture.
     * Values in [rect] must be in texture coordinates (in range [0, 1])
     */
    fun updateTextureRect(rect: RectF) {
        updateVertexBuffer(textureVertexBuffer, rect)
    }

    /**
     * Updates program attributes according to this image
     */
    fun bind(program: Program) {
        setVertexAttributePointer(program.aPositionLocation, TRIANGLE_COMPONENTS, drawVertexBuffer)
        setVertexAttributePointer(program.aTextureCoordinatesLocation, TEXTURE_COMPONENTS, textureVertexBuffer)
        glBindTexture(GL_TEXTURE_2D, texture)
        // Texture data is uploaded from native code
    }

    private fun setVertexAttributePointer(attributeLocation: Int, componentCount: Int, buffer: FloatBuffer) {
        buffer.rewind()
        glVertexAttribPointer(attributeLocation, componentCount, GL_FLOAT, false, componentCount * FLOAT_SIZE, buffer)
        glEnableVertexAttribArray(attributeLocation)
    }

    fun draw() {
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT)
    }
}