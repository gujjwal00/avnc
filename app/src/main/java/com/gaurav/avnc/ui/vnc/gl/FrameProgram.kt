/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20.GL_CLAMP_TO_EDGE
import android.opengl.GLES20.GL_LINEAR
import android.opengl.GLES20.GL_TEXTURE0
import android.opengl.GLES20.GL_TEXTURE_2D
import android.opengl.GLES20.GL_TEXTURE_MAG_FILTER
import android.opengl.GLES20.GL_TEXTURE_MIN_FILTER
import android.opengl.GLES20.GL_TEXTURE_WRAP_S
import android.opengl.GLES20.GL_TEXTURE_WRAP_T
import android.opengl.GLES20.glActiveTexture
import android.opengl.GLES20.glBindBuffer
import android.opengl.GLES20.glBindTexture
import android.opengl.GLES20.glEnableVertexAttribArray
import android.opengl.GLES20.glGenTextures
import android.opengl.GLES20.glGetAttribLocation
import android.opengl.GLES20.glGetUniformLocation
import android.opengl.GLES20.glTexParameteri
import android.opengl.GLES20.glUniform1i
import android.opengl.GLES20.glUniformMatrix4fv
import android.opengl.GLES20.glUseProgram
import android.opengl.GLES20.glVertexAttribPointer
import android.opengl.GLES20.GL_ARRAY_BUFFER
import android.opengl.GLES20.GL_FLOAT
import android.util.Log
import com.gaurav.avnc.BuildConfig

/**
 * Represents the GL program used for Frame rendering.
 *
 * NOTE: It must be instantiated in an OpenGL context.
 */
class FrameProgram {

    companion object {
        // Attribute constants
        const val A_POSITION = "a_Position"
        const val A_TEXTURE_COORDINATES = "a_TextureCoordinates"
        const val A_NORMAL = "a_Normal" // Added Normal attribute constant

        // Uniform constants
        const val U_PROJECTION = "u_Projection"
        const val U_VIEW = "u_ViewMatrix" // New View Matrix Uniform
        const val U_TEXTURE_UNIT = "u_TextureUnit"
    }

    val program = ShaderCompiler.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_SHADER)
    val aPositionLocation = glGetAttribLocation(program, A_POSITION)
    val aTextureCoordinatesLocation = glGetAttribLocation(program, A_TEXTURE_COORDINATES)
    private var aNormalLocation: Int = -1 // Added Normal attribute location
    val uProjectionLocation = glGetUniformLocation(program, U_PROJECTION)
    val uViewMatrixLocation = glGetUniformLocation(program, U_VIEW) // Get location for View Matrix
    val uTexUnitLocation = glGetUniformLocation(program, U_TEXTURE_UNIT)
    val textureId = createTexture()
    var validated = false

    init {
        aNormalLocation = glGetAttribLocation(program, A_NORMAL)
    }


    fun setUniforms(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        glUniformMatrix4fv(uViewMatrixLocation, 1, false, viewMatrix, 0) // Pass View Matrix
        glUniformMatrix4fv(uProjectionLocation, 1, false, projectionMatrix, 0) // Pass Projection Matrix
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)
        glUniform1i(uTexUnitLocation, 0)
    }

    private fun createTexture(): Int {
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

    fun validate() {
        if (BuildConfig.DEBUG && !validated) {
            ShaderCompiler.validateProgram(program)
            validated = true
        }
    }

    fun useProgram() {
        glUseProgram(program)
    }

    fun enablePosition(bufferId: Int) {
        glBindBuffer(GL_ARRAY_BUFFER, bufferId)
        glVertexAttribPointer(aPositionLocation, 3, GL_FLOAT, false, 0, 0) // 3 components for vec3
        glEnableVertexAttribArray(aPositionLocation)
    }

    fun enableTexCoord(bufferId: Int) {
        if (aTextureCoordinatesLocation != -1) { // ADDED THIS CHECK
            glBindBuffer(GL_ARRAY_BUFFER, bufferId)
            glVertexAttribPointer(aTextureCoordinatesLocation, 2, GL_FLOAT, false, 0, 0) // 2 components for vec2
            glEnableVertexAttribArray(aTextureCoordinatesLocation)
        }
    }

    fun enableNormal(bufferId: Int) {
        if (aNormalLocation != -1) {
            glBindBuffer(GL_ARRAY_BUFFER, bufferId)
            glVertexAttribPointer(aNormalLocation, 3, GL_FLOAT, false, 0, 0) // 3 components for vec3
            glEnableVertexAttribArray(aNormalLocation)
        }
    }
}