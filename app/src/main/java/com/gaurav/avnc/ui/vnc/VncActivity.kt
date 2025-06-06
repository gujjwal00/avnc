/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.SamsungDex
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.viewmodel.VncViewModel.State.Companion.isConnected
import com.gaurav.avnc.viewmodel.VncViewModel.State.Companion.isDisconnected
import com.gaurav.avnc.vnc.VncUri
import com.gaurav.avnc.ui.vnc.xr.ViturePanningInputDevice
import com.gaurav.avnc.ui.vnc.xr.PhoneImuPanningInputDevice
import com.gaurav.avnc.ui.vnc.xr.PhoneRotationPanningInputDevice // Add this new import
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.lang.ref.WeakReference

/********** [VncActivity] startup helpers *********************************/

private const val PROFILE_KEY = "com.gaurav.avnc.server_profile"
private const val PROFILE_ID_KEY = "com.gaurav.avnc.server_profile_id"
private const val FRAME_STATE_KEY = "com.gaurav.avnc.frame_state"
private const val AUTO_RECONNECT_DELAY_KEY = "com.gaurav.avnc.auto_reconnect_delay"

fun createVncIntent(context: Context, profile: ServerProfile): Intent {
    return Intent(context, VncActivity::class.java).apply {
        if (profile.ID != 0L)
            putExtra(PROFILE_ID_KEY, profile.ID)
        else
            putExtra(PROFILE_KEY, profile)
    }
}

fun startVncActivity(source: Activity, profile: ServerProfile) {
    source.startActivity(createVncIntent(source, profile))
}

fun startVncActivity(source: Activity, uri: VncUri) {
    startVncActivity(source, uri.toServerProfile())
}

@Parcelize
private data class SavedFrameState(val frameX: Float, val frameY: Float, val zoom1: Float, val zoom2: Float) : Parcelable

private fun startVncActivity(source: Activity, profile: ServerProfile, frameState: SavedFrameState, autoReconnectDelay: Int) {
    source.startActivity(createVncIntent(source, profile).also {
        it.putExtra(FRAME_STATE_KEY, frameState)
        it.putExtra(AUTO_RECONNECT_DELAY_KEY, autoReconnectDelay)
    })
}
/**************************************************************************/


/**
 * This activity handles the connection to a VNC server.
 */
class VncActivity : AppCompatActivity() {
    private val TAG = "VncActivity"

    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    public val dispatcher by lazy { Dispatcher(this) }
    // Updated TouchHandler instantiation to include viewModel
    private val touchHandler by lazy { TouchHandler(binding.frameView, dispatcher, viewModel, viewModel.pref) }
    val keyHandler by lazy { KeyHandler(dispatcher, viewModel.pref) }
    val virtualKeys by lazy { VirtualKeys(this) }
    val toolbar by lazy { Toolbar(this) }
    private val serverUnlockPrompt = DeviceAuthPrompt(this)
    private val layoutManager by lazy { LayoutManager(this) }
    private var restoredFromBundle = false
    private var wasConnectedWhenStopped = false
    private var onStartTime = 0L
    private var autoReconnectDelay = 5

    private var viturePanningDevice: ViturePanningInputDevice? = null
    private var phoneImuPanningDevice: PhoneImuPanningInputDevice? = null
    private var phoneRotationPanningDevice: PhoneRotationPanningInputDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)

        super.onCreate(savedInstanceState)
        if (!initConnection(savedInstanceState)) {
            finish()
            return
        }

        //Main UI
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.frameView.initialize(this)
        viewModel.frameViewRef = WeakReference(binding.frameView)
        toolbar.initialize()

        setupLayout()
        setupServerUnlock()

        //Observers
        binding.reconnectBtn.setOnClickListener { retryConnection() }
        viewModel.loginInfoRequest.observe(this) { showLoginDialog() }
        viewModel.confirmationRequest.observe(this) { showConfirmationDialog() }
        viewModel.activeGestureStyle.observe(this) { dispatcher.onGestureStyleChanged() }
        viewModel.state.observe(this) { onClientStateChanged(it) }
        viewModel.profileLive.observe(this) { onProfileUpdated(it) }
        viewModel.panRequest.observe(this) { panData ->
            // Pair<Float, Float> where first is deltaYaw, second is deltaPitch
            panData?.let {
                binding.frameView.panFrameRenderer(it.first, it.second)
                binding.frameView.requestRender() // Add this line
            }
        }
        viewModel.zoomRequest.observe(this) { deltaZ ->
            deltaZ?.let {
                binding.frameView.zoomFrameRenderer(it)
                binding.frameView.requestRender()
            }
        }
        viewModel.reinitializeDispatcherRequest.observe(this) {
            // Parameter is not used, it's just a trigger
            dispatcher.reinitializeConfig()
        }
        viewModel.triggerViewReset.observe(this) {
            // Parameter 'it' is not used as it's a Unit event (or null)
            binding.frameView.resetRendererCameraAndSurface()
            binding.frameView.requestRender()
        }

        autoReconnectDelay = intent.getIntExtra(AUTO_RECONNECT_DELAY_KEY, 5)
        savedInstanceState?.let {
            restoredFromBundle = true
            wasConnectedWhenStopped = it.getBoolean("wasConnectedWhenStopped")
        }

        // Initialize and register Panning Input Devices
        initializeAndRegisterPanningDevices()

        // Ensure dispatcher and touchHandler are initialized by accessing them, then link them.
        // Dispatcher needs the TouchPanningInputDevice from TouchHandler.
        dispatcher.setTouchPanningInputDevice(touchHandler.getTouchPanningInputDevice())

        // Enable the TouchPanningInputDevice by default. Its use is now governed by
        // Dispatcher's gesture mapping and its internal enabled flag.
        touchHandler.getTouchPanningInputDevice().enable()

        // Setup initial state of other panning devices based on preferences
        val xrPrefs = viewModel.pref.xr // Convenience accessor

        viturePanningDevice?.let { // Only if device was successfully initialized
            if (xrPrefs.enableViturePanning) viewModel.enablePanningDevice(ViturePanningInputDevice::class.java)
            else viewModel.disablePanningDevice(ViturePanningInputDevice::class.java)
        }

        phoneImuPanningDevice?.let { // Only if device was successfully initialized
            if (xrPrefs.enablePhoneImuDeltaPanning) viewModel.enablePanningDevice(PhoneImuPanningInputDevice::class.java)
            else viewModel.disablePanningDevice(PhoneImuPanningInputDevice::class.java)
        }

        phoneRotationPanningDevice?.let { // Only if device was successfully initialized
            if (xrPrefs.enablePhoneRotationPanning) viewModel.enablePanningDevice(PhoneRotationPanningInputDevice::class.java)
            else viewModel.disablePanningDevice(PhoneRotationPanningInputDevice::class.java)
        }
    }

    private fun initializeAndRegisterPanningDevices() {
        // Viture Panning Device
        try {
            viturePanningDevice = ViturePanningInputDevice(this)
            viewModel.registerPanningInputDevice(viturePanningDevice!!)
            Log.i(TAG, "ViturePanningInputDevice initialized and registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize or register ViturePanningInputDevice", e)
            viturePanningDevice = null
        }

        // Phone IMU Panning Device
        try {
            phoneImuPanningDevice = PhoneImuPanningInputDevice(this)
            viewModel.registerPanningInputDevice(phoneImuPanningDevice!!)
            Log.i(TAG, "PhoneImuPanningInputDevice initialized and registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize or register PhoneImuPanningInputDevice", e)
            phoneImuPanningDevice = null
        }

        // Phone Rotation Panning Device (Magic Window Style)
        try {
            phoneRotationPanningDevice = PhoneRotationPanningInputDevice(this) // Pass Activity context
            viewModel.registerPanningInputDevice(phoneRotationPanningDevice!!)
            Log.i(TAG, "PhoneRotationPanningInputDevice initialized and registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize or register PhoneRotationPanningInputDevice", e)
            phoneRotationPanningDevice = null
        }
    }

    override fun onStart() {
        super.onStart()
        binding.frameView.onResume()
        onStartTime = SystemClock.uptimeMillis()

        // Refresh framebuffer on activity restart:
        // - It forces read/write on the socket. This allows us to verify the socket, which might have
        //   been closed by the server while app process was frozen in background
        // - It also attempts to fix some unusual cases of old updates requests being lost while AVNC
        //   was frozen by the system
        if (viewModel.pref.viewer.pauseUpdatesInBackground)
            viewModel.resumeFrameBufferUpdates()
        else if (wasConnectedWhenStopped)
            viewModel.refreshFrameBuffer()
    }

    override fun onStop() {
        super.onStop()
        virtualKeys.releaseMetaKeys()
        binding.frameView.onPause() // This is GLSurfaceView's onPause

        // Save state here as well, as onStop is guaranteed before destruction for many cases
        // This might be more reliable than onPause for this specific state saving.
        if (this::binding.isInitialized && binding.frameView.mRenderer != null) {
            viewModel.saveXrViewState(binding.frameView.mRenderer)
        }

        if (viewModel.pref.viewer.pauseUpdatesInBackground)
            viewModel.pauseFrameBufferUpdates()
        wasConnectedWhenStopped = viewModel.state.value.isConnected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(PROFILE_KEY, viewModel.profileLive.value)
        outState.putBoolean("wasConnectedWhenStopped", wasConnectedWhenStopped || viewModel.state.value.isConnected)
    }

    private fun initConnection(savedState: Bundle?): Boolean {
        @Suppress("DEPRECATION")
        val profile = savedState?.getParcelable(PROFILE_KEY)
                      ?: intent.getParcelableExtra<ServerProfile?>(PROFILE_KEY)

        if (profile != null) {
            viewModel.initConnection(profile.copy()) // Use a copy to avoid modification to intent
            return true
        }

        val profileId = intent.getLongExtra(PROFILE_ID_KEY, 0)
        if (profileId == 0L) {
            Toast.makeText(this, "Error: Missing Server Info", Toast.LENGTH_LONG).show()
            return false
        }

        initConnectionFromId(profileId)
        return true
    }

    private fun initConnectionFromId(profileId: Long) {
        lifecycleScope.launch {
            val profile = viewModel.getProfileById(profileId)
            if (profile != null) {
                viewModel.initConnection(profile)
            } else {
                Toast.makeText(this@VncActivity, "Error: Invalid Server ID", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Invalid profile ID passed via Intent: $profileId")
                finish()
            }
        }
    }

    private fun onProfileUpdated(profile: ServerProfile) {
        keyHandler.emitLegacyKeysym = true /*profile.fLegacyKeySym*/
        setupOrientation()
    }

    private fun retryConnection(seamless: Boolean = false, nextAutoReconnectDelay: Int = 0) {
        //We simply create a new activity to force creation of new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
            val savedFrameState = viewModel.frameState.let {
                SavedFrameState(frameX = it.frameX, frameY = it.frameY, zoom1 = it.zoomScale1, zoom2 = it.zoomScale2)
            }

            startVncActivity(this, viewModel.profile, savedFrameState, nextAutoReconnectDelay)

            if (seamless) {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            finish()
        }
    }

    private fun setupServerUnlock() {
        serverUnlockPrompt.init(
                onSuccess = { viewModel.serverUnlockRequest.offerResponse(true) },
                onFail = { viewModel.serverUnlockRequest.offerResponse(false) }
        )

        viewModel.serverUnlockRequest.observe(this) {
            if (serverUnlockPrompt.canLaunch())
                serverUnlockPrompt.launch(getString(R.string.title_unlock_dialog))
            else
                viewModel.serverUnlockRequest.offerResponse(true)
        }
    }

    private fun showLoginDialog() {
        LoginFragment().show(supportFragmentManager, "LoginDialog")
    }

    private fun showConfirmationDialog() {
        ConfirmationDialog().show(supportFragmentManager, "ConfirmationDialog")
    }

    fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)

        virtualKeys.onKeyboardOpen()
    }

    private fun onClientStateChanged(newState: VncViewModel.State) {
        val isConnected = newState.isConnected

        binding.frameView.isVisible = isConnected
        binding.frameView.keepScreenOn = isConnected && viewModel.pref.viewer.keepScreenOn
        SamsungDex.setMetaKeyCapture(this, isConnected)
        layoutManager.onConnectionStateChanged()
        updateStatusContainerVisibility(isConnected)
        autoReconnect(newState)

        if (isConnected) {
            ViewerHelp().onConnected(this)
            keyHandler.enableMacOSCompatibility = viewModel.client.isConnectedToMacOS()
            virtualKeys.onConnected(isInPiPMode())
            binding.frameView.setInputHandlers(keyHandler, touchHandler)
            autoReconnectDelay = 1
        }

        if (isConnected && !restoredFromBundle) {
            incrementUseCount()
            restoreFrameState()
        }
    }

    private fun incrementUseCount() {
        viewModel.profile.useCount += 1
        viewModel.saveProfile()
    }

    private fun updateStatusContainerVisibility(isConnected: Boolean) {
        binding.statusContainer.isVisible = true
        binding.statusContainer
                .animate()
                .alpha(if (isConnected) 0f else 1f)
                .withEndAction { binding.statusContainer.isVisible = !isConnected }
    }

    private fun restoreFrameState() {
        intent.extras?.let { extras ->
            BundleCompat.getParcelable(extras, FRAME_STATE_KEY, SavedFrameState::class.java)?.let {
                viewModel.setZoom(it.zoom1, it.zoom2)
                viewModel.panFrame(it.frameX, it.frameY)
            }
        }
    }

    private var autoReconnecting = false
    private fun autoReconnect(state: VncViewModel.State) {
        if (!state.isDisconnected)
            return

        // If disconnected when coming back from background, try to reconnect immediately
        if (wasConnectedWhenStopped && (SystemClock.uptimeMillis() - onStartTime) in 0..2000) {
            Log.i(TAG, "Disconnected while in background, reconnecting ...")
            retryConnection(true)
            return
        }

        if ((autoReconnecting || !viewModel.pref.server.autoReconnect) && !viewModel.profile.enableWol)
            return

        autoReconnecting = true
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val reconnectDelay = autoReconnectDelay.coerceIn(0, 5) //seconds

                repeat(reconnectDelay) {
                    val progress = if (reconnectDelay <= 1) 100 else (100 * it) / (reconnectDelay - 1)
                    binding.autoReconnectProgress.setProgressCompat(progress, true)
                    delay(1000)
                }

                // Automatic reconnect attempts happen every 5 seconds.
                // But if session had reached Connected state, first attempt happens
                // after 1 second, second attempt after 3 seconds, and then every 5 seconds.
                val nextReconnectDelay = if (reconnectDelay < 3) 3 else 5
                Log.d(TAG, "AutoReconnect: Retrying after $reconnectDelay seconds")
                retryConnection(nextAutoReconnectDelay = nextReconnectDelay)
            }
        }
    }


    /************************************************************************************
     * Layout handling.
     ************************************************************************************/
    private fun setupLayout() {
        layoutManager.initialize()

        if (Build.VERSION.SDK_INT >= 28 && viewModel.pref.viewer.drawBehindCutout) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun setupOrientation() {
        val choice = viewModel.profile.screenOrientation.let {
            if (it != "auto") it else viewModel.pref.viewer.orientation
        }

        requestedOrientation = when (choice) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        layoutManager.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.sendClipboardText()
    }


    /************************************************************************************
     * Picture-in-Picture support
     ************************************************************************************/

    private fun isInPiPMode(): Boolean {
        return Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPiPMode()
    }

    @RequiresApi(26)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        virtualKeys.onPiPModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            toolbar.close()
            viewModel.resetZoom()
        } else {
            // If user taps the Close button on PiP window, Android will stop the Activity
            // but won't destroy it. This is not a problem for singleTask activities since those
            // are still shown in Recents screen. But AVNC doesn't use singleTask. So VncActivity
            // in PiP mode gets detached into a separate task, which for some reason isn't shown
            // in Recents screen. Hence the activity is effectively leaked.
            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                Log.i(TAG, "Finishing activity on PiP Close button click")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Most cleanup is handled by VncViewModel.onCleared(), which calls
        // clearAndDisableAllPanningDevices().
        // If specific devices need Activity context for release and VncViewModel can't do it,
        // it would be done here. For ViturePanningInputDevice, its releaseSdk()
        // should ideally be called from VncViewModel.clearAndDisableAllPanningDevices()
        // after checking instance type.

        // Example if explicit call from Activity was needed for some reason:
        // viturePanningDevice?.releaseSdk()
        // phoneImuPanningDevice has no explicit release method.
        // TouchPanningInputDevice has no explicit release method.
        Log.d(TAG, "VncActivity onDestroy.")
    }

    private fun enterPiPMode() {
        val canEnter = viewModel.pref.viewer.pipEnabled && viewModel.client.connected

        if (canEnter && Build.VERSION.SDK_INT >= 26) {

            var w = viewModel.frameState.fbWidth
            var h = viewModel.frameState.fbHeight
            if (w <= 0 || h <= 0)
                return

            // Android require aspect ratio to be less than 2.39
            w = w.coerceIn(1f, 2.3f * h)
            h = h.coerceIn(1f, 2.3f * w)

            val aspectRatio = Rational(w.toInt(), h.toInt())
            val param = PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build()

            try {
                enterPictureInPictureMode(param)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Cannot enter PiP mode", e)
            }
        }
    }

    /************************************************************************************
     * Input
     ************************************************************************************/

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || workarounds(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || workarounds(event) || super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return keyHandler.onKeyEvent(event) || super.onKeyMultiple(keyCode, repeatCount, event)
    }

    private fun workarounds(keyEvent: KeyEvent): Boolean {

        //It seems that some device manufacturers are hell-bent on making developers'
        //life miserable. In their infinite wisdom, they decided that Android apps don't
        //need Mouse right-click events. It is hardcoded to act as back-press, without
        //giving apps a chance to handle it. For better or worse, they set the 'source'
        //for such key events to Mouse, enabling the following workarounds.
        if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK &&
            InputDevice.getDevice(keyEvent.deviceId)?.supportsSource(InputDevice.SOURCE_MOUSE) == true &&
            viewModel.pref.input.interceptMouseBack) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN)
                touchHandler.onMouseBack()
            return true
        }
        return false
    }
}