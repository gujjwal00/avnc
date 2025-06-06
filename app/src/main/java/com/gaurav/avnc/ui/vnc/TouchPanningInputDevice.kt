package com.gaurav.avnc.ui.vnc

import android.view.MotionEvent
import com.gaurav.avnc.util.AppPreferences

// Callback interface for TouchPanningInputDevice to communicate back to TouchHandler
interface PanningTouchHandlerCallback {
    fun onPanInitiated() // Called when a gesture that will lead to panning starts
    fun onPanEnded()     // Called when a panning gesture ends
}

class TouchPanningInputDevice(
        private val prefs: AppPreferences, // For accessing scroll mode, sensitivity, etc.
        private val callback: PanningTouchHandlerCallback // To signal back to the main TouchHandler
) : PanningInputDevice {

    private var panningListener: PanningListener? = null
    private var enabled = true // Touch panning is enabled by default

    private var isCurrentlyPanning = false // Internal state to track if a pan gesture is active

    // Scale factors from the original TouchHandler
    private val SCROLL_SCALE_FACTOR = 0.4f // For discrete scroll events (e.g., mouse wheel)
    private val DRAG_SCALE_FACTOR = 1.0f   // For touch drag/pan gestures

    override fun setPanningListener(listener: PanningListener?) {
        this.panningListener = listener
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
        if (isCurrentlyPanning) {
            // If a pan was in progress, ensure it's properly ended
            isCurrentlyPanning = false
            callback.onPanEnded()
        }
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    // --- Methods to be called by TouchHandler's GestureDetector ---

    fun onDown(event: MotionEvent): Boolean {
        if (!enabled) return false
        // Do not start isCurrentlyPanning here. onScroll will determine if it's a pan.
        // lastX and lastY are not needed if relying on distanceX/Y from onScroll.
        return true // Indicate event was handled if it could lead to panning
    }

    fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (!enabled || panningListener == null) return false

        // distanceX/Y is the delta since the last onScroll.
        // distanceX > 0 means finger moved right (content should move left / pan left)
        // distanceY > 0 means finger moved down (content should move up / pan up)

        // PanningController/ViewModel expects:
        // positive deltaX to pan right
        // positive deltaY to pan up
        // So, we need to invert the signs of distanceX and distanceY.
        val panDeltaX = -distanceX * DRAG_SCALE_FACTOR
        val panDeltaY = -distanceY * DRAG_SCALE_FACTOR

        if (!isCurrentlyPanning) {
            // Heuristic: if the gesture is primarily horizontal or vertical, treat as pan.
            // This avoids panning on slight diagonal movements during pinch-zoom, for example.
            // This logic might need to be more sophisticated depending on how pinch-zoom
            // and other gestures are handled in TouchHandler.
            // For now, any scroll when enabled will be treated as pan.
            isCurrentlyPanning = true
            callback.onPanInitiated()
        }

        panningListener?.onPan(panDeltaX, panDeltaY)
        return true // Event handled
    }

    // Method for discrete scroll events (e.g., mouse wheel when scrollMode is PAN_FRAME)
    fun processDiscreteScroll(scrollX: Float, scrollY: Float): Boolean {
        if (!enabled || panningListener == null) {
            return false
        }

        // scrollX > 0 means mouse wheel scrolled right (content should pan left)
        // scrollY > 0 means mouse wheel scrolled down (content should pan up)
        // So, invert signs for panning.
        val panDeltaX = -scrollX * SCROLL_SCALE_FACTOR
        val panDeltaY = -scrollY * SCROLL_SCALE_FACTOR

        panningListener?.onPan(panDeltaX, panDeltaY)
        return true
    }

    // Called by TouchHandler when the gesture ends (e.g., ACTION_UP)
    fun onGestureEnded() {
        if (isCurrentlyPanning) {
            isCurrentlyPanning = false
            callback.onPanEnded()
        }
    }

    // If onFling is to be supported for inertial panning in the future
    // fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
    //     if (!enabled || panningListener == null) return false
    //     // To implement fling, you'd typically start an animation here that calls
    //     // panningListener.onPan() repeatedly with decreasing deltas.
    //     // For now, fling ends the pan.
    //     if (isCurrentlyPanning) {
    //         isCurrentlyPanning = false
    //         callback.onPanEnded()
    //     }
    //     return true // If fling for panning is handled
    // }
}
