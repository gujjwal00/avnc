package com.gaurav.avnc.ui.vnc.gl

import java.nio.FloatBuffer

/**
 * Defines the geometry of a surface to be rendered in 3D space.
 * Implementations provide vertex positions, texture coordinates, normals,
 * and optionally indices for indexed drawing.
 */
interface ProjectedSurface {
    /**
     * @return A [FloatBuffer] containing the vertex coordinates (X, Y, Z).
     */
    fun getVertices(): FloatBuffer

    /**
     * @return A [FloatBuffer] containing the texture coordinates (U, V).
     */
    fun getTextureCoordinates(): FloatBuffer

    /**
     * @return A [FloatBuffer] containing the normal vectors (Nx, Ny, Nz) for each vertex.
     * Normals are essential for lighting calculations.
     */
    fun getNormals(): FloatBuffer

    /**
     * @return A [ShortArray] of indices for indexed drawing (e.g., `glDrawElements`).
     * Returns `null` if the surface is drawn using non-indexed drawing (e.g., `glDrawArrays`).
     */
    fun getIndices(): ShortArray? = null
}
