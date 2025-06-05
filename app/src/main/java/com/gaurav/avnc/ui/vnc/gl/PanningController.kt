package com.gaurav.avnc.ui.vnc.gl

/**
 * Manages camera panning operations by delegating to a [PanningStrategy].
 * This allows for interchangeable panning behaviors (e.g., rotation, surface-offset panning).
 *
 * @property camera The [Camera] instance to be controlled.
 * @property strategy The initial [PanningStrategy] to be used.
 */
class PanningController(
    private val camera: Camera,
    private var strategy: PanningStrategy
) {
    /**
     * Stores a reference to the current [ProjectedSurface].
     * Some strategies, like [OffsetSurfacePanningStrategy], may use this
     * to adapt their behavior or initialize themselves based on surface properties.
     */
    private var projectedSurface: ProjectedSurface? = null

    /**
     * Changes the current panning strategy.
     * If the new strategy is an [OffsetSurfacePanningStrategy] and a surface is already set,
     * it will initialize the strategy with the current camera and surface.
     *
     * @param newStrategy The new [PanningStrategy] to use.
     */
    fun setStrategy(newStrategy: PanningStrategy) {
        this.strategy = newStrategy
        // If the new strategy requires initialization with the surface, do it now.
        if (newStrategy is OffsetSurfacePanningStrategy && projectedSurface != null) {
            newStrategy.initialize(camera, projectedSurface!!)
        }
    }

    /**
     * Sets or updates the [ProjectedSurface] that the camera is interacting with.
     * If the current strategy is an [OffsetSurfacePanningStrategy], it will be
     * (re)initialized with the new surface.
     *
     * @param surface The [ProjectedSurface] to be used by panning strategies.
     */
    fun setSurface(surface: ProjectedSurface) {
        this.projectedSurface = surface
        // If the current strategy needs to be (re)initialized with surface info.
         if (strategy is OffsetSurfacePanningStrategy) {
            (strategy as OffsetSurfacePanningStrategy).initialize(camera, surface)
        }
    }

    /**
     * Applies panning to the camera using the current strategy.
     *
     * @param deltaYaw Change in yaw (e.g., from horizontal swipe).
     * @param deltaPitch Change in pitch (e.g., from vertical swipe).
     * @param deltaRoll Change in roll (e.g., from a two-finger twist gesture, though not yet implemented). Defaults to 0f.
     */
    fun onPan(deltaYaw: Float, deltaPitch: Float, deltaRoll: Float = 0f) {
        strategy.pan(camera, deltaYaw, deltaPitch, deltaRoll)
    }

    /**
     * @return The currently active [PanningStrategy].
     */
    fun getCurrentStrategy(): PanningStrategy = strategy
}
