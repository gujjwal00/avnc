package com.gaurav.avnc.ui.vnc.gl

import android.opengl.Matrix

/**
 * A [PanningStrategy] that implements camera panning by rotating the camera's
 * look-at direction around its current position.
 * - Yaw rotation is applied around the world Y-axis.
 * - Pitch rotation is applied around the camera's local X-axis (right vector).
 * - Roll rotation is applied around the camera's local Z-axis (current view direction), affecting the 'up' vector.
 * This strategy is akin to looking around from a fixed point.
 */
class RotationPanningStrategy : PanningStrategy {
    override fun pan(camera: Camera, deltaYaw: Float, deltaPitch: Float, deltaRoll: Float) {
        // This strategy rotates the camera's view direction (lookAt point relative to position).
        // It's a common way to implement "look around" functionality.

        // Current camera state
        val currentLookAtX = camera.lookAtX
        val currentLookAtY = camera.lookAtY
        val currentLookAtZ = camera.lookAtZ
        val currentPositionX = camera.positionX
        val currentPositionY = camera.positionY
        val currentPositionZ = camera.positionZ

        // Calculate the current direction vector from camera position to the lookAt point.
        var dirX = currentLookAtX - currentPositionX
        var dirY = currentLookAtY - currentPositionY
        var dirZ = currentLookAtZ - currentPositionZ

        // Temporary arrays for matrix operations.
        val tempMatrix = FloatArray(16)
        val resultVec = FloatArray(4)
        val inputVec = FloatArray(4) { 0f }
        inputVec[3] = 0f // This represents a direction vector (w=0).

        // Apply Pitch: Rotation around the camera's local X-axis (right vector).
        if (deltaPitch != 0f) {
            // Calculate the camera's right vector (normalized cross product of view direction and up vector).
            // Note: Current dirX, dirY, dirZ is the view direction.
            val upX = camera.upX
            val upY = camera.upY
            val upZ = camera.upZ

            var rightX = upY * dirZ - upZ * dirY
            var rightY = upZ * dirX - upX * dirZ
            var rightZ = upX * dirY - upY * dirX
            val invLenRight = 1.0f / Matrix.length(rightX, rightY, rightZ)
            rightX *= invLenRight
            rightY *= invLenRight
            rightZ *= invLenRight

            Matrix.setIdentityM(tempMatrix, 0)
            Matrix.rotateM(tempMatrix, 0, deltaPitch, rightX, rightY, rightZ)
            inputVec[0] = dirX; inputVec[1] = dirY; inputVec[2] = dirZ
            Matrix.multiplyMV(resultVec, 0, tempMatrix, 0, inputVec, 0)
            dirX = resultVec[0]
            dirY = resultVec[1]
            dirZ = resultVec[2]
        }

        // Apply Yaw: Rotation around the world Y-axis (0, 1, 0).
        // This is a common simplification for yaw. Orbiting around camera's local Y might also be an option.
        if (deltaYaw != 0f) {
            Matrix.setIdentityM(tempMatrix, 0)
            Matrix.rotateM(tempMatrix, 0, deltaYaw, 0f, 1f, 0f) // Rotate around world Y-axis.
            inputVec[0] = dirX; inputVec[1] = dirY; inputVec[2] = dirZ
            Matrix.multiplyMV(resultVec, 0, tempMatrix, 0, inputVec, 0)
            dirX = resultVec[0]
            dirY = resultVec[1]
            dirZ = resultVec[2]
        }

        // Update the camera's lookAt point based on the new direction vector.
        camera.lookAtX = currentPositionX + dirX
        camera.lookAtY = currentPositionY + dirY
        camera.lookAtZ = currentPositionZ + dirZ

        // Apply Roll: Rotation around the camera's local Z-axis (the new view direction).
        // This primarily affects the camera's 'up' vector.
        if (deltaRoll != 0f) {
            // Normalize the new view direction (dirX, dirY, dirZ) to use as rotation axis for roll.
            val invLenDir = 1.0f / Matrix.length(dirX, dirY, dirZ)
            val rollAxisX = dirX * invLenDir
            val rollAxisY = dirY * invLenDir
            val rollAxisZ = dirZ * invLenDir

            Matrix.setIdentityM(tempMatrix, 0)
            Matrix.rotateM(tempMatrix, 0, deltaRoll, rollAxisX, rollAxisY, rollAxisZ)
            inputVec[0] = camera.upX; inputVec[1] = camera.upY; inputVec[2] = camera.upZ
            Matrix.multiplyMV(resultVec, 0, tempMatrix, 0, inputVec, 0)
            camera.upX = resultVec[0]
            camera.upY = resultVec[1]
            camera.upZ = resultVec[2]
        }

        // After all transformations, update the camera's view matrix.
        camera.updateViewMatrix()
    }
}
