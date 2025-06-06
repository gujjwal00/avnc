package com.gaurav.avnc.ui.vnc.xr

import android.app.Activity // Add this import
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gaurav.avnc.util.AppPreferences // Added import
import com.gaurav.avnc.ui.vnc.PanningInputDevice
import com.gaurav.avnc.ui.vnc.PanningListener
import com.viture.sdk.ArCallback
import com.viture.sdk.ArManager
import com.viture.sdk.Constants
import java.nio.ByteBuffer

class ViturePanningInputDevice(private val activity: Activity) : PanningInputDevice {

    private val TAG = "ViturePanningDevice"
    private var mArManager: ArManager? = null
    private var mSdkInitSuccess = -1
    private var listener: PanningListener? = null
    private var enabled = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences // Added field

    private var mLastYaw: Float? = null
    private var mLastPitch: Float? = null

    init {
        prefs = AppPreferences(activity.applicationContext) // Initialize prefs
        try {
            mArManager = ArManager.getInstance(activity)
            val initReturnCode = mArManager?.init() ?: -1 // Store return code
            mSdkInitSuccess = initReturnCode // Keep this assignment
            Log.i(TAG, "ArManager.init() called. Synchronous return code: $initReturnCode. mSdkInitSuccess set to this value initially.") // Enhanced log
            if (mSdkInitSuccess != Constants.ERROR_INIT_SUCCESS) {
                Log.e(TAG, "Viture SDK synchronous init failed. Error code: $mSdkInitSuccess. Check logs from ArManager if any, and ensure USB permissions if applicable.")
                mArManager = null
            } else {
                Log.i(TAG, "Viture SDK synchronous init appears successful. Waiting for potential EVENT_ID_INIT in onEvent for final confirmation or updates.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Viture SDK ArManager.getInstance or init(): ${e.message}", e)
            mArManager = null
            mSdkInitSuccess = -99 // Indicate exception during init
        }
    }

    private val mCallback = object : ArCallback() {
        override fun onEvent(eventId: Int, event: ByteArray?, l: Long) {
            Log.d(TAG, "onEvent: eventId=$eventId, eventData: ${event?.joinToString { "%02x".format(it) }}")
            if (eventId == Constants.EVENT_ID_INIT) {
                if (event != null) {
                    val initResult = byteArrayToInt(event, 0, event.size)
                    mSdkInitSuccess = initResult // Update mSdkInitSuccess
                    Log.i(TAG, "Received EVENT_ID_INIT from Viture SDK. Parsed mSdkInitSuccess: $mSdkInitSuccess. Event data: ${event.joinToString { "%02x".format(it) }}")
                    if (mSdkInitSuccess != Constants.ERROR_INIT_SUCCESS) {
                        Log.e(TAG, "Viture SDK onEvent reported non-success initialization status: $mSdkInitSuccess")
                    }
                } else {
                    Log.w(TAG, "Received EVENT_ID_INIT with null event data.")
                }
            }
            // Add other eventId handling here if needed in the future.
        }

        override fun onImu(ts: Long, imu: ByteArray?) {
            if (imu == null || !enabled) return

            val byteBuffer = ByteBuffer.wrap(imu)
            // Euler angles are big-endian floats
            // byteBuffer.order(ByteOrder.BIG_ENDIAN) // Default is BIG_ENDIAN for ByteBuffer

            // float eulerRoll = byteBuffer.getFloat(0) // roll --> front-axis
            val eulerPitch = byteBuffer.getFloat(4) // pitch -> right-axis (used as Y)
            val eulerYaw = byteBuffer.getFloat(8)   // yaw --> up-axis (used as X)

            // Run on UI thread if the listener might update UI
            // For now, assume listener handles threading if necessary, or is safe.
            // uiHandler.post {
            // }

            if (mLastYaw == null || mLastPitch == null) {
                mLastYaw = eulerYaw
                mLastPitch = eulerPitch
                return
            }

            val deltaYaw = eulerYaw - mLastYaw!!
            val deltaPitch = eulerPitch - mLastPitch!!

            mLastYaw = eulerYaw
            mLastPitch = eulerPitch

            val sensitivityX = prefs.xr.viturePanSensitivityX
            val sensitivityY = prefs.xr.viturePanSensitivityY

            val adjustedDeltaYaw = deltaYaw * sensitivityX
            val adjustedDeltaPitch = deltaPitch * sensitivityY

            // Positive yaw from SDK is typically right, positive pitch is typically up.
            // Adjust signs if needed based on how PanningController expects input.
            // Assuming PanningController expects:
            // - positive deltaYaw for panning right
            // - positive deltaPitch for panning up
            // The Viture SDK sample's move(eulerYaw, eulerPitch) implies:
            // - eulerYaw: positive for right rotation (around up-axis)
            // - eulerPitch: positive for upward rotation (around right-axis)
            // So, direct mapping might be fine, but needs testing.
            // The example app uses `moveX = (int)(-xStep * deltaX)` for yaw.
            // and `moveY = (int)(yStep * deltaY)` for pitch.
            // This suggests that positive eulerYaw change means looking left if not negated.
            // Let's assume for now that the listener expects standard Cartesian changes:
            // deltaYaw > 0 means pan right, deltaPitch > 0 means pan up.
            // The Viture example implies `deltaX` (yaw change) is negated for `moveX`.
            // So, we should probably negate `deltaYaw`.
            listener?.onPan(-adjustedDeltaYaw, adjustedDeltaPitch)
        }
    }

    override fun setPanningListener(listener: PanningListener?) {
        this.listener = listener
    }

    override fun enable() {
        if (mArManager == null || mSdkInitSuccess != Constants.ERROR_INIT_SUCCESS) {
            Log.w(TAG, "Cannot enable: Viture SDK not initialized.")
            return
        }
        if (enabled) return

        mArManager?.registerCallback(mCallback) // This should be before setImuOn generally
        val result = mArManager?.setImuOn(true)
        Log.i(TAG, "mArManager.setImuOn(true) called. Return code: $result") // Log the result
        if (result == Constants.ERR_SET_SUCCESS) {
            enabled = true
            mLastYaw = null
            mLastPitch = null
            Log.i(TAG, "Viture IMU enabled successfully via setImuOn(true).")

           val set3DResult = mArManager?.set3D(false)
           Log.i(TAG, "mArManager.set3D(false) called. Return code: $set3DResult")
           if (set3DResult != Constants.ERR_SET_SUCCESS) {
               Log.e(TAG, "Failed to set 3D mode to false. Error code: $set3DResult. This might affect IMU data.")
               // Optional: Consider if we should disable IMU or revert 'enabled' state if set3D(false) is critical
               // For now, just logging the error.
           } else {
               Log.i(TAG, "Successfully set 3D mode to false.")
           }
        } else {
            Log.e(TAG, "Failed to enable Viture IMU via setImuOn(true). Error code: $result. Unregistering callback.")
            mArManager?.unregisterCallback(mCallback)
        }
    }

    override fun disable() {
        if (mArManager == null) {
            // Log this, but don't crash. It might be called during cleanup.
            Log.w(TAG, "Cannot disable: Viture SDK not initialized or already released.")
            // return // Allow unregisterCallback to be called even if mArManager is null, to be safe
        }
        if (!enabled && mArManager != null) { // only try to turn off if ARManager exists
            mArManager?.setImuOn(false) // Attempt to turn off hardware even if not "enabled" by our flag
        }

        enabled = false
        mArManager?.unregisterCallback(mCallback)
        // Note: The SDK docs mention mArManager.release() for resource cleanup.
        // This should be called when the device is permanently disconnected or app is closing,
        // not just on disable if it might be re-enabled later.
        // For now, release() is not called here. It should be managed by whoever owns this ViturePanningInputDevice.
        Log.i(TAG, "Viture IMU disabled.")
    }

    override fun isEnabled(): Boolean {
        return enabled
    }

    // Consider adding a method to release SDK resources when this device is no longer needed.
    fun releaseSdk() {
        if (mArManager != null) {
            if (enabled) {
                disable()
            }
            mArManager?.release()
            mArManager = null
            mSdkInitSuccess = -1 // Mark as uninitialized
            Log.i(TAG, "Viture SDK resources released.")
        }
    }

    private fun byteArrayToInt(bytes: ByteArray?, offset: Int = 0, length: Int = bytes?.size ?: 0): Int {
        if (bytes == null || offset < 0 || offset + length > bytes.size) {
            Log.e(TAG, "byteArrayToInt: Invalid input. Bytes null: ${bytes == null}, offset: $offset, length: $length, bytes.size: ${bytes?.size}")
            return if (bytes == null) 0 else -1 // Or throw an IllegalArgumentException
        }
        var value = 0
        // Viture SDK docs state event data is little-endian for EVENT_ID_INIT
        for (i in 0 until length) {
            value += (bytes[offset + i].toInt() and 0xFF) shl (i * 8)
        }
        return value
    }
}
