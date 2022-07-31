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
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.Experimental
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        //Buttons
        binding.keyboardBtn.setOnClickListener { showKeyboard(); closeDrawers() }
        binding.zoomResetBtn.setOnClickListener { viewModel.resetZoom(); closeDrawers() }
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

        cleanUp()

        binding.frameView.onPause()
    }

    private fun cleanUp() {
        virtualKeys.releaseMetaKeys()
    }

    private fun loadProfile(): ServerProfile {
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

    private fun showCredentialDialog() {
        CredentialFragment().show(supportFragmentManager, "CredentialDialog")
    }

    private fun showHostKeyDialog() {
        HostKeyFragment().show(supportFragmentManager, "HostKeyFragment")
    }

    fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)

        virtualKeys.onKeyboardOpen()
    }

    private fun closeDrawers() = binding.drawerLayout.closeDrawers()

    private fun onClientStateChanged(newState: VncViewModel.State) {
        if (newState == VncViewModel.State.Connected) {

            if (!viewModel.pref.runInfo.hasConnectedSuccessfully) {
                viewModel.pref.runInfo.hasConnectedSuccessfully = true

                // Highlight drawer for first time users
                binding.drawerLayout.open()
                lifecycleScope.launchWhenCreated {
                    delay(1500)
                    binding.drawerLayout.close()
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
    private val immersiveMode by lazy { viewModel.pref.experimental.immersiveMode }

    private fun setupLayout() {

        setupOrientation()

        @Suppress("DEPRECATION")
        if (fullscreenMode) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.setOnSystemUiVisibilityChangeListener { updateSystemUiVisibility() }
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
        lp.gravity = gravityH or Gravity.CENTER_VERTICAL
        binding.primaryToolbar.layoutParams = lp

        if (viewModel.pref.experimental.swipeCloseToolbar)
            Experimental.setupDrawerCloseOnScrimSwipe(binding.drawerLayout, gravityH)

        //Add System Gesture exclusion rects to allow opening toolbar drawer by swiping from edge
        if (Build.VERSION.SDK_INT >= 29) {
            binding.primaryToolbar.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val root = binding.drawerLayout
                val rect = Rect(left, top, right, bottom)

                if (left < 0) rect.offset(-left, 0)
                if (right > root.width) rect.offset(-(right - root.width), 0)

                if (immersiveMode) {
                    rect.top = 0
                    rect.bottom = root.height
                }

                //Set exclusion rects on root because toolbar may not be visible
                root.systemGestureExclusionRects = listOf(rect)
            }
        }
    }


    @Suppress("DEPRECATION")
    private fun updateSystemUiVisibility() {
        if (!fullscreenMode || !immersiveMode)
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

    override fun onPictureInPictureModeChanged(inPiP: Boolean, newConfig: Configuration?) {
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

            val fs = viewModel.frameState
            val aspectRatio = Rational(fs.fbWidth.toInt(), fs.fbHeight.toInt())
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