/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20.*
import android.util.Log

/**
 * Represents the GL program used for Frame rendering.
 *
 * NOTE: It must be instantiated in an OpenGL context.
 */
internal class FrameProgram {

    companion object {
        // Attribute constants
        const val A_POSITION = "a_Position"
        const val A_TEXTURE_COORDINATES = "a_TextureCoordinates"

        // Uniform constants
        const val U_PROJECTION = "u_Projection"
        const val U_TEXTURE_UNIT = "u_TextureUnit"
    }

    val program = ShaderCompiler.buildProgram(Shaders.VERTEX_SHADER, Shaders.FRAGMENT_SHADER)
    val a_PositionLocation = glGetAttribLocation(program, A_POSITION)
    val a_TextureCoordLocation = glGetAttribLocation(program, A_TEXTURE_COORDINATES)
    val u_ProjectionLocation = glGetUniformLocation(program, U_PROJECTION)
    val u_TexUnitLocation = glGetUniformLocation(program, U_TEXTURE_UNIT)
    val textureId = createTexture()


    fun setUniforms(projectionMatrix: FloatArray) {
        glUniformMatrix4fv(u_ProjectionLocation, 1, false, projectionMatrix, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureId)
        glUniform1i(u_TexUnitLocation, 0)
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
        glBindTexture(GL_TEXTURE_2D, 0)
        return texturesObjects[0]
    }

    fun useProgram() {
        glUseProgram(program)
    }
}