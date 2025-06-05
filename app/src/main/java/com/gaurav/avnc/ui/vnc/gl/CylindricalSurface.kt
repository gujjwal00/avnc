package com.gaurav.avnc.ui.vnc.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * An implementation of [ProjectedSurface] that represents a cylinder aligned with the Y-axis.
 * The cylinder is composed of a series of quadrilateral segments, each rendered as two triangles.
 * This class generates vertices, texture coordinates, normals, and indices for indexed drawing.
 *
 * @property radius The radius of the cylinder's circular base.
 * @property height The height of the cylinder along the Y-axis.
 * @property segments The number of segments used to approximate the circular base of the cylinder.
 *                    More segments result in a smoother cylinder but more vertices.
 * @param vncImageAspectRatio The aspect ratio (width/height) of the VNC image to be projected.
 */
class CylindricalSurface(
    val radius: Float = 2f,
    val height: Float = 1f,
    private val vncImageAspectRatio: Float, // Added, private as only used in init
    val segments: Int = 32
) : ProjectedSurface {

    private val vertices: FloatBuffer
    private val textureCoordinates: FloatBuffer
    private val normals: FloatBuffer
    private val indices: ShortArray // Uses indexed drawing

    init {
        val vertexData = mutableListOf<Float>()
        val textureData = mutableListOf<Float>()
        val normalData = mutableListOf<Float>()
        val indexData = mutableListOf<Short>()

        val halfHeight = this.height / 2f

        // Calculate the angle span of the cylinder segment to match the VNC image's aspect ratio
        val targetArcWidth = this.vncImageAspectRatio * this.height
        var calculatedAngleSpan = targetArcWidth / this.radius.coerceAtLeast(0.001f) // Avoid division by zero if radius is tiny

        // Clamp angleSpan, e.g., from a small angle up to 2*PI (360 degrees)
        calculatedAngleSpan = calculatedAngleSpan.coerceIn(0.1f, (2 * kotlin.math.PI).toFloat())

        val startAngle = -calculatedAngleSpan / 2f // Center the arc (e.g., around Z+ or X+ depending on convention)
                                                 // Let's assume centered around X-axis (0 radians in XZ plane)
                                                 // So, if angle = 0 is along +X, this centers it.
        val angleStep = calculatedAngleSpan / this.segments.toFloat()


        // Generate vertices, texture coordinates, and normals
        for (i in 0..this.segments) { // Iterate one more time for the last edge to close the shape if angleSpan is 2*PI
            val angle = startAngle + i * angleStep
            val x = this.radius * kotlin.math.cos(angle)
            val z = this.radius * kotlin.math.sin(angle) // Cylinder along Y-axis, so XZ forms the circle

            // Bottom vertex
            vertexData.addAll(listOf(x, -halfHeight, z))
            // Texture U-coordinate goes from 0 to 1 across the defined segments
            textureData.addAll(listOf(i.toFloat() / this.segments.toFloat(), 1f)) // V = 1 (bottom of texture)
            normalData.addAll(listOf(kotlin.math.cos(angle), 0f, kotlin.math.sin(angle))) // Normal points outwards from Y-axis

            // Top vertex for the current segment
            vertexData.addAll(listOf(x, halfHeight, z))
            textureData.addAll(listOf(i.toFloat() / this.segments.toFloat(), 0f)) // V = 0 (top of texture)
            normalData.addAll(listOf(kotlin.math.cos(angle), 0f, kotlin.math.sin(angle))) // Normal is the same for top and bottom
        }

        vertices = ByteBuffer.allocateDirect(vertexData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertexData.toFloatArray())
                position(0)
            }

        textureCoordinates = ByteBuffer.allocateDirect(textureData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(textureData.toFloatArray())
                position(0)
            }

        normals = ByteBuffer.allocateDirect(normalData.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(normalData.toFloatArray())
                position(0)
            }

        // Generate indices for drawing the cylinder wall using triangles.
        // Each segment of the cylinder forms a quad, which is drawn as two triangles.
        // Vertices are ordered: bottom0, top0, bottom1, top1, bottom2, top2, ...
        for (i in 0 until segments) {
            val currentBottom = (i * 2).toShort()      // Index of bottom vertex of current segment
            val currentTop = (i * 2 + 1).toShort()    // Index of top vertex of current segment
            val nextBottom = ((i + 1) * 2).toShort()  // Index of bottom vertex of next segment
            val nextTop = ((i + 1) * 2 + 1).toShort()    // Index of top vertex of next segment

            // First triangle of the quad: (currentBottom, nextBottom, currentTop)
            indexData.add(currentBottom)
            indexData.add(nextBottom)
            indexData.add(currentTop)

            // Second triangle of the quad: (currentTop, nextBottom, nextTop)
            indexData.add(currentTop)
            indexData.add(nextBottom)
            indexData.add(nextTop)
        }
        indices = indexData.toShortArray()
    }

    /** Returns the buffer containing vertex coordinates (X, Y, Z). */
    override fun getVertices(): FloatBuffer = vertices

    /** Returns the buffer containing texture coordinates (U, V). */
    override fun getTextureCoordinates(): FloatBuffer = textureCoordinates

    /** Returns the buffer containing normal vectors (Nx, Ny, Nz). */
    override fun getNormals(): FloatBuffer = normals

    /** Returns the array of short indices for indexed drawing. */
    override fun getIndices(): ShortArray = indices
}
