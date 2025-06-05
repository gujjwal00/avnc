package com.gaurav.avnc.ui.vnc.gl

import android.opengl.Matrix

/**
 * Manages the camera's view and projection matrices for 3D rendering.
 * It allows setting camera position, look-at point, and up vector.
 */
class Camera {
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    /** The X coordinate of the camera's position. */
    var positionX = 0f
    /** The Y coordinate of the camera's position. */
    var positionY = 0f
    /** The Z coordinate of the camera's position. */
    var positionZ = 5f

    /** The X coordinate of the point the camera is looking at. */
    var lookAtX = 0f
    /** The Y coordinate of the point the camera is looking at. */
    var lookAtY = 0f
    /** The Z coordinate of the point the camera is looking at. */
    var lookAtZ = 0f

    /** The X component of the camera's up vector. */
    var upX = 0f
    /** The Y component of the camera's up vector. Typically 1.0 for no roll. */
    var upY = 1f
    /** The Z component of the camera's up vector. */
    var upZ = 0f

    /**
     * Represents the camera's zoom level. Larger values typically mean more zoomed in.
     * This value can be used by panning strategies (like OffsetSurfacePanningStrategy)
     * to adjust camera distance or field of view.
     * Coerced to be between 0.1f and 10.0f.
     */
    var zoomLevel: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.1f, 10.0f) // Example clamping, adjust as needed
        }

    private var aspectRatio = 1f

    init {
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
    }

    /**
     * Recalculates the view matrix based on the current position, look-at point, and up vector.
     * This should be called after modifying any of these camera properties.
     */
    fun updateViewMatrix() {
        Matrix.setLookAtM(viewMatrix, 0, positionX, positionY, positionZ, lookAtX, lookAtY, lookAtZ, upX, upY, upZ)
    }

    /**
     * Updates the projection matrix based on the provided screen dimensions and a fixed FOV.
     * @param width The width of the viewport.
     * @param height The height of the viewport.
     */
    fun updateProjectionMatrix(width: Int, height: Int) {
        aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspectRatio, 0.1f, 100f)
    }

    /**
     * @return The current view matrix.
     */
    fun getViewMatrix(): FloatArray {
        return viewMatrix
    }

    /**
     * @return The current projection matrix.
     */
    fun getProjectionMatrix(): FloatArray {
        return projectionMatrix
    }
}
