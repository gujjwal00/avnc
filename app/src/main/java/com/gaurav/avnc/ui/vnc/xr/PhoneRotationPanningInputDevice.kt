package com.gaurav.avnc.ui.vnc.xr

import android.app.Activity
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
import kotlin.math.abs // Keep this for potential jitter filtering later

// Quaternion and Matrix utility functions might be needed here or in a separate util class.
// For now, we'll assume Android's SensorManager provides enough tools.

class PhoneRotationPanningInputDevice(private val activity: Activity) : PanningInputDevice, SensorEventListener {

    private val TAG = "PhoneRotationPanning"
    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null
    private var listener: PanningListener? = null
    private var enabled = false
    // private val uiHandler = Handler(Looper.getMainLooper()) // May not be needed if listener handles threading

    // --- Parameters for rotation to offset mapping ---
    // Neutral orientation (set when enabled)
    private val neutralRotationMatrix = FloatArray(9)
    private var neutralOrientationCaptured = false

    // Last sent total offsets to calculate deltas
    private var lastSentOffsetX = 0f
    private var lastSentOffsetY = 0f

    // Current rotation matrix and orientation angles
    private val currentRotationMatrix = FloatArray(9)
    private val remappedRotationMatrix = FloatArray(9) // For screen orientation adjustment
    private val currentOrientationAngles = FloatArray(3) // yaw, pitch, roll

    // Scaling factor: how many pan units per radian of phone rotation.
    // This will need careful tuning. Example: 1 radian of rotation = 500 pan units.
    // This effectively defines the "zoom" level of the magic window.
    private val ROTATION_TO_PAN_SCALE_FACTOR = 1200f // Approx maps +/- 45deg rotation to +/- screen_dimension_units (e.g. 1200px)

    private lateinit var prefs: AppPreferences // Added field

    init {
        prefs = AppPreferences(activity.applicationContext) // Initialize prefs
        try {
            sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationVectorSensor == null) {
                Log.w(TAG, "Rotation Vector sensor not available. This device cannot function.")
            } else {
                Log.i(TAG, "PhoneRotationPanningInputDevice initialized.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during SensorManager initialization: ${e.message}", e)
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

        // Attempt to capture neutral orientation immediately.
        // This requires a valid sensor reading. For simplicity, we'll try to get one.
        // A more robust way might be to wait for the first few sensor readings to stabilize.
        neutralOrientationCaptured = false // Reset flag
        lastSentOffsetX = 0f
        lastSentOffsetY = 0f

        // Get an initial sensor reading to set the neutral orientation.
        // This is tricky because registerListener is async.
        // For now, we'll capture it on the first onSensorChanged event after enabling.

        sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        enabled = true
        Log.i(TAG, "PhoneRotationPanningInputDevice enabled. Will capture neutral orientation on first sensor event.")
    }

    override fun disable() {
        if (sensorManager == null) {
            Log.w(TAG, "Cannot disable: SensorManager not available or not initialized.")
        }
        sensorManager?.unregisterListener(this, rotationVectorSensor)
        enabled = false
        neutralOrientationCaptured = false // Reset for next enable
        Log.i(TAG, "PhoneRotationPanningInputDevice disabled.")
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Sensor accuracy changed: ${sensor?.name} to $accuracy")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || !enabled || listener == null) {
            return
        }

        SensorManager.getRotationMatrixFromVector(currentRotationMatrix, event.values)

        // Capture neutral orientation on the first event after enabling.
        // This matrix represents the phone's orientation in world space when panning is initiated.
        if (!neutralOrientationCaptured) {
            System.arraycopy(currentRotationMatrix, 0, neutralRotationMatrix, 0, currentRotationMatrix.size)
            neutralOrientationCaptured = true
            lastSentOffsetX = 0f // Ensure offsets start from zero relative to neutral
            lastSentOffsetY = 0f
            Log.i(TAG, "Neutral orientation captured.")
            // Don't process panning on this first event, as it's the reference point.
            return
        }

        // Get current display rotation to adjust coordinate systems
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        var xAxis = SensorManager.AXIS_X
        var yAxis = SensorManager.AXIS_Z // Default for landscape-like neutral
        // This remapping logic needs to be consistent for BOTH neutral and current matrices
        // if we are to compare their direct Euler angles.
        // It's usually better to get current device orientation in a fixed frame (e.g. world frame)
        // and then calculate how the view should pan based on that.

        // For "magic window", the screen itself is the window. We need to know how much the device *itself*
        // has rotated from its neutral pose, in terms of yaw and pitch relative to the *user*.

        // Let's get Euler angles from the raw currentRotationMatrix and neutralRotationMatrix.
        // These are in the device's own coordinate system relative to world.
        val neutralAngles = FloatArray(3)
        SensorManager.getOrientation(neutralRotationMatrix, neutralAngles) // yaw_N, pitch_N, roll_N (world relative)

        val currentRawAngles = FloatArray(3)
        SensorManager.getOrientation(currentRotationMatrix, currentRawAngles) // yaw_C, pitch_C, roll_C (world relative)

        // Calculate difference in world-relative yaw and pitch
        var deltaWorldYaw = currentRawAngles[0] - neutralAngles[0]
        var deltaWorldPitch = currentRawAngles[1] - neutralAngles[1]

        // Handle yaw wrap-around (-PI to PI)
        if (deltaWorldYaw > Math.PI) deltaWorldYaw -= (2 * Math.PI).toFloat()
        if (deltaWorldYaw < -Math.PI) deltaWorldYaw += (2 * Math.PI).toFloat()
        // Pitch usually doesn't wrap around in typical usage ranges (-PI/2 to PI/2)

        // Now, how these world-relative deltas translate to screen panning
        // depends on the initial screen orientation when neutral was set, and current screen orientation.
        // This is where it gets complex. A simpler model might be needed if this becomes too unstable.

        // Let's use the remapping approach from PhoneImuPanningInputDevice for the *current* orientation
        // and apply it to the *delta* calculation to keep things in the "user's view" space.
        // This means the neutral orientation is fixed. We see how much current device orientation (remapped for screen)
        // has changed from that fixed neutral orientation's remapped angles.

        // Remap neutral matrix once (or re-calculate if screen could rotate while enabled, though less common for this mode)
        // For simplicity, assume screen orientation is fixed while this mode is active, or neutral is re-established.
        // Let's remap both neutral and current with the *current* screen rotation.
        // This makes their Euler angles comparable in the current screen frame.

        val remappedNeutralMatrix = FloatArray(9)
        val remappedCurrentMatrix = FloatArray(9)

        // Determine axes for remapping based on current display rotation
        // This ensures that the yaw and pitch are relative to the current screen orientation.
        when (displayRotation) {
            Surface.ROTATION_0 -> { // Normal portrait
                xAxis = SensorManager.AXIS_X; yAxis = SensorManager.AXIS_Y
            }
            Surface.ROTATION_90 -> { // Landscape, rotated left (phone rotates CCW)
                xAxis = SensorManager.AXIS_Y; yAxis = SensorManager.AXIS_MINUS_X
            }
            Surface.ROTATION_180 -> { // Upside down portrait
                xAxis = SensorManager.AXIS_MINUS_X; yAxis = SensorManager.AXIS_MINUS_Y
            }
            Surface.ROTATION_270 -> { // Landscape, rotated right (phone rotates CW)
                xAxis = SensorManager.AXIS_MINUS_Y; yAxis = SensorManager.AXIS_X
            }
        }

        // Remap both neutral and current rotation matrices based on the *current* screen orientation.
        // This allows us to compare their Euler angles in a coordinate system aligned with what the user is currently seeing.
        // The neutral pose (in world space) remains fixed, but its representation in terms of screen-aligned yaw/pitch
        // will change if the screen is rotated. This is desired for a "magic window" effect.
        SensorManager.remapCoordinateSystem(neutralRotationMatrix, xAxis, yAxis, remappedNeutralMatrix)
        SensorManager.remapCoordinateSystem(currentRotationMatrix, xAxis, yAxis, remappedCurrentMatrix)

        val finalNeutralAngles = FloatArray(3) // yaw, pitch, roll
        SensorManager.getOrientation(remappedNeutralMatrix, finalNeutralAngles)
        val finalCurrentAngles = FloatArray(3) // yaw, pitch, roll
        SensorManager.getOrientation(remappedCurrentMatrix, finalCurrentAngles)

        // Calculate relative rotation in radians.
        // relativeYaw: positive when phone is to the left of neutral.
        // relativePitch: positive when phone is tilted up from neutral.
        var relativeYaw = finalCurrentAngles[0] - finalNeutralAngles[0]
        var relativePitch = finalCurrentAngles[1] - finalNeutralAngles[1]

        // Handle wrap-around for yaw (azimuth is -PI to PI).
        if (relativeYaw > Math.PI) relativeYaw -= (2 * Math.PI).toFloat()
        if (relativeYaw < -Math.PI) relativeYaw += (2 * Math.PI).toFloat()
        // Pitch is typically -PI/2 to PI/2, less likely to wrap around dramatically.

        // Fetch sensitivities from AppPreferences
        val sensitivityX = prefs.xr.phoneRotationPanSensitivityX
        val sensitivityY = prefs.xr.phoneRotationPanSensitivityY

        // Apply sensitivities
        val effectiveRelativeYaw = relativeYaw * sensitivityX
        val effectiveRelativePitch = relativePitch * sensitivityY

        // Convert radians of relative rotation to desired absolute pan offset.
        // The ROTATION_TO_PAN_SCALE_FACTOR determines how much the view pans for a given rotation (sensitivity).
        // A larger value means more panning for the same rotation (feels "zoomed in").

        // desiredTotalOffsetX:
        // SensorManager.getOrientation angles[0] (azimuth/yaw) typically increases as the device rotates counter-clockwise (left) from its reference.
        // If `relativeYaw` is positive (phone rotated left from neutral), we want a negative pan offset (pan left).
        // Thus, `desiredTotalOffsetX = -relativeYaw * ROTATION_TO_PAN_SCALE_FACTOR`.
        // Apply sensitivity here using effectiveRelativeYaw
        val desiredTotalOffsetX = -effectiveRelativeYaw * ROTATION_TO_PAN_SCALE_FACTOR

        // desiredTotalOffsetY:
        // SensorManager.getOrientation angles[1] (pitch) typically increases as the device's top tilts upwards (or front tilts up).
        // If `relativePitch` is positive (phone tilted up from neutral), we want a positive pan offset (pan up).
        // Thus, `desiredTotalOffsetY = relativePitch * ROTATION_TO_PAN_SCALE_FACTOR`.
        // Apply sensitivity here using effectiveRelativePitch
        val desiredTotalOffsetY = effectiveRelativePitch * ROTATION_TO_PAN_SCALE_FACTOR

        // Calculate the delta from the last sent absolute offsets to provide smooth incremental panning.
        val deltaX = desiredTotalOffsetX - lastSentOffsetX
        val deltaY = desiredTotalOffsetY - lastSentOffsetY

        // Basic jitter filter: only send pan event if change is significant.
        if (abs(deltaX) > 0.1f || abs(deltaY) > 0.1f) {
            listener?.onPan(deltaX, deltaY)
            // Update last sent offsets to the new absolute offsets.
            lastSentOffsetX = desiredTotalOffsetX
            lastSentOffsetY = desiredTotalOffsetY
        }
    }
}
