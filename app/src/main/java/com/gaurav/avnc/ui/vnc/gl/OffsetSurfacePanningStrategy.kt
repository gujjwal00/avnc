package com.gaurav.avnc.ui.vnc.gl

import android.opengl.Matrix

/**
 * A [PanningStrategy] that moves the camera as if it's sliding over an offset surface
 * parallel to the main [ProjectedSurface]. The camera maintains a fixed distance
 * (offset) from the projected surface and orients itself to look towards it.
 *
 * **Note:** This implementation is currently simplified.
 * - It largely assumes a flat target surface (e.g., XY plane at Z=0) for its movement logic.
 * - True panning over arbitrary curved surfaces (like a cylinder or sphere) would require
 *   more complex calculations involving surface normals and geodesic paths.
 * - The camera's offset from the surface is currently fixed.
 *
 * @property movementSpeedFactor Factor to control how much the camera moves per unit of deltaYaw/deltaPitch (legacy, now internal).
 */
// Removed movementSpeed from constructor
class OffsetSurfacePanningStrategy : PanningStrategy {

    private var currentSurface: ProjectedSurface? = null
    private var isCylindrical: Boolean = false
    private var cylinderRadius: Float = 0f // Actual radius of the CylindricalSurface

    // TODO: This offset should ideally be dynamic (e.g., related to zoom level or surface properties).
    // Value of 0.5f might be too small if cylinder radius is large, or too large if radius is small.
    // Let's use a slightly larger default or make it proportional to radius/view.
    private val initialOffsetConstant = 1.0f // Renamed from cameraMovementSurfaceOffset

    // State for cylindrical panning
    internal var cameraAngleY: Float = 0f // Angle around Y-axis for camera on its movement cylinder (radians)
    internal var cameraHeightY: Float = 0f // Y position of camera

    // New base sensitivity, to be tuned. This factor aims to make panning speed
    // proportional to the camera's distance from the surface (dynamicOffset).
    private val basePanSensitivity = 0.005f
    // Old speed factors are removed:
    // private val flatPanningSpeed: Float = 0.005f
    // private val cylindricalAngularSpeed: Float = 0.002f
    // private val cylindricalHeightSpeed: Float = 0.005f


    init {
        // Initial target values are typically set by the `initialize` method when the
        // strategy is first applied or the surface changes.
    }

    /**
     * Initializes the strategy's internal state based on the
     * current camera state and the given surface.
     * This should be called when the strategy is first applied or when the target surface changes.
     *
     * @param camera The current camera.
     * @param surface The [ProjectedSurface] the camera will be interacting with.
     */
    fun initialize(camera: Camera, surface: ProjectedSurface) {
        this.currentSurface = surface
        if (surface is CylindricalSurface) {
            this.isCylindrical = true
            // Ensure cylinderRadius is positive; AppPreferences already coerces it to 1.0f-5.0f
            this.cylinderRadius = surface.radius.coerceAtLeast(0.1f)

            // Derive strategy's internal state from current camera state
            this.cameraHeightY = camera.positionY

            // Calculate the angle of the camera's current XZ position relative to the cylinder's center (0,0,0).
            // This assumes the camera is already somewhat positioned relative to the cylinder.
            // atan2 returns angle in radians from -PI to PI.
            this.cameraAngleY = kotlin.math.atan2(camera.positionZ, camera.positionX)
        } else {
            this.isCylindrical = false
            // For flat surfaces, the existing direct camera manipulation in pan() doesn't require
            // complex angle/height state like cylindrical.
            // The previous targetPositionX/Y/Z fields are removed as we now directly modify camera.
        }
        // Note: We do not modify camera position or call camera.updateViewMatrix() here.
        // The Renderer is responsible for initial camera setup. This method just syncs strategy state.
    }

    /**
     * Explicitly synchronizes the strategy's internal orientation state (angles, height)
     * from the provided camera object. This is useful after external camera resets or
     * programmatic camera movements to ensure the strategy's next pan operation
     * starts from the correct baseline.
     */
    fun syncStateFromCamera(camera: Camera) {
        if (isCylindrical) { // isCylindrical should have been set by initialize()
            this.cameraHeightY = camera.positionY
            // Ensure cylinderRadius is positive to avoid issues if currentSurface is not yet a valid cylinder
            // or if radius is zero (though coerceAtLeast(0.1f) in initialize should prevent exactly 0).
            if (this.cylinderRadius > 0f) {
                // Calculate angle based on camera's position relative to the cylinder's center (world 0,0,0).
                // This assumes the camera is on its correct offset orbit defined by (effectiveRadius * cos/sin(angle)).
                this.cameraAngleY = kotlin.math.atan2(camera.positionZ, camera.positionX)
            } else {
                // Default angle if radius is not properly set (should ideally not happen if initialize was called correctly)
                this.cameraAngleY = 0f
            }
        }
        // No specific angular/height state to sync for flat panning in this strategy version using this method,
        // as its panning is relative to current camera pos/lookAt.
    }

    override fun pan(camera: Camera, deltaYaw: Float, deltaPitch: Float, deltaRoll: Float) {
        if (isCylindrical) {
            // Cylindrical Panning Logic
            if (cylinderRadius <= 0f) return // Guard against zero or negative radius

            // Calculate dynamic offset based on zoom level
            // Coerce against cylinderRadius to prevent camera going through or too far if zoom is extreme.
            val dynamicOffset = (initialOffsetConstant / camera.zoomLevel).coerceIn(0.1f, cylinderRadius - 0.05f)

            // Calculate desired world space movement on the surface based on visual angle change and distance
            // deltaYaw from Dispatcher: positive for swipe Left.
            // deltaPitch from Dispatcher: positive for swipe Up.
            val dArcSurface = dynamicOffset * (deltaYaw * basePanSensitivity)
            val dHeightSurface = dynamicOffset * (deltaPitch * basePanSensitivity)

            // Convert surface movements to changes in cameraAngleY and cameraHeightY
            // For natural scroll (view moves with finger):
            // Swipe Left (positive deltaYaw) -> view moves left -> cameraAngleY should DECREASE (CW for view left if angle increases CCW).
            // Swipe Right (negative deltaYaw) -> view moves right -> cameraAngleY should INCREASE.
            // The dArcSurface is positive for swipe left (positive deltaYaw). To make view move left, camera should rotate right (angle decreases if CCW).
            // No, if angle is CCW from +X: Swipe Left (positive deltaYaw) -> dArcSurface positive. To move view left, camera moves left along arc. Angle increases.
            // The prompt asks to invert, so:
            cameraAngleY -= dArcSurface / cylinderRadius.coerceAtLeast(0.001f)
            // Swipe Up (positive deltaPitch) -> dHeightSurface positive. To move view up, camera moves up. cameraHeightY increases.
            // The prompt asks to invert, so:
            cameraHeightY -= dHeightSurface

            // Clamp cameraHeightY to cylinder height
            (currentSurface as? CylindricalSurface)?.height?.let { surfHeight ->
                 cameraHeightY = cameraHeightY.coerceIn(-surfHeight / 2f, surfHeight / 2f)
            }

            // Calculate the current viewing radius (camera's actual orbit radius from cylinder's Y-axis)
            val currentViewingRadius = (cylinderRadius - dynamicOffset).coerceAtLeast(0.1f)

            // Update camera position to orbit the cylinder
            camera.positionX = currentViewingRadius * kotlin.math.cos(cameraAngleY)
            camera.positionY = cameraHeightY
            camera.positionZ = currentViewingRadius * kotlin.math.sin(cameraAngleY)

            // Camera looks towards the surface of the main cylinder
            camera.lookAtX = cylinderRadius * kotlin.math.cos(cameraAngleY)
            camera.lookAtY = cameraHeightY // Look at the same height
            camera.lookAtZ = cylinderRadius * kotlin.math.sin(cameraAngleY)

        } else {
            // Flat Surface Panning Logic
            val dynamicOffset = (initialOffsetConstant / camera.zoomLevel).coerceIn(0.1f, 20.0f) // Max offset 20 for sanity
            val effectiveSensitivity = dynamicOffset * basePanSensitivity

            // deltaYaw from Dispatcher: positive for swipe Left. For natural scroll (view left), lookAtX decreases.
            val directionX = -1f
            camera.lookAtX += deltaYaw * effectiveSensitivity * directionX
            // deltaPitch from Dispatcher: positive for swipe Up. For natural scroll (view up), lookAtY increases.
            val directionY =  -1f
            camera.lookAtY += deltaPitch * effectiveSensitivity * directionY

            camera.lookAtZ = 0f // Keep lookAt point on the Z=0 plane (surface plane)

            // Position the camera directly in front of the new lookAt point, at the dynamicOffset distance.
            camera.positionX = camera.lookAtX
            camera.positionY = camera.lookAtY
            // Camera is positioned along the positive Z-axis relative to the Z=0 surface.
            camera.positionZ = dynamicOffset

            // Camera's up vector is handled by the common roll logic below.
            // For a simple perpendicular view, up is typically (0,1,0).
        }

        // Common Roll Logic (applied after position/lookAt updates for the current pan type)
        if (deltaRoll != 0f) {
            val currentViewDirX = camera.lookAtX - camera.positionX
            val currentViewDirY = camera.lookAtY - camera.positionY
            val currentViewDirZ = camera.lookAtZ - camera.positionZ

            val viewVectorLength = Matrix.length(currentViewDirX, currentViewDirY, currentViewDirZ)
            // Avoid division by zero or issues with very small vectors if position == lookAt
            if (viewVectorLength > 0.0001f) {
                val normFactor = 1.0f / viewVectorLength
                val axisX = currentViewDirX * normFactor
                val axisY = currentViewDirY * normFactor
                val axisZ = currentViewDirZ * normFactor

                val rollMatrix = FloatArray(16)
                Matrix.setIdentityM(rollMatrix, 0)
                Matrix.rotateM(rollMatrix, 0, deltaRoll, axisX, axisY, axisZ)

                val currentUpVec = floatArrayOf(camera.upX, camera.upY, camera.upZ, 0f)
                val newUpVec = FloatArray(4)
                Matrix.multiplyMV(newUpVec, 0, rollMatrix, 0, currentUpVec, 0)
                camera.upX = newUpVec[0]
                camera.upY = newUpVec[1]
                camera.upZ = newUpVec[2]
            }
        }

        // After all updates, recalculate the view matrix.
        camera.updateViewMatrix()
    }

    // getCameraMovementSurfaceOffset() removed as initialOffsetConstant is private now,
    // and dynamic offset is calculated internally based on zoomLevel.
}
