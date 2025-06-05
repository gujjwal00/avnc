/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20
import java.nio.ShortBuffer

/**
 * Frame represents geometry that can be drawn.
 * It now sources its geometry data from a ProjectedSurface
 * and manages VBOs and an optional EBO.
 */
class Frame {

    // VBO IDs: 0 for vertices, 1 for texture coordinates, 2 for normals
    private val vboIds = IntArray(3)
    private var eboId: Int = 0
    private var indexCount: Int = 0
    private var vertexCount: Int = 0 // For glDrawArrays
    private var drawMode: Int = GLES20.GL_TRIANGLE_STRIP // Default, can be changed by ProjectedSurface

    init {
        // Initialize buffer IDs to 0 (invalid in OpenGL context until generated)
        vboIds.fill(0)
    }

    /**
     * Binds vertex data from the ProjectedSurface to VBOs and EBO (if applicable).
     * Enables vertex attributes.
     */
    fun bind(program: FrameProgram, surface: ProjectedSurface) {
        // Clean up old buffers if they exist
        if (vboIds[0] != 0) GLES20.glDeleteBuffers(vboIds.size, vboIds, 0)
        if (eboId != 0) GLES20.glDeleteBuffers(1, intArrayOf(eboId), 0)

        GLES20.glGenBuffers(3, vboIds, 0)

        // Vertex Buffer
        val vertices = surface.getVertices()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.capacity() * 4, vertices, GLES20.GL_STATIC_DRAW)
        program.enablePosition(vboIds[0])

        // Texture Coordinates Buffer
        val texCoords = surface.getTextureCoordinates()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, texCoords.capacity() * 4, texCoords, GLES20.GL_STATIC_DRAW)
        program.enableTexCoord(vboIds[1])

        // Normals Buffer
        val normals = surface.getNormals()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[2])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, normals.capacity() * 4, normals, GLES20.GL_STATIC_DRAW)
        program.enableNormal(vboIds[2])

        // Index Buffer (EBO)
        val indices = surface.getIndices()
        if (indices != null) {
            val eboArray = IntArray(1)
            GLES20.glGenBuffers(1, eboArray, 0)
            eboId = eboArray[0]
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId)
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ShortBuffer.wrap(indices), GLES20.GL_STATIC_DRAW)
            indexCount = indices.size
            drawMode = GLES20.GL_TRIANGLES // Typically use GL_TRIANGLES with indices
        } else {
            eboId = 0 // Ensure eboId is 0 if no indices
            indexCount = 0
            // Vertex count for glDrawArrays (assuming 3 components X,Y,Z per vertex)
            vertexCount = surface.getVertices().capacity() / 3
            // Set drawMode based on surface type, or surface could expose this
            if (surface is FlatSurface) {
                drawMode = GLES20.GL_TRIANGLE_STRIP // FlatSurface (4 vertices) is suitable for TRIANGLE_STRIP
                vertexCount = 4 // FlatSurface has 4 vertices
            } else {
                // Default for non-indexed, non-FlatSurface cases, might need adjustment
                drawMode = GLES20.GL_TRIANGLES // Or some other default
            }
        }
        // Unbind ARRAY_BUFFER and ELEMENT_ARRAY_BUFFER after setup
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        if (eboId != 0) {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    /**
     * Draws the frame using either glDrawElements (if EBO is used) or glDrawArrays.
     * Assumes that the correct shader program is already in use and uniforms are set.
     * Also assumes that necessary buffers were bound in the bind() call.
     */
    fun draw() {
        if (eboId != 0) {
            // Bind the EBO before drawing elements
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId)
            GLES20.glDrawElements(drawMode, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
            // Unbind EBO after drawing
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        } else {
            GLES20.glDrawArrays(drawMode, 0, vertexCount)
        }
    }

    // updateFbSize method is removed as geometry is now from ProjectedSurface
}