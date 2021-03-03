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
import android.content.res.Configuration
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.gl.Renderer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncClient
import com.gaurav.avnc.vnc.VncUri
import java.lang.ref.WeakReference

/********** [VncActivity] startup helpers *********************************/

private const val PROFILE_KEY = "com.gaurav.avnc.server_profile"

fun startVncActivity(source: Activity, profile: ServerProfile) {
    val intent = Intent(source, VncActivity::class.java)
    intent.putExtra(PROFILE_KEY, profile)
    source.startActivity(intent)
}

fun startVncActivity(source: Activity, uri: VncUri) {
    startVncActivity(source, uri.toServerProfile())
}

/**************************************************************************/


/**
 * This activity handle the VNC connection to a server.
 */
class VncActivity : AppCompatActivity() {

    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    val dispatcher by lazy { Dispatcher(viewModel) }
    val touchHandler by lazy { TouchHandler(viewModel, dispatcher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        setupLayout()

        //FrameView
        binding.frameView.activity = this
        binding.frameView.setEGLContextClientVersion(2)
        binding.frameView.setRenderer(Renderer(viewModel))
        binding.frameView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        binding.retryConnectionBtn.setOnClickListener { retryConnection() }

        //Drawers
        binding.drawerLayout.setScrimColor(0)
        binding.kbBtn.setOnClickListener { showKeyboard(); closeDrawers() }
        binding.zoomResetBtn.setOnClickListener { viewModel.resetZoom(); closeDrawers() }

        //ViewModel setup
        viewModel.frameViewRef = WeakReference(binding.frameView)
        viewModel.credentialRequiredEvent.observe(this) { showCredentialDialog() }
        viewModel.connect(getProfile()) //Should be called after observers has been setup
    }

    override fun onResume() {
        super.onResume()
        viewModel.sendClipboardText()
    }

    /**
     * Initializes layout handling.
     */
    private fun setupLayout() {

        if (viewModel.pref.display.fullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        binding.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            viewModel.frameState.setWindowSize(v.width.toFloat(), v.height.toFloat())
        }

        //This is used to handle cases where a system view (ex: soft keyboard) is covering
        //some part of our window. We retrieve the visible area and add padding to our
        //root view so that its content is resized to that area.
        //This will trigger the resize of frame view allowing it to handle the available space.
        val visibleFrame = Rect()
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            binding.root.getWindowVisibleDisplayFrame(visibleFrame)

            var paddingBottom = binding.root.bottom - visibleFrame.bottom
            if (paddingBottom < 0)
                paddingBottom = 0

            binding.root.updatePadding(bottom = paddingBottom)
        }
    }

    private fun getProfile(): ServerProfile {
        val profile = intent.getParcelableExtra<ServerProfile>(PROFILE_KEY)
        if (profile != null) {
            return profile
        }

        Log.e(javaClass.simpleName, "No connection information was passed through Intent.")
        return ServerProfile()
    }

    private fun retryConnection() {
        //We simply create a new activity to force creation of a new ViewModel
        //which effectively restarts the connection.
        if (!isFinishing) {
            startActivity(intent)
            finish()
        }
    }

    private fun showCredentialDialog() {
        CredentialFragment().show(supportFragmentManager, "CredentialDialog")
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)
    }

    private fun closeDrawers() = binding.drawerLayout.closeDrawers()


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
        }
    }

    private fun enterPiPMode() {
        val canEnter = viewModel.pref.display.pipEnabled && viewModel.client.state == VncClient.State.Connected

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
}