/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.gaurav.avnc.databinding.NoVideoOverlayBinding
import com.gaurav.avnc.databinding.ViewerHelpBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.input.InputHandler
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.SamsungDex
import com.gaurav.avnc.util.debugCheck
import com.gaurav.avnc.util.enableChildLayoutTransitions
import com.gaurav.avnc.util.loopAnimatedDrawable
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncUri
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
    private val inputHandler = InputHandler(this)
    val virtualKeys by lazy { VirtualKeys(this, inputHandler) }
    val toolbar by lazy { Toolbar(this) }
    private val serverUnlockPrompt = DeviceAuthPrompt(this)
    private val layoutManager by lazy { LayoutManager(this) }
    private var restoredFromBundle = false
    private var wasConnectedWhenStopped = false
    private var onStartTime = 0L
    private var autoReconnectDelay = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)

        super.onCreate(savedInstanceState)
        if (!startup(savedInstanceState))
            return

        //Main UI
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.frameView.initialize(viewModel, inputHandler)
        viewModel.frameViewRef = WeakReference(binding.frameView)
        toolbar.initialize()

        setupLayout()
        setupNoVideoOverlay()

        //Observers
        binding.reconnectBtn.setOnClickListener { retryConnection() }
        viewModel.loginInfoRequest.observe(this) { showLoginDialog() }
        viewModel.confirmationRequest.observe(this) { showConfirmationDialog() }
        viewModel.state.observe(this) { onClientStateChanged(it) }
        viewModel.profileLive.observe(this) { onProfileUpdated() }
        viewModel.capturePointer.observe(this) { updatePointerCapture(it) }

        autoReconnectDelay = intent.getIntExtra(AUTO_RECONNECT_DELAY_KEY, 5)
        savedInstanceState?.let {
            restoredFromBundle = true
            wasConnectedWhenStopped = it.getBoolean("wasConnectedWhenStopped")
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
        if (viewModel.pref.viewer.pauseUpdatesInBackground && !viewModel.videoDisabled)
            viewModel.setFrameBufferUpdatesPaused(false)
        else if (wasConnectedWhenStopped)
            viewModel.refreshFrameBuffer()
    }

    override fun onStop() {
        super.onStop()
        virtualKeys.releaseMetaKeys()
        binding.frameView.onPause()
        if (viewModel.pref.viewer.pauseUpdatesInBackground)
            viewModel.setFrameBufferUpdatesPaused(true)
        wasConnectedWhenStopped = viewModel.connected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(PROFILE_KEY, viewModel.profileLive.value)
        outState.putBoolean("wasConnectedWhenStopped", wasConnectedWhenStopped || viewModel.connected)
    }


    /**********************************************************************************************
     * Session startup
     *********************************************************************************************/
    private sealed class StartupArg {
        data class Profile(val profile: ServerProfile) : StartupArg()
        data class ProfileId(val profileId: Long) : StartupArg()
    }

    @Suppress("DEPRECATION")
    private fun prepareStartupArg(savedState: Bundle?): StartupArg? {
        val id = intent.getLongExtra(PROFILE_ID_KEY, 0)
        val profile = savedState?.getParcelable(PROFILE_KEY)
                      ?: intent.getParcelableExtra<ServerProfile?>(PROFILE_KEY)

        return when {
            id != 0L -> StartupArg.ProfileId(id)
            profile != null -> StartupArg.Profile(profile.copy())  // Use a copy to avoid modification to intent
            else -> {
                handleMissingStartupArgs()
                null
            }
        }
    }

    private fun startup(savedState: Bundle?): Boolean {
        if (viewModel.profileLive.value != null) // todo refactor
            return true

        val startupArg = prepareStartupArg(savedState) ?: return false
        val isSavedServer = startupArg is StartupArg.ProfileId ||
                            (startupArg is StartupArg.Profile && startupArg.profile.ID == 0L)

        if (isSavedServer && viewModel.pref.server.lockSavedServer)
            startAfterUnlockingServer(startupArg)
        else
            startSession(startupArg)

        return true
    }


    private fun startAfterUnlockingServer(startupArg: StartupArg) {
        serverUnlockPrompt.init(
                onSuccess = { startSession(startupArg) },
                onFail = { handleServerUnlockFailure(it) }
        )

        if (serverUnlockPrompt.canLaunch()) {
            if (!serverUnlockPrompt.hasLaunched())
                serverUnlockPrompt.launch(getString(R.string.title_unlock_dialog))
        } else
            startSession(startupArg)
    }


    private fun startSession(startupArg: StartupArg) {
        when (startupArg) {
            is StartupArg.Profile -> startSession(startupArg.profile)
            is StartupArg.ProfileId -> {
                lifecycleScope.launch {
                    val profile = viewModel.getProfileById(startupArg.profileId)
                    if (profile == null)
                        handleInvalidProfileId(startupArg.profileId)
                    else
                        startSession(profile)
                }
            }
        }
    }

    private fun startSession(profile: ServerProfile) {
        viewModel.initConnection(profile)
    }

    private fun handleMissingStartupArgs() {
        debugCheck(false) // Crash debug builds
        Toast.makeText(this, "Error: Missing Server Info", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleServerUnlockFailure(msg: String) {
        Toast.makeText(this, "Could not unlock server", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Server unlock failed: $msg")
        finish()
    }

    private fun handleInvalidProfileId(id: Long) {
        Toast.makeText(this, "Error: Invalid Server ID", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Invalid profile ID passed via Intent: $id")
        finish()
    }

    private fun onProfileUpdated() {}

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

    private fun setupNoVideoOverlay() {
        viewModel.activeViewMode.observe(this) {
            if (viewModel.videoDisabled) {
                inflateNoVideoOverlay()
                binding.noVideoOverlayStub.root?.isVisible = true
            } else {
                binding.noVideoOverlayStub.root?.isVisible = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun inflateNoVideoOverlay() {
        if (binding.noVideoOverlayStub.isInflated)
            return

        enableChildLayoutTransitions(binding.frameContainer)

        binding.noVideoOverlayStub.viewStub?.inflate()
        val stubBinding = binding.noVideoOverlayStub.binding as NoVideoOverlayBinding
        val rootView = stubBinding.overlayRoot
        val tapIndicator = stubBinding.tapIndicator

        enableChildLayoutTransitions(stubBinding.overlayRoot)

        // Tap indicator should appear immediately, but disappear with animation
        rootView.layoutTransition?.setDuration(LayoutTransition.APPEARING, 0)

        rootView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> tapIndicator.apply {
                    isVisible = true
                    translationX = event.x - (width / 2)
                    translationY = event.y - (height / 2)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> tapIndicator.isVisible = false
            }
            inputHandler.onTouchEvent(event)
        }
    }

    private fun showLoginDialog() {
        LoginFragment().show(supportFragmentManager, "LoginDialog")
    }

    private fun showConfirmationDialog() {
        ConfirmationDialog().show(supportFragmentManager, "ConfirmationDialog")
    }

    fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)

        virtualKeys.onKeyboardOpen()
    }

    private fun onClientStateChanged(newState: VncViewModel.State) {
        val isConnected = newState == VncViewModel.State.Connected

        binding.frameView.isVisible = isConnected
        binding.frameView.keepScreenOn = isConnected && viewModel.pref.viewer.keepScreenOn
        SamsungDex.setMetaKeyCapture(this, isConnected)
        layoutManager.onConnectionStateChanged()
        inputHandler.onStateChanged(isConnected)
        toolbar.onStateChange(isConnected)
        updateStatusContainerVisibility(isConnected)
        autoReconnect(newState)

        if (isConnected) {
            showViewerHelp()
            virtualKeys.onConnected()
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

    private fun updatePointerCapture(capturePointer: Boolean) {
        if (Build.VERSION.SDK_INT < 26)
            return

        if (capturePointer) {
            binding.frameView.requestFocus()
            binding.frameView.requestPointerCapture()
        } else
            binding.frameView.releasePointerCapture()
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
        if (state != VncViewModel.State.Disconnected)
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

        viewModel.preferredScreenOrientation.observe(this) { requestedOrientation = it }

        if (Build.VERSION.SDK_INT >= 28 && viewModel.pref.viewer.drawBehindCutout) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        viewModel.hasWindowFocus.value = hasFocus
        if (hasFocus) {
            viewModel.sendClipboardText()
        }
    }


    /************************************************************************************
     * Picture-in-Picture support
     ************************************************************************************/

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPiPMode()
    }

    @RequiresApi(26)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.inPiPMode.value = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
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

    private fun enterPiPMode() {
        val canEnter = viewModel.pref.viewer.pipEnabled && viewModel.connected

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
        return inputHandler.onKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return inputHandler.onKeyEvent(event) || super.onKeyUp(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        return inputHandler.onKeyEvent(event) || super.onKeyMultiple(keyCode, repeatCount, event)
    }


    /************************************************************************************
     * Help for new users.
     * Two of the most common question asked by new users are:
     * - Where is the toolbar, or how to open it
     * - How to cleanly exit a session
     *
     * When user starts a session for the first time, this help is shown.
     * It consists of two pages: one shows how to open the toolbar drawer,
     * other tells about the Back navigation button.
     ***********************************************************************************/
    fun showViewerHelp() {
        if (viewModel.pref.runInfo.hasShownViewerHelp)
            return

        initHelpView()
    }

    private fun initHelpView() {
        val helpBinding = ViewerHelpBinding.inflate(layoutInflater, binding.drawerLayout, false)
        binding.drawerLayout.addView(helpBinding.root, 1)
        viewModel.viewerHelpIsVisible.value = true

        helpBinding.root.setOnClickListener { /* Consume clicks to stop them from passing through to FrameView */ }
        enableChildLayoutTransitions(helpBinding.pageHost)

        // Open help view with animation
        helpBinding.root.alpha = 0f
        helpBinding.root.animate().alpha(1f).setStartDelay(500).withEndAction {
            loopAnimatedDrawable(helpBinding.toolbarAnimation)
        }

        helpBinding.nextBtn.setOnClickListener {
            helpBinding.page1.isVisible = false
            helpBinding.page2.isVisible = true
            loopAnimatedDrawable(helpBinding.navbarAnimation)
        }
        helpBinding.endBtn.setOnClickListener {
            viewModel.pref.runInfo.hasShownViewerHelp = true
            viewModel.viewerHelpIsVisible.value = false
            helpBinding.root.animate().alpha(0f).withEndAction {
                binding.drawerLayout.removeView(helpBinding.root)
            }
        }
    }
}