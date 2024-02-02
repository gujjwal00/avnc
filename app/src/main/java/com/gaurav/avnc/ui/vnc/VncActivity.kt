/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.SamsungDex
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/********** [VncActivity] startup helpers *********************************/

private const val PROFILE_KEY = "com.gaurav.avnc.server_profile"

fun createVncIntent(context: Context, profile: ServerProfile): Intent {
    return Intent(context, VncActivity::class.java).apply {
        putExtra(PROFILE_KEY, profile)
    }
}

fun startVncActivity(source: Activity, profile: ServerProfile) {
    source.startActivity(createVncIntent(source, profile))
}

fun startVncActivity(source: Activity, uri: VncUri) {
    startVncActivity(source, uri.toServerProfile())
}

/**************************************************************************/


/**
 * This activity handles the connection to a VNC server.
 */
class VncActivity : AppCompatActivity() {

    lateinit var viewModel: VncViewModel
    lateinit var binding: ActivityVncBinding
    private val dispatcher by lazy { Dispatcher(this) }
    val touchHandler by lazy { TouchHandler(viewModel, dispatcher) }
    val keyHandler by lazy { KeyHandler(dispatcher, viewModel.profile.fLegacyKeySym, viewModel.pref) }
    val virtualKeys by lazy { VirtualKeys(this) }
    private val serverUnlockPrompt = DeviceAuthPrompt(this)
    private val layoutManager by lazy { LayoutManager(this) }
    private var restoredFromBundle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)
        restoredFromBundle = savedInstanceState != null

        super.onCreate(savedInstanceState)
        if (!loadViewModel(savedInstanceState)) {
            finish()
            return
        }

        viewModel.initConnection()

        //Main UI
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.frameView.initialize(this)
        viewModel.frameViewRef = WeakReference(binding.frameView)

        setupLayout()
        setupDrawerLayout()
        setupServerUnlock()
        setupGestureStyle()

        //Buttons
        binding.keyboardBtn.setOnClickListener { showKeyboard(); closeDrawers() }
        binding.zoomOptions.setOnLongClickListener { resetZoomToDefault(); closeDrawers(); true }
        binding.zoomResetBtn.setOnClickListener { resetZoomToDefault(); closeDrawers() }
        binding.zoomResetBtn.setOnLongClickListener { resetZoom(); closeDrawers(); true }
        binding.zoomLockBtn.isChecked = viewModel.profile.fZoomLocked
        binding.zoomLockBtn.setOnCheckedChangeListener { _, checked -> toggleZoomLock(checked); closeDrawers() }
        binding.zoomSaveBtn.setOnClickListener { saveZoom(); closeDrawers() }
        binding.virtualKeysBtn.setOnClickListener { virtualKeys.show(); closeDrawers() }
        binding.reconnectBtn.setOnClickListener { retryConnection() }

        //Observers
        viewModel.loginInfoRequest.observe(this) { showLoginDialog() }
        viewModel.sshHostKeyVerifyRequest.observe(this) { showHostKeyDialog() }
        viewModel.state.observe(this) { onClientStateChanged(it) }
    }

    override fun onStart() {
        super.onStart()
        binding.frameView.onResume()
        viewModel.resumeFrameBufferUpdates()
    }

    override fun onStop() {
        super.onStop()
        virtualKeys.releaseMetaKeys()
        binding.frameView.onPause()
        viewModel.pauseFrameBufferUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(PROFILE_KEY, viewModel.profile)
    }

    private fun loadViewModel(savedState: Bundle?): Boolean {
        @Suppress("DEPRECATION")
        val profile = savedState?.getParcelable(PROFILE_KEY)
                      ?: intent.getParcelableExtra<ServerProfile?>(PROFILE_KEY)

        if (profile == null) {
            Toast.makeText(this, "Error: Missing Server Info", Toast.LENGTH_LONG).show()
            return false
        }

        val factory = viewModelFactory { initializer { VncViewModel(profile, application) } }
        viewModel = viewModels<VncViewModel> { factory }.value
        return true
    }

    private fun retryConnection() {
        //We simply create a new activity to force creation of new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
            startVncActivity(this, viewModel.profile)
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

    private fun setupGestureStyle() {
        val styleButtonMap = mapOf(
                "auto" to R.id.gesture_style_auto,
                "touchscreen" to R.id.gesture_style_touchscreen,
                "touchpad" to R.id.gesture_style_touchpad
        )

        binding.gestureStyleGroup.let { group ->
            group.check(styleButtonMap[viewModel.profile.gestureStyle] ?: -1)
            group.setOnCheckedChangeListener { _, id ->
                for ((k, v) in styleButtonMap)
                    if (v == id) viewModel.profile.gestureStyle = k
                viewModel.saveProfile()
                dispatcher.onGestureStyleChanged()
                closeDrawers()
            }
        }
    }

    private fun showLoginDialog() {
        LoginFragment().show(supportFragmentManager, "LoginDialog")
    }

    private fun showHostKeyDialog() {
        HostKeyFragment().show(supportFragmentManager, "HostKeyFragment")
    }

    private fun resetZoom() {
        viewModel.resetZoom()
        Toast.makeText(this, getString(R.string.msg_zoom_reset), Toast.LENGTH_SHORT).show()
    }

    private fun resetZoomToDefault() {
        viewModel.resetZoomToDefault()
        Toast.makeText(this, getString(R.string.msg_zoom_reset_default), Toast.LENGTH_SHORT).show()
    }

    private fun toggleZoomLock(enabled: Boolean) {
        viewModel.toggleZoomLock(enabled)
        Toast.makeText(this, getString(if (enabled) R.string.msg_zoom_locked else R.string.msg_zoom_unlocked), Toast.LENGTH_SHORT).show()
    }

    private fun saveZoom() {
        viewModel.saveZoom()
        Toast.makeText(this, getString(R.string.msg_zoom_saved), Toast.LENGTH_SHORT).show()
    }

    fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)

        virtualKeys.onKeyboardOpen()
    }

    private fun closeDrawers() {
        binding.drawerLayout.closeDrawers()
    }

    private fun onClientStateChanged(newState: VncViewModel.State) {
        val isConnected = (newState == VncViewModel.State.Connected)
        val drawerLockMode = if (isConnected) DrawerLayout.LOCK_MODE_UNDEFINED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED

        binding.drawerLayout.setDrawerLockMode(drawerLockMode)
        binding.frameView.isVisible = isConnected
        binding.frameView.keepScreenOn = isConnected
        SamsungDex.setMetaKeyCapture(this, isConnected)
        layoutManager.onConnectionStateChanged()
        updateStatusContainerVisibility(isConnected)
        highlightDrawer(isConnected)
        autoReconnect(newState)

        if (isConnected && !restoredFromBundle)
            incrementUseCount()
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

    // Highlight drawer for first time users
    private fun highlightDrawer(isConnected: Boolean) {
        if (isConnected && !viewModel.pref.runInfo.hasConnectedSuccessfully) {
            viewModel.pref.runInfo.hasConnectedSuccessfully = true
            binding.drawerLayout.openDrawer(binding.primaryToolbar)
            lifecycleScope.launch {
                delay(1500)
                binding.drawerLayout.closeDrawer(binding.primaryToolbar)
            }
        }
    }

    private var autoReconnecting = false
    private fun autoReconnect(state: VncViewModel.State) {
        if (autoReconnecting || state != VncViewModel.State.Disconnected || !viewModel.pref.server.autoReconnect)
            return

        autoReconnecting = true
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val timeout = 5 //seconds, must be >1
                repeat(timeout) {
                    binding.autoReconnectProgress.setProgressCompat((100 * it) / (timeout - 1), true)
                    delay(1000)
                    if (it >= (timeout - 1))
                        retryConnection()
                }
            }
        }
    }

    /************************************************************************************
     * Layout handling.
     ************************************************************************************/

    private val fullscreenMode by lazy { viewModel.pref.viewer.fullscreen }

    private fun setupLayout() {

        setupOrientation()
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

    private fun setupDrawerLayout() {
        binding.drawerLayout.setScrimColor(0)

        // Update Toolbar gravity
        val gravityH = if (viewModel.pref.viewer.toolbarAlignment == "start") Gravity.START else Gravity.END
        val lp = binding.primaryToolbar.layoutParams as DrawerLayout.LayoutParams

        @SuppressLint("WrongConstant")
        lp.gravity = gravityH or Gravity.CENTER_VERTICAL
        binding.primaryToolbar.layoutParams = lp

        setupDrawerCloseOnScrimSwipe(binding.drawerLayout, gravityH)

        //Add System Gesture exclusion rects to allow opening toolbar drawer by swiping from edge
        if (Build.VERSION.SDK_INT >= 29) {
            binding.primaryToolbar.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val root = binding.drawerLayout
                val rect = Rect(left, top, right, bottom)

                if (left < 0) rect.offset(-left, 0)
                if (right > root.width) rect.offset(-(right - root.width), 0)

                if (fullscreenMode) {
                    rect.top = 0
                    rect.bottom = root.height
                }

                //Set exclusion rects on root because toolbar may not be visible
                root.systemGestureExclusionRects = listOf(rect)
            }
        }

        // Close flyouts after drawer is closed
        // We can't do this when calling closeDrawers() because that will change drawer
        // size *while* drawer is closing. This can mess with internal calculations of DrawerLayout,
        // and close operation can fail.
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                binding.zoomOptions.isChecked = false
                binding.gestureStyleToggle.isChecked = false
            }
        })
    }

    /**
     * Normally, drawers in [DrawerLayout] are closed by two gestures:
     * 1. Swipe 'on' the drawer
     * 2. Tap inside Scrim (dimmed region outside of drawer)
     *
     * Notably, swiping inside scrim area does NOT hide the drawer. This can be jarring
     * to users if drawer is relatively small & most of the layout area acts as scrim.
     * The toolbar drawer is affected by this issue.
     *
     * This function attempts to detect these swipe gestures and close the drawer
     * when they happen.
     *
     * [drawerGravity] can be [Gravity.START] or [Gravity.END]
     *
     * Note: It will set a custom TouchListener on [drawerLayout].
     */
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun setupDrawerCloseOnScrimSwipe(drawerLayout: DrawerLayout, drawerGravity: Int) {

        drawerLayout.setOnTouchListener(object : View.OnTouchListener {
            var drawerOpen = false

            val detector = GestureDetector(drawerLayout.context, object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                    val absGravity = Gravity.getAbsoluteGravity(drawerGravity, drawerLayout.layoutDirection)
                    if ((absGravity == Gravity.LEFT && vX < 0) || (absGravity == Gravity.RIGHT && vX > 0)) {
                        drawerLayout.closeDrawer(drawerGravity)
                        drawerOpen = false
                    }
                    return true
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN)
                    drawerOpen = drawerLayout.isDrawerOpen(drawerGravity)

                if (drawerOpen)
                    detector.onTouchEvent(event)

                return false
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        layoutManager.onWindowFocusChanged(hasFocus)
        if (hasFocus) viewModel.sendClipboardText()
    }


    /************************************************************************************
     * Picture-in-Picture support
     ************************************************************************************/

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPiPMode()
    }

    @RequiresApi(26)
    override fun onPictureInPictureModeChanged(inPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPiP, newConfig)
        if (inPiP) {
            closeDrawers()
            viewModel.resetZoom()
            virtualKeys.hide()
        }
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
                Log.w(javaClass.simpleName, "Cannot enter PiP mode", e)
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