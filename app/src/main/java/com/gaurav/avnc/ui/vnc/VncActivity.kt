/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.ui.vnc.gl.Renderer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncClient
import java.lang.ref.WeakReference

/**
 * This activity handle the VNC connection to a server.
 *
 * A VncProfile MUST be passed to this activity (via Intent) which will be
 * used to establish VNC connection.
 */
class VncActivity : AppCompatActivity() {

    object KEY {
        const val PROFILE = "com.gaurav.avnc.profile"
    }

    val viewModel by viewModels<VncViewModel>()
    lateinit var binding: ActivityVncBinding
    val dispatcher by lazy { Dispatcher(viewModel) }
    val inputHandler by lazy { InputHandler(this, dispatcher) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = getProfile()
        binding = DataBindingUtil.setContentView(this, R.layout.activity_vnc)
        binding.viewModel = viewModel
        binding.lifecycleOwner = this

        //Setup FrameView
        binding.frameView.activity = this
        binding.frameView.setEGLContextClientVersion(2)
        binding.frameView.setRenderer(Renderer(viewModel))
        binding.frameView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        viewModel.frameViewRef = WeakReference(binding.frameView)
        viewModel.credentialRequiredEvent.observe(this) { showCredentialDialog() }
        viewModel.clientInfo.observe(this, Observer { processClientInfo(it) })

        //Should be called after observers has been installed
        viewModel.connect(profile)
    }

    /**
     * Extracts profile from Intent.
     */
    private fun getProfile(): VncProfile = intent.getParcelableExtra(KEY.PROFILE)
            ?: throw IllegalStateException("No profile was passed to VncActivity")


    private fun processClientInfo(info: VncClient.Info) {

    }

    private fun showCredentialDialog() {
        CredentialFragment().show(supportFragmentManager, "CredentialDialog")
    }
}