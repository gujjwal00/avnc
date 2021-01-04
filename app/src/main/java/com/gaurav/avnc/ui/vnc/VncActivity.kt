/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.gl.Renderer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncUri
import java.lang.ref.WeakReference

/**
 * This activity handle the VNC connection to a server.
 */
class VncActivity : AppCompatActivity() {

    object KEY {
        const val PROFILE = "com.gaurav.avnc.server_profile"
    }

    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    val dispatcher by lazy { Dispatcher(viewModel) }
    val inputHandler by lazy { InputHandler(viewModel, dispatcher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        //FrameView
        binding.frameView.activity = this
        binding.frameView.setEGLContextClientVersion(2)
        binding.frameView.setRenderer(Renderer(viewModel))
        binding.frameView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        //Drawers
        binding.drawerLayout.setScrimColor(0)

        //Drawer Buttons
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

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START) ||
                binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Extracts profile from Intent.
     *
     * Currently there are two sources of [VncActivity] start:
     *  1. HomeActivity
     *  2. VNC Uri Intent
     */
    private fun getProfile(): ServerProfile {

        val profile = intent.getParcelableExtra<ServerProfile>(KEY.PROFILE)
        if (profile != null) {
            return profile
        }

        if (intent.data?.scheme == "vnc") {
            return ServerProfile(VncUri(intent.data!!))
        }

        Log.e(javaClass.simpleName, "No connection information was passed through Intent.")
        return ServerProfile()
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
}