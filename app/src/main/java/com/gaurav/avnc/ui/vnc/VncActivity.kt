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
import android.app.PictureInPictureParams
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
import androidx.biometric.auth.AuthPromptErrorException
import androidx.biometric.auth.AuthPromptFailureException
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.databinding.NoVideoOverlayBinding
import com.gaurav.avnc.databinding.ViewerHelpBinding
import com.gaurav.avnc.ui.vnc.input.InputHandler
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.EdgeToEdgeHelper
import com.gaurav.avnc.util.SamsungDex
import com.gaurav.avnc.util.debugCheck
import com.gaurav.avnc.util.enableChildLayoutTransitions
import com.gaurav.avnc.util.loopAnimatedDrawable
import com.gaurav.avnc.viewmodel.VncViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds

/**
 * This activity handles the connection to a VNC server.
 */
class VncActivity : AppCompatActivity() {

    @Parcelize
    private data class SavedState(
            val frameX: Float,
            val frameY: Float,
            val zoomScale1: Float,
            val zoomScale2: Float,
            val reconnectDelay: Int?) : Parcelable

    companion object {
        private const val TAG = "VncActivity"
        private const val SAVED_STATE_KEY = "com.gaurav.avnc.vnc_activity.saved_state"
    }

    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    private val inputHandler = InputHandler(this)
    val virtualKeys by lazy { VirtualKeys(this, inputHandler) }
    val toolbar by lazy { Toolbar(this) }
    private val serverUnlockPrompt = DeviceAuthPrompt(this)
    private val layoutManager by lazy { LayoutManager(this) }
    private var oldState: SavedState? = null
    private var hasActivityRestarted = false
    private var hasConnectedSuccessfully = false
    private var wasConnectedWhenActivityStopped = false
    private var onStartTime = 0L

    /**********************************************************************************************
     * Activity Lifecycle
     *********************************************************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)

        super.onCreate(savedInstanceState)
        startup(savedInstanceState)

        //Main UI
        binding = EdgeToEdgeHelper.setDataBindingContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.frameView.initialize(viewModel)
        binding.inputView.initialize(viewModel, inputHandler)
        viewModel.frameViewRef = WeakReference(binding.frameView)

        setupLayout()
        setupNoVideoOverlay()

        //Observers
        binding.reconnectBtn.setOnClickListener { reconnect() }
        viewModel.loginInfoRequest.observe(this) { showLoginDialog() }
        viewModel.confirmationRequest.observe(this) { showConfirmationDialog() }
        viewModel.state.observe(this) { onClientStateChanged(it) }
        viewModel.profileLive.observe(this) { onProfileUpdated() }
        viewModel.capturePointer.observe(this) { updatePointerCapture(it) }

        hasActivityRestarted = savedInstanceState != null
        oldState = (savedInstanceState ?: intent.extras)?.let {
            BundleCompat.getParcelable(it, SAVED_STATE_KEY, SavedState::class.java)
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
        else if (wasConnectedWhenActivityStopped)
            viewModel.refreshFrameBuffer()
    }

    override fun onStop() {
        super.onStop()
        virtualKeys.releaseMetaKeys()
        binding.frameView.onPause()
        if (viewModel.pref.viewer.pauseUpdatesInBackground)
            viewModel.setFrameBufferUpdatesPaused(true)
        wasConnectedWhenActivityStopped = viewModel.connected
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        viewModel.hasWindowFocus.value = hasFocus
        if (hasFocus) {
            viewModel.sendClipboardText()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SAVED_STATE_KEY, prepareSavedState())
        viewModel.profileLive.value?.let { outState.putProfile(it) }
    }

    private fun prepareSavedState(reconnectDelay: Int? = null): SavedState {
        val fs = viewModel.frameState
        return SavedState(
                frameX = fs.frameX,
                frameY = fs.frameY,
                zoomScale1 = fs.zoomScale1,
                zoomScale2 = fs.zoomScale2,
                reconnectDelay = reconnectDelay
        )
    }


    /**********************************************************************************************
     * Session Control
     *********************************************************************************************/

    private fun startup(savedState: Bundle?) {
        if (viewModel.profileLive.value != null) // todo refactor
            return

        lifecycleScope.launch {
            runCatching {
                val startupArg = parseStartupArg(intent, savedState)
                unlockServers(startupArg)
                startSession(startupArg, viewModel)
            }.onFailure {
                handleStartupFailure(it)
            }
        }
    }

    private suspend fun unlockServers(startupArg: StartupArg) {
        val isSavedServer = startupArg is StartupArg.ProfileId ||
                            (startupArg is StartupArg.Profile && startupArg.profile.isSaved())

        if (isSavedServer && viewModel.pref.server.lockSavedServer && serverUnlockPrompt.canLaunch())
            serverUnlockPrompt.authenticate(getString(R.string.title_unlock_dialog))
    }

    private fun handleStartupFailure(cause: Throwable) {
        var msg = cause.message ?: "An error occurred during startup"
        when (cause) {
            is MissingStartupArgException -> {
                debugCheck(false) // Crash debug builds
                msg = "Error: Missing Server Info"
            }
            is InvalidProfileIdException -> {
                msg = "Error: Invalid Server ID"
            }
            is AuthPromptErrorException,
            is AuthPromptFailureException -> {
                msg = "Could not unlock server"
            }
        }

        Log.e(TAG, msg, cause)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun reconnect(savedState: SavedState = prepareSavedState()) {
        //We simply create a new activity to force creation of new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
            startActivity(createVncIntent(this, viewModel.profile).also {
                it.putExtra(SAVED_STATE_KEY, savedState)
            })

            if (savedState.reconnectDelay == 0) {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
            finish()
        }
    }

    private var autoReconnecting = false
    private fun autoReconnect() {
        if (autoReconnecting)
            return

        val reconnectDelay = calculateAutoReconnectDelay() ?: return
        autoReconnecting = true
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Show progress bar
                // Progress intentionally reaches 100% 1 step earlier to allow the animation to complete
                repeat(reconnectDelay) {
                    val progress = if (reconnectDelay <= 1) 100 else (100 * it) / (reconnectDelay - 1)
                    binding.autoReconnectProgress.setProgressCompat(progress, true)
                    delay(1.seconds)
                }

                reconnect(prepareSavedState(reconnectDelay))
            }
        }
    }

    private fun calculateAutoReconnectDelay(): Int? {
        // If disconnected when coming back from background, try to reconnect immediately
        if (wasConnectedWhenActivityStopped && (SystemClock.uptimeMillis() - onStartTime) in 0..2000) {
            Log.i(TAG, "Disconnected during activity restart, reconnecting ...")
            return 0
        }

        if (!viewModel.pref.server.autoReconnect && !viewModel.profile.enableWol)
            return null // Auto-reconnect is disabled

        // Automatic reconnect happens every 5 seconds.
        // But if session had reached Connected state, first attempt happens
        // after 1 second, second attempt after 3 seconds, and then every 5 seconds.
        val previousDelay = oldState?.reconnectDelay
        return when {
            hasConnectedSuccessfully -> 1   // Connection lost, try to reconnect early
            previousDelay == null -> 5
            previousDelay == 0 -> 1
            else -> (previousDelay + 2).coerceAtMost(5)
        }
    }


    /**********************************************************************************************
     * Session lifecycle
     *********************************************************************************************/

    private fun onProfileUpdated() {
        toolbar.initialize()
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

        if (isConnected) {
            showViewerHelp()
            virtualKeys.onConnected()
            hasConnectedSuccessfully = true
        }

        if (isConnected && !hasActivityRestarted) {
            incrementUseCount()
            restoreFrameState()
        }

        if (newState == VncViewModel.State.Disconnected)
            autoReconnect()
    }

    private fun restoreFrameState() {
        oldState?.let {
            viewModel.setZoom(it.zoomScale1, it.zoomScale2)
            viewModel.panFrame(it.frameX, it.frameY)
        }
    }

    private fun incrementUseCount() {
        viewModel.profile.useCount += 1
        viewModel.saveProfile()
    }


    /************************************************************************************
     * Interface
     ************************************************************************************/
    private fun setupLayout() {
        layoutManager.initialize()

        viewModel.preferredScreenOrientation.observe(this) { requestedOrientation = it }

        if (Build.VERSION.SDK_INT >= 28 && viewModel.pref.viewer.drawBehindCutout) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (Build.VERSION.SDK_INT >= 26 && isInPictureInPictureMode) {
            viewModel.inPiPMode.value = true
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

        binding.inputView.requestFocus()
        imm.showSoftInput(binding.inputView, 0)

        virtualKeys.onKeyboardOpen()
    }

    private fun updatePointerCapture(capturePointer: Boolean) {
        if (Build.VERSION.SDK_INT < 26)
            return

        if (capturePointer) {
            binding.inputView.requestFocus()
            binding.inputView.requestPointerCapture()
        } else
            binding.inputView.releasePointerCapture()
    }

    private fun updateStatusContainerVisibility(isConnected: Boolean) {
        binding.statusContainer.isVisible = true
        binding.statusContainer
                .animate()
                .alpha(if (isConnected) 0f else 1f)
                .withEndAction { binding.statusContainer.isVisible = !isConnected }
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
        val canEnter = viewModel.pref.viewer.pipEnabled && viewModel.connected && !viewModel.videoDisabled

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