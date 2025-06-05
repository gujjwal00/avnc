package com.gaurav.avnc.ui.vnc.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * An implementation of [ProjectedSurface] that represents a simple flat rectangle (quad)
 * centered at the origin in the XY plane, facing the positive Z-axis.
 * The dimensions of the quad are calculated to maintain the aspect ratio of the VNC framebuffer,
 * scaled to a target display width.
 * This surface does not use indexed drawing by default.
 *
 * @param vncFbWidth The width of the VNC framebuffer. Used to calculate aspect ratio.
 * @param vncFbHeight The height of the VNC framebuffer. Used to calculate aspect ratio.
 * @param targetDisplayWidth The desired width of the quad in OpenGL world units. The height will be scaled
 *                           to maintain the VNC framebuffer's aspect ratio.
 */
class FlatSurface(vncFbWidth: Float, vncFbHeight: Float, targetDisplayWidth: Float = 2f) : ProjectedSurface {

    private val vertices: FloatBuffer
    private val textureCoordinates: FloatBuffer
    private val normals: FloatBuffer

    init {
        // Ensure framebuffer dimensions are positive to avoid division by zero or negative aspect ratios.
        // Default to 16:9 aspect ratio if initial dimensions are invalid.
        val actualFbWidth = if (vncFbWidth > 0f) vncFbWidth else 16f
        val actualFbHeight = if (vncFbHeight > 0f) vncFbHeight else 9f

        val aspectRatio = actualFbWidth / actualFbHeight

        // Calculate the quad's dimensions in world units.
        // The width is set to targetDisplayWidth, and height is derived from the aspect ratio.
        val width = targetDisplayWidth
        val height = targetDisplayWidth / aspectRatio

        // Half dimensions for easier calculations relative to the origin (0,0,0).
        val x = width / 2f
        val y = height / 2f

        // Define vertex positions for a quad: (bottom-left, bottom-right, top-left, top-right)
        // This order is suitable for GL_TRIANGLE_STRIP.
        val vertexData = floatArrayOf(
            // X,  Y,  Z
            -x, -y, 0f, // Vertex 0: Bottom-left
             x, -y, 0f, // Vertex 1: Bottom-right
            -x,  y, 0f, // Vertex 2: Top-left
             x,  y, 0f  // Vertex 3: Top-right
        )
        vertices = ByteBuffer.allocateDirect(vertexData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertexData)
                position(0)
            }

        // Define texture coordinates (UV mapping).
        // (0,0) is typically bottom-left in OpenGL textures, but VNC frames might be top-left.
        // This mapping (0,1 top-left, 1,0 bottom-right) is common if texture origin is top-left.
        // For GL_TRIANGLE_STRIP with the above vertices:
        // V0 (-x,-y) -> (0,1) or (0,0) depending on desired texture orientation
        // V1 ( x,-y) -> (1,1) or (1,0)
        // V2 (-x, y) -> (0,0) or (0,1)
        // V3 ( x, y) -> (1,0) or (1,1)
        // The current vertex order and this UV order will render texture upright if texture origin is bottom-left.
        // If VNC frame texture has origin at top-left, UVs might need flipping (e.g. y = 1-y).
        // For now, using standard UV mapping for a quad (0,0 at one corner, 1,1 at opposite).
        // Correct mapping for the given vertex order (BL, BR, TL, TR for TRIANGLE_STRIP):
        // BL: (0,0), BR: (1,0), TL: (0,1), TR: (1,1) if texture origin is bottom-left.
        // The provided one (0,1), (1,1), (0,0), (1,0) means:
        // -x,-y (BL) -> (0,1) (Texture Top-Left)
        //  x,-y (BR) -> (1,1) (Texture Top-Right)
        // -x, y (TL) -> (0,0) (Texture Bottom-Left)
        //  x, y (TR) -> (1,0) (Texture Bottom-Right)
        // This effectively flips the texture vertically if its origin is bottom-left.
        // If VNC frame origin is top-left, this mapping is correct.
        val textureData = floatArrayOf(
            // U,  V
            0f, 1f, // Corresponds to -x, -y (Vertex 0)
            1f, 1f, // Corresponds to  x, -y (Vertex 1)
            0f, 0f, // Corresponds to -x,  y (Vertex 2)
            1f, 0f  // Corresponds to  x,  y (Vertex 3)
        )
        textureCoordinates = ByteBuffer.allocateDirect(textureData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(textureData)
                position(0)
            }

        // Define normal vectors. For a flat surface in the XY plane, all normals point along positive Z.
        val normalData = floatArrayOf(
            // Nx, Ny, Nz
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f
        )
        normals = ByteBuffer.allocateDirect(normalData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(normalData)
                position(0)
            }
    }

    /** Returns the buffer containing vertex coordinates (X, Y, Z). */
    override fun getVertices(): FloatBuffer = vertices

    /** Returns the buffer containing texture coordinates (U, V). */
    override fun getTextureCoordinates(): FloatBuffer = textureCoordinates

    /** Returns the buffer containing normal vectors (Nx, Ny, Nz). */
    override fun getNormals(): FloatBuffer = normals

    /** FlatSurface does not use indexed drawing by default, so it returns `null`. */
    override fun getIndices(): ShortArray? = null
}
