package com.gaurav.avnc.ui.vnc

// Removed unused imports:
// import android.view.MotionEvent
// import com.gaurav.avnc.util.AppPreferences

// PanningTouchHandlerCallback interface removed

class TouchPanningInputDevice : PanningInputDevice {

    private var panningListener: PanningListener? = null
    private var enabled = false // Default to false, enabled by Dispatcher

    override fun setPanningListener(listener: PanningListener?) {
        this.panningListener = listener
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
        // No callback or internal state like isCurrentlyPanning to manage here
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    /**
     * Processes pan delta values received from an external source (e.g., TouchHandler).
     * If enabled and a listener is set, it forwards the pan event.
     *
     * @param deltaX The change in X-coordinate for panning.
     * @param deltaY The change in Y-coordinate for panning.
     */
    fun processPan(deltaX: Float, deltaY: Float) {
        if (!enabled || panningListener == null) {
            return
        }
        panningListener?.onPan(deltaX, deltaY)
    }
}
