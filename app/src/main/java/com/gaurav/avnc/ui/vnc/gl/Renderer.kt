/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.opengl.GLES20.GL_COLOR_BUFFER_BIT
import android.opengl.GLES20.glClear
import android.opengl.GLES20.glClearColor
import android.opengl.GLES20.glViewport
import android.opengl.GLSurfaceView
import android.opengl.Matrix // Import Matrix for length calculation
// import android.opengl.Matrix // Matrix operations are now primarily handled by Camera and PanningStrategy classes
import android.util.Log
import com.gaurav.avnc.viewmodel.VncViewModel
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer implementation for rendering the VNC content in 3D space.
 * This class manages the Camera, ProjectedSurface (the geometry being rendered),
 * shader programs (via FrameProgram), and the PanningController for camera manipulation.
 */
class Renderer(val viewModel: VncViewModel) : GLSurfaceView.Renderer {

    private val camera = Camera() // Manages view and projection matrices.
    private val hideCursor = viewModel.pref.input.hideRemoteCursor // VNC specific preference.
    private lateinit var program: FrameProgram // Handles GLSL shader program.
    private lateinit var frame: Frame // Handles VBOs/EBOs and draw calls for the surface.
    private lateinit var surface: ProjectedSurface // The geometric surface to be rendered.
    private lateinit var panningController: PanningController // Handles camera panning logic.

    // Removed: private val useCylindricalSurface = false (now driven by preferences)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(0f, 0f, 0f, 1f) // Set background color to black.

        program = FrameProgram() // Initialize shader program.
        frame = Frame() // Initialize frame object that will hold geometry buffers.

        // Initialize PanningController with the strategy from preferences
        // This must be done before resetCameraAndSurface, as that method might use the controller/strategy
        val initialXrPrefs = viewModel.pref.xr
        val initialPanningStrategy = if (initialXrPrefs.panningMode == "offset_surface") {
            OffsetSurfacePanningStrategy()
        } else { // "rotation" or any other default
            RotationPanningStrategy()
        }
        panningController = PanningController(camera, initialPanningStrategy)

        // resetCameraAndSurface will create the surface, bind it, set it on panningController,
        // and then position the camera relative to it.
        resetCameraAndSurface() // Uses current preferences via viewModel.pref.xr
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        // Update camera's projection matrix whenever the surface size changes.
        camera.updateProjectionMatrix(width, height)
    }

    /**
     * Called to draw the current frame.
     * This method is responsible for updating camera matrices, setting shader uniforms,
     * and issuing the draw call for the surface.
     *
     * The original lengthy comment about Y-axis inversion for 2D frame rendering
     * is less relevant now as we are in a 3D perspective view, but the underlying
     * VNC framebuffer texture might still have its coordinates originate from top-left.
     * This is handled by the texture coordinates provided by the ProjectedSurface implementations
     * (e.g., FlatSurface maps texture appropriately for a quad).
     */
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT) // Clear the color buffer.

        // Update the camera's view matrix. This is crucial if camera position/orientation
        // has been changed by panning or other interactions.
        camera.updateViewMatrix()

        // TODO: Implement Model Matrix transformation if surfaces need to be moved/rotated independently.
        // For now, the surface is assumed to be at the world origin, so Model Matrix is identity.
        // If a model matrix is added, it should be passed to the shader program.
        // Example: val modelMatrix = FloatArray(16); Matrix.setIdentityM(modelMatrix, 0);
        // And program.setUniforms would take modelMatrix, viewMatrix, projectionMatrix.
        // The shader would then calculate: u_Projection * u_ViewMatrix * u_ModelMatrix * a_Position;

        program.useProgram() // Activate the shader program.
        // Set the shader uniforms, including the view and projection matrices from the camera.
        program.setUniforms(camera.getViewMatrix(), camera.getProjectionMatrix())

        // The VNC-specific texture upload logic (viewModel.client.uploadFrameTexture())
        // is currently outside this simplified rendering path. If the ProjectedSurface
        // is intended to display the VNC content, that texture would need to be
        // uploaded to `program.textureId` (or a new texture ID managed by Frame/FrameProgram)
        // and the fragment shader would sample from it.

        // Re-enable VNC texture uploads for testing their impact on rendering state.
        // Even with simplified shaders not using these textures, this can help identify
        // if the upload process itself causes GL errors or state corruption.
        viewModel.client.uploadFrameTexture()
        if (!hideCursor) viewModel.client.uploadCursor()


        // For now, this renders the geometric shape (FlatSurface or CylindricalSurface)
        // which will be textured with whatever default texture `FrameProgram` has (likely blank or simple),
        // or in this case, rendered with a solid green color due to simplified fragment shader.

        program.validate() // Validate the shader program (good for debugging).
        frame.draw() // Issue the draw call for the surface (uses VBOs/EBOs).
    }

    /**
     * Pans the camera based on delta values for yaw, pitch, and roll.
     * This method is typically called from VncActivity in response to touch events
     * processed by TouchHandler and propagated via VncViewModel.
     *
     * @param deltaYaw Change in yaw (rotation around the camera's Y-axis or world Y-axis, depending on strategy).
     * @param deltaPitch Change in pitch (rotation around the camera's X-axis).
     * @param deltaRoll Change in roll (rotation around the camera's Z-axis/view direction).
     */
    fun panCamera(deltaYaw: Float, deltaPitch: Float, deltaRoll: Float = 0f) {
        if (!this::panningController.isInitialized) {
            Log.w("Renderer", "panCamera called before panningController is initialized. Ignoring pan event.")
            return
        }
        panningController.onPan(deltaYaw, deltaPitch, deltaRoll)
        // After panning, request a new render to display the updated view.
        // GLSurfaceView.requestRender() should be called if renderMode is RENDERMODE_WHEN_DIRTY.
        // This is handled by frameViewRef.get()?.requestRender() in VncViewModel or similar.
        // For this Renderer class, we assume the requestRender call happens externally if needed.
        // If viewModel is available and has a direct way: viewModel.frameViewRef.get()?.requestRender()
    }

    /**
     * Zooms the camera by moving it along its forward vector.
     * @param deltaZ The amount to move. A negative value (from pinch-out) moves the camera closer (zooms in),
     *               a positive value (from pinch-in) moves it further away (zooms out).
     */
    fun zoomCamera(deltaZ: Float) { // deltaZ from (1-scaleFactor)*sensitivity, negative means zoom IN
        if (!this::panningController.isInitialized) {
            Log.w("Renderer", "zoomCamera called before panningController is initialized. Ignoring zoom event.")
            return
        }
        val strategy = panningController.getCurrentStrategy()
        // Tunable factor for how much deltaZ affects zoomLevel.
        // Smaller = less sensitive zoom.
        val zoomLevelSensitivityFactor = 0.25f

        if (strategy is OffsetSurfacePanningStrategy) {
            // For OffsetSurfacePanningStrategy, pinch gesture modifies camera.zoomLevel.
            // deltaZ is (1 - scaleFactor) * touchSensitivity.
            // If scaleFactor > 1 (pinch out, zoom in), deltaZ is negative.
            // We want zoomLevel to increase when zooming in (scaleFactor > 1 => deltaZ < 0).
            // So, camera.zoomLevel -= deltaZ * someFactor (e.g. a factor to scale deltaZ effect appropriately)
            camera.zoomLevel -= deltaZ * zoomLevelSensitivityFactor // zoomLevel increases if deltaZ is negative

            // Force the strategy to re-evaluate and apply camera position based on the new zoomLevel.
            // This is done by calling pan with no angular/height changes, relying on pan()
            // to use the new camera.zoomLevel to calculate the offset.
            strategy.pan(camera, 0f, 0f, 0f)
        } else {
            // For other strategies like RotationPanningStrategy, direct camera move along forward vector is fine.
            val forwardX = camera.lookAtX - camera.positionX
            val forwardY = camera.lookAtY - camera.positionY
            val forwardZ = camera.lookAtZ - camera.positionZ
            val len = Matrix.length(forwardX, forwardY, forwardZ)
            if (len == 0f) return // Avoid division by zero

            val normForwardX = forwardX / len
            val normForwardY = forwardY / len
            val normForwardZ = forwardZ / len

            // deltaZ is (1-scaleFactor)*sensitivity.
            // Negative deltaZ (pinch out, zoom in) should move camera position FORWARD.
            camera.positionX -= normForwardX * deltaZ
            camera.positionY -= normForwardY * deltaZ
            camera.positionZ -= normForwardZ * deltaZ
        }
        // Note: PanningStrategy.pan() or the direct position change above should call camera.updateViewMatrix().
        // If not, call it here. OffsetSurfacePanningStrategy.pan() does. Rotation strategy doesn't have pan called here.
        // For RotationStrategy, the direct position change requires updateViewMatrix().
        if (strategy !is OffsetSurfacePanningStrategy) {
            camera.updateViewMatrix()
        }
        // VncActivity will call requestRender after this.
    }

    /**
     * Resets the camera to a default position/orientation suitable for the current or specified
     * display mode and re-creates the projection surface if needed.
     * This is useful when display settings change or a manual reset is requested.
     *
     * @param newDisplayMode Optional. If provided (e.g., "flat", "cylindrical"), the surface
     *                       will be recreated to this type, and camera reset for it.
     *                       If null, uses the current preference for displayMode to reset camera
     *                       and potentially recreate surface if it's not of the preferred type.
     */
    fun resetCameraAndSurface(newDisplayMode: String? = null) {
        val xrPrefs = viewModel.pref.xr
        // Determine the display mode to use: either the new one passed in, or the current preference.
        val targetDisplayMode = newDisplayMode ?: xrPrefs.displayMode

        // Part 1: Re-create surface if needed
        // This flag helps decide later if OffsetSurfacePanningStrategy's initialize needs explicit call
        var surfaceNeedsBinding = false
        if (newDisplayMode != null || // If a mode is forced
            !this::surface.isInitialized || // If surface doesn't exist yet
            (targetDisplayMode == "cylindrical" && surface !is CylindricalSurface) || // If mode is cylindrical but surface is flat
            (targetDisplayMode == "flat" && surface !is FlatSurface) || // If mode is flat but surface is cylindrical
            (targetDisplayMode == "cylindrical" && (surface as CylindricalSurface).radius != xrPrefs.cylinderRadius) // If cylinder radius changed
           ) {

            val frameStateSnapshot = viewModel.frameState.getSnapshot()
            val fbW = if (frameStateSnapshot.fbWidth > 0f) frameStateSnapshot.fbWidth else 1920f
            val fbH = if (frameStateSnapshot.fbHeight > 0f) frameStateSnapshot.fbHeight else 1080f
            val aspectRatio = if (fbH > 0f) fbW / fbH else 16f / 9f
            val calculatedHeight = 2f / aspectRatio

            if (targetDisplayMode == "cylindrical") {
                val radius = xrPrefs.cylinderRadius
                surface = CylindricalSurface(
                    radius = radius,
                    height = calculatedHeight,
                    vncImageAspectRatio = aspectRatio, // Pass the calculated aspect ratio
                    segments = 32
                )
            } else {
                surface = FlatSurface(vncFbWidth = fbW, vncFbHeight = fbH, targetDisplayWidth = 2f)
            }
            surfaceNeedsBinding = true
        }

        // Ensure program and frame are initialized (they should be from onSurfaceCreated)
        if (this::program.isInitialized && this::frame.isInitialized) {
            frame.bind(program, surface)
        }

        // Part 2: Update Panning Strategy (must be done *after* surface is stable and *before* camera reset that might use strategy props)
        // PanningController itself should already be initialized from onSurfaceCreated.
        val currentPanningModePref = xrPrefs.panningMode
        val newStrategyInstance = if (currentPanningModePref == "offset_surface") {
            OffsetSurfacePanningStrategy()
        } else {
            RotationPanningStrategy()
        }
        panningController.setStrategy(newStrategyInstance) // Sets the new strategy
        panningController.setSurface(this.surface) // Crucially, re-initializes the strategy with the current surface & camera state

        // Part 3: Try to restore state. If not, set default camera position/orientation.
        val stateRestored = applySavedXrViewStateIfAvailable()

        if (!stateRestored) {
            Log.d("Renderer", "No state restored, setting default camera position.")
            // Original Part 3: Reset Camera Position and Orientation
            camera.zoomLevel = 1.0f // Reset zoom level first for default setup

            val surfaceToResetFor = this.surface
            var newCamX = 0f
            var newCamY = 0f
            var newCamZ = 5f // Default for flat
            var newLookAtX = 0f
            var newLookAtY = 0f
            var newLookAtZ = 0f

            if (surfaceToResetFor is CylindricalSurface) {
                val actualCylinderRadius = surfaceToResetFor.radius.coerceAtLeast(0.1f)
                val defaultInitialOffset = 1.0f
                val offsetToUse = defaultInitialOffset.coerceIn(0.1f, actualCylinderRadius - 0.05f)
                val effectiveRadius = (actualCylinderRadius - offsetToUse).coerceAtLeast(0.1f)
                val angle = 0f
                newCamX = effectiveRadius * kotlin.math.cos(angle)
                newCamY = 0f
                newCamZ = effectiveRadius * kotlin.math.sin(angle)
                newLookAtX = actualCylinderRadius * kotlin.math.cos(angle)
                newLookAtY = 0f
                newLookAtZ = actualCylinderRadius * kotlin.math.sin(angle)
            }

            camera.positionX = newCamX
            camera.positionY = newCamY
            camera.positionZ = newCamZ
            camera.lookAtX = newLookAtX
            camera.lookAtY = newLookAtY
            camera.lookAtZ = newLookAtZ
            camera.upX = 0f
            camera.upY = 1f
            camera.upZ = 0f
            camera.updateViewMatrix()

            // Sync strategy state for default setup
            val currentStrategy = panningController.getCurrentStrategy()
            if (currentStrategy is OffsetSurfacePanningStrategy) {
                currentStrategy.syncStateFromCamera(this.camera)
            }
        } else {
            Log.d("Renderer", "State was restored, default camera setup skipped.")
            // If state was restored, camera.updateViewMatrix() was already called in applySavedXrViewStateIfAvailable.
            // Panning strategy state (like cameraAngleY for OffsetSurfacePanningStrategy) was also handled there.
        }
    }

    private fun applySavedXrViewStateIfAvailable(): Boolean {
        val cameraState = viewModel.savedCameraState
        val panningState = viewModel.savedPanningState

        if (cameraState != null && panningState != null) {
            Log.d("Renderer", "Attempting to restore XR View State.")
            // Restore camera properties
            camera.positionX = cameraState.position[0]
            camera.positionY = cameraState.position[1]
            camera.positionZ = cameraState.position[2]

            camera.lookAtX = cameraState.lookAt[0]
            camera.lookAtY = cameraState.lookAt[1]
            camera.lookAtZ = cameraState.lookAt[2]

            camera.upX = cameraState.up[0]
            camera.upY = cameraState.up[1]
            camera.upZ = cameraState.up[2]

            camera.zoomLevel = cameraState.zoomLevel

            // Restore panning strategy state
            val currentStrategy = panningController.getCurrentStrategy()
            val currentStrategyMode = if (currentStrategy is OffsetSurfacePanningStrategy) "offset_surface" else "rotation"

            if (panningState.panningMode == currentStrategyMode) {
                if (currentStrategy is OffsetSurfacePanningStrategy) {
                    panningState.offsetStrategyAngleY?.let { currentStrategy.cameraAngleY = it }
                    panningState.offsetStrategyHeightY?.let { currentStrategy.cameraHeightY = it }
                    // After setting strategy state, call pan with no deltas to update camera position
                    // based on the restored strategy state, current surface, and zoom.
                    currentStrategy.pan(camera, 0f, 0f, 0f)
                    Log.d("Renderer", "Restored OffsetSurfacePanningStrategy state and called pan.")
                } else {
                    // For RotationPanningStrategy, just updating camera matrices is enough
                    // as it's stateless beyond the camera itself.
                    Log.d("Renderer", "Restored state for RotationPanningStrategy (camera only).")
                }
            } else {
                Log.w("Renderer", "Mismatch in panning strategy mode during restore. Saved: ${panningState.panningMode}, Current: $currentStrategyMode. Skipping strategy state restore.")
                // If modes mismatch, we've restored camera, but strategy state might be off.
                // The subsequent default camera setup might partially override this.
                // Or, we could choose to not restore camera either if strategy is critical and mismatched.
                // For now, camera is restored, strategy specific state is skipped on mismatch.
            }

            camera.updateViewMatrix() // Ensure view matrix is updated with all restored properties
            Log.d("Renderer", "XR View State Restored. Camera: $cameraState, Panning: $panningState")

            viewModel.clearSavedXrViewState() // Consume the state
            return true
        }
        Log.d("Renderer", "No XR View State to restore.")
        return false
    }

    fun getCurrentCameraState(): CameraStateData {
        return CameraStateData(
            position = floatArrayOf(camera.positionX, camera.positionY, camera.positionZ),
            lookAt = floatArrayOf(camera.lookAtX, camera.lookAtY, camera.lookAtZ),
            up = floatArrayOf(camera.upX, camera.upY, camera.upZ),
            zoomLevel = camera.zoomLevel
        )
    }

    fun getCurrentPanningStrategyState(): PanningStrategyStateData {
        val currentStrategy = panningController.getCurrentStrategy()
        val mode = if (currentStrategy is OffsetSurfacePanningStrategy) "offset_surface" else "rotation"
        var angleY: Float? = null
        var heightY: Float? = null
        if (currentStrategy is OffsetSurfacePanningStrategy) {
            // Need to expose these from OffsetSurfacePanningStrategy or have methods to get them
            // For now, assume OffsetSurfacePanningStrategy has public getters or properties for these
            angleY = currentStrategy.cameraAngleY // Assuming public access
            heightY = currentStrategy.cameraHeightY // Assuming public access
        }
        return PanningStrategyStateData(
            panningMode = mode,
            offsetStrategyAngleY = angleY,
            offsetStrategyHeightY = heightY
        )
    }
}