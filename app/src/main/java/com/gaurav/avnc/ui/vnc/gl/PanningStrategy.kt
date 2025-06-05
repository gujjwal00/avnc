package com.gaurav.avnc.ui.vnc.gl

/**
 * Interface for defining different camera panning behaviors.
 * Implementations of this interface can provide various ways to interpret
 * yaw, pitch, and roll changes to manipulate the camera.
 */
interface PanningStrategy {
    /**
     * Applies panning/rotation to the camera.
     *
     * @param camera The [Camera] instance to be manipulated.
     * @param deltaYaw The change in yaw (typically rotation around the Y-axis).
     * @param deltaPitch The change in pitch (typically rotation around the X-axis).
     * @param deltaRoll The change in roll (typically rotation around the Z-axis or view direction).
     */
    fun pan(camera: Camera, deltaYaw: Float, deltaPitch: Float, deltaRoll: Float)
}
