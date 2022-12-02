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
import android.view.WindowInsets.Type
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.SamsungDex
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncUri
import kotlinx.coroutines.delay
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

    private lateinit var profile: ServerProfile
    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    private val dispatcher by lazy { Dispatcher(this) }
    val touchHandler by lazy { TouchHandler(viewModel, dispatcher) }
    val keyHandler by lazy { KeyHandler(dispatcher, profile.keyCompatMode, viewModel.pref) }
    private val virtualKeys by lazy { VirtualKeys(this) }
    private val serverUnlockPrompt = DeviceAuthPrompt(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)

        super.onCreate(savedInstanceState)
        profile = loadProfile()
        viewModel.initConnection(profile)

        //Main UI
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        binding.frameView.initialize(this)
        viewModel.frameViewRef = WeakReference(binding.frameView)

        setupLayout()
        setupDrawerLayout()
        setupServerUnlock()

        //Buttons
        binding.keyboardBtn.setOnClickListener { showKeyboard(); closeDrawers() }
        binding.zoomOptions.setOnLongClickListener { resetZoom(); closeDrawers(); true }
        binding.zoomResetBtn.setOnClickListener { resetZoom(); closeDrawers() }
        binding.zoomSaveBtn.setOnClickListener { saveZoom(); closeDrawers() }
        binding.virtualKeysBtn.setOnClickListener { virtualKeys.show(); closeDrawers() }
        binding.retryConnectionBtn.setOnClickListener { retryConnection() }

        //Observers
        viewModel.credentialRequest.observe(this) { showCredentialDialog() }
        viewModel.sshHostKeyVerifyRequest.observe(this) { showHostKeyDialog() }
        viewModel.state.observe(this) { onClientStateChanged(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.sendClipboardText()
    }

    override fun onStart() {
        super.onStart()
        binding.frameView.onResume()
    }

    override fun onStop() {
        super.onStop()
        virtualKeys.releaseMetaKeys()
        binding.frameView.onPause()
    }

    private fun loadProfile(): ServerProfile {
        @Suppress("DEPRECATION")
        val profile = intent.getParcelableExtra<ServerProfile>(PROFILE_KEY)
        check(profile != null) { "ServerProfile is missing from VncActivity Intent" }

        // Make a copy to avoid modifying intent's instance,
        // because we may need the original if we have to retry connection.
        return profile.copy()
    }

    private fun retryConnection() {
        //We simply create a new activity to force creation of new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
            startActivity(intent)
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

    private fun showCredentialDialog() {
        CredentialFragment().show(supportFragmentManager, "CredentialDialog")
    }

    private fun showHostKeyDialog() {
        HostKeyFragment().show(supportFragmentManager, "HostKeyFragment")
    }

    private fun resetZoom() {
        viewModel.resetZoom()
        Toast.makeText(this, getString(R.string.msg_zoom_reset), Toast.LENGTH_SHORT).show()
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
        binding.zoomOptions.isChecked = false
        binding.drawerLayout.closeDrawers()
    }

    private fun onClientStateChanged(newState: VncViewModel.State) {
        if (newState == VncViewModel.State.Connected) {

            if (!viewModel.pref.runInfo.hasConnectedSuccessfully) {
                viewModel.pref.runInfo.hasConnectedSuccessfully = true

                // Highlight drawer for first time users
                binding.drawerLayout.openDrawer(binding.primaryToolbar)
                lifecycleScope.launchWhenCreated {
                    delay(1500)
                    binding.drawerLayout.closeDrawer(binding.primaryToolbar)
                }
            }

            SamsungDex.setMetaKeyCapture(this, true)
        } else {
            SamsungDex.setMetaKeyCapture(this, false)
        }

        updateSystemUiVisibility()
    }

    /************************************************************************************
     * Layout handling.
     ************************************************************************************/

    private val fullscreenMode by lazy { viewModel.pref.viewer.fullscreen }

    private fun setupLayout() {

        setupOrientation()

        @Suppress("DEPRECATION")
        if (fullscreenMode) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.setOnSystemUiVisibilityChangeListener { updateSystemUiVisibility() }
            window.decorView.setOnApplyWindowInsetsListener { v, insets ->
                maybeToggleNavigationBar(insets)
                v.onApplyWindowInsets(insets)
            }
        }

        binding.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            viewModel.frameState.setWindowSize(v.width.toFloat(), v.height.toFloat())
        }

        //This is used to handle cases where a system view (e.g. soft keyboard) is covering
        //some part of our window. We retrieve the visible area and add padding to our
        //root view so that its content is resized to that area.
        //This will trigger the resize of frame view allowing it to handle the available space.
        val visibleFrame = Rect()
        val rootLocation = intArrayOf(0, 0)
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            binding.root.getWindowVisibleDisplayFrame(visibleFrame)

            // Normally, the root view will cover the whole screen, but on devices
            // with display-cutout it will be letter-boxed by the system.
            // In that case the root view won't start from (0,0).
            // So we have to offset the visibleFame (which is in display coordinates)
            // to make sure it is relative to our root view.
            binding.root.getLocationOnScreen(rootLocation)
            visibleFrame.offset(-rootLocation[0], -rootLocation[1])

            var paddingBottom = binding.root.bottom - visibleFrame.bottom
            if (paddingBottom < 0)
                paddingBottom = 0

            //Try to guess if keyboard is closing
            if (paddingBottom == 0 && binding.root.paddingBottom != 0)
                virtualKeys.onKeyboardClose()

            binding.root.updatePadding(bottom = paddingBottom)
        }

        if (Build.VERSION.SDK_INT >= 28 && viewModel.pref.viewer.drawBehindCutout) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun maybeToggleNavigationBar(insets: WindowInsets) {
        // On API 30, Android doesn't automatically show the navigation bar with keyboard.
        // So to hide the keyboard, user has to first swipe to un-hide the navigation bar
        // and then tap on Back button. To avoid this, we manually show the navigation bar
        // whenever keyboard is visible. Although only API 30 seems to be affected, fix is
        // applied on 30+ APIs, to ensure consistency.
        if (Build.VERSION.SDK_INT >= 30) {
            if (insets.isVisible(Type.ime())) {
                window.insetsController?.show(Type.navigationBars())
            } else if (viewModel.client.connected) {
                window.insetsController?.hide(Type.navigationBars())
            }
        }
    }

    private fun setupOrientation() {
        requestedOrientation = when (viewModel.pref.viewer.orientation) {
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

                override fun onFling(e1: MotionEvent, e2: MotionEvent, vX: Float, vY: Float): Boolean {
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

    @Suppress("DEPRECATION")
    private fun updateSystemUiVisibility() {
        if (!fullscreenMode)
            return

        val flags = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        window.decorView.apply {
            if (viewModel.client.connected)
                systemUiVisibility = systemUiVisibility or flags
            else
                systemUiVisibility = systemUiVisibility and flags.inv()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updateSystemUiVisibility()
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
            InputDevice.getDevice(keyEvent.deviceId).supportsSource(InputDevice.SOURCE_MOUSE) &&
            viewModel.pref.input.interceptMouseBack) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN)
                touchHandler.onMouseBack()
            return true
        }
        return false
    }
}