package com.gaurav.avnc.ui.vnc.xr

import android.app.Activity // Add this import
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.gaurav.avnc.util.AppPreferences // Added import
import com.gaurav.avnc.ui.vnc.PanningInputDevice
import com.gaurav.avnc.ui.vnc.PanningListener
import kotlin.math.abs

class PhoneImuPanningInputDevice(private val activity: Activity) : PanningInputDevice, SensorEventListener {

    private val TAG = "PhoneImuPanningDevice"
    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var listener: PanningListener? = null
    private var enabled = false
    private val uiHandler = Handler(Looper.getMainLooper())

    // Orientation arrays
    private val rotationMatrix = FloatArray(9)
    private val adjustedRotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Previous orientation values for calculating deltas
    private var lastYaw: Float? = null
    private var lastPitch: Float? = null

    // Timestamp of the last sensor event to calculate delta time for gyroscope integration (optional)
    // private var timestamp: Long = 0
    // private val NS2S = 1.0f / 1000_000_000.0f // nanoseconds to seconds

    // Sensitivity factor - adjust as needed
    // private val SENSITIVITY = 0.5f // Remove this line
    private lateinit var prefs: AppPreferences // Added field

    init {
        prefs = AppPreferences(activity.applicationContext) // Initialize prefs
        try {
            sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            // Prefer ROTATION_VECTOR sensor as it's usually more stable and already fused
            rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationVectorSensor == null) {
                Log.w(TAG, "Rotation Vector sensor not available. Gyroscope might be an alternative but requires manual integration and drift compensation.")
                // As a fallback, one could use Sensor.TYPE_GYROSCOPE and integrate deltas,
                // but that's more complex due to drift. For now, we'll rely on TYPE_ROTATION_VECTOR.
            } else {
                Log.i(TAG, "Phone IMU (Rotation Vector) initialized.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Phone IMU SensorManager initialization: ${e.message}", e)
            sensorManager = null
            rotationVectorSensor = null
        }
    }

    override fun setPanningListener(listener: PanningListener?) {
        this.listener = listener
    }

    override fun enable() {
        if (sensorManager == null || rotationVectorSensor == null) {
            Log.w(TAG, "Cannot enable: SensorManager or Rotation Vector sensor not available.")
            return
        }
        if (enabled) return

        // SENSOR_DELAY_GAME is a common choice for responsiveness.
        sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        enabled = true
        lastYaw = null // Reset last known orientation
        lastPitch = null
        // timestamp = 0L
        Log.i(TAG, "Phone IMU (Rotation Vector) enabled.")
    }

    override fun disable() {
        if (sensorManager == null || rotationVectorSensor == null) {
            Log.w(TAG, "Cannot disable: SensorManager or Rotation Vector sensor not available or not initialized.")
            // No return, try to unregister still
        }
        if (!enabled && sensorManager == null) { // if not enabled and no sensor manager, nothing to do
            return
        }

        sensorManager?.unregisterListener(this, rotationVectorSensor)
        enabled = false
        Log.i(TAG, "Phone IMU (Rotation Vector) disabled.")
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can be used to monitor sensor accuracy if needed
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} to $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || !enabled) {
            return
        }

        // Get rotation matrix from the rotation vector
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap coordinate system based on device natural orientation and current display rotation
        // This is crucial for consistent panning regardless of how the phone is held or screen orientation.
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        // Parameters for remapCoordinateSystem. AXIS_X and AXIS_Z are common for landscape.
        // This might need adjustment based on the app's fixed orientation or user preference.
        // Defaulting to a common remapping for landscape or natural orientation.
        // This part can be tricky and might need specific adjustments for your app's use case.
        var xAxis = SensorManager.AXIS_X
        var yAxis = SensorManager.AXIS_Z

        when (displayRotation) {
            Surface.ROTATION_0 -> { // Normal portrait
                xAxis = SensorManager.AXIS_X
                yAxis = SensorManager.AXIS_Y
            }
            Surface.ROTATION_90 -> { // Landscape, rotated left
                xAxis = SensorManager.AXIS_Y
                yAxis = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> { // Upside down portrait
                xAxis = SensorManager.AXIS_MINUS_X
                yAxis = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> { // Landscape, rotated right
                xAxis = SensorManager.AXIS_MINUS_Y
                yAxis = SensorManager.AXIS_X
            }
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, xAxis, yAxis, adjustedRotationMatrix)

        // Get orientation angles from the remapped rotation matrix
        SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles)

        // orientationAngles[0] is yaw (azimuth), orientationAngles[1] is pitch, orientationAngles[2] is roll
        // Values are in radians.
        val currentYaw = orientationAngles[0]   // Yaw around Z axis (after remapping)
        val currentPitch = orientationAngles[1] // Pitch around X axis (after remapping)
        // val currentRoll = orientationAngles[2] // Roll around Y axis (after remapping) - not typically used for direct panning control

        if (lastYaw == null || lastPitch == null) {
            lastYaw = currentYaw
            lastPitch = currentPitch
            return
        }

        var deltaYaw = currentYaw - lastYaw!!
        var deltaPitch = currentPitch - lastPitch!!

        // Handle wrap-around for yaw (azimuth). Radians from -PI to PI.
        if (deltaYaw > Math.PI) deltaYaw -= (2 * Math.PI).toFloat()
        if (deltaYaw < -Math.PI) deltaYaw += (2 * Math.PI).toFloat()

        // Pitch is typically -PI/2 to PI/2, less likely to wrap around dramatically in normal use.

        lastYaw = currentYaw
        lastPitch = currentPitch

        // Convert radians to degrees or apply a sensitivity factor
        // The PanningController likely expects changes in screen pixels or degrees.
        // Let's assume the listener expects something proportional to screen movement.
        // Negative deltaYaw for panning right (counter-intuitive but common in sensor to graphics mapping)
        // Negative deltaPitch for panning up (also counter-intuitive)
        // This depends heavily on the PanningController's expected input.
        // For consistency with Viture (where we negated deltaYaw from SDK), let's try:
        // Pan right: positive deltaYaw from this calculation (e.g. phone rotates left)
        // Pan up: positive deltaPitch from this calculation (e.g. phone tilts down)
        // This needs to be tested and adjusted.

        // Let's apply sensitivity and consider the direction.
        // If rotating phone to the right (yaw decreases for typical sensor coordinate systems), we want to pan right.
        // So, deltaYaw from (current - last) will be negative. We need to flip it.
        // If tilting phone upwards (pitch increases for typical sensor coordinate systems), we want to pan up.
        // So, deltaPitch from (current - last) will be positive.
        // This is very dependent on the remapping and interpretation.
        // A common approach:
        // Yaw: Positive rotation around Z axis (device pointing up) is yaw.
        // Pitch: Positive rotation around X axis (device pointing right) is pitch.
        // If user rotates phone to their right (clockwise from top view), yaw value might decrease. deltaYaw is negative. To pan right, send positive.
        // If user tilts phone top edge towards them (pitch up), pitch value might increase. deltaPitch is positive. To pan up, send positive.

        // Let's use a simpler approach: directly use the delta but allow PanningController to interpret/scale
        // The `FrameView.panFrameRenderer(deltaX, deltaY)` seems to take pixel-like values.
        // We need a scaling factor from radians to "pixels".
        // A small radian change should result in a noticeable pan.
        // Let's assume 1 radian = N pixels of pan.
        // The Viture example used xStep = 1920f / 64.0f. This means 64 degrees FOV maps to 1920 pixels.
        // 1 degree = (1920/64) pixels. 1 radian = (180/PI) * (1920/64) pixels.

        // Fetch sensitivities from AppPreferences
        val sensitivityX = prefs.xr.phoneImuDeltaPanSensitivityX
        val sensitivityY = prefs.xr.phoneImuDeltaPanSensitivityY

        val radToPanScale = (180.0f / Math.PI.toFloat()) * (1920f / 90f) // Assume 90 deg FOV for phone panning sensitivity

        val panX = -deltaYaw * radToPanScale * sensitivityX // Apply sensitivityX
        val panY = deltaPitch * radToPanScale * sensitivityY  // Apply sensitivityY

        // Filter out very small movements (jitter)
        if (abs(panX) < 0.1f && abs(panY) < 0.1f) {
            return
        }

        listener?.onPan(panX, panY)
    }
}
