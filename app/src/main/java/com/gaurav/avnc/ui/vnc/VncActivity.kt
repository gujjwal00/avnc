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
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.ui.vnc.gl.Renderer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncClient
import java.lang.ref.WeakReference

/**
 * This activity handle the VNC connection to a server.
 *
 * A [Bookmark] MUST be passed to this activity (via Intent) which will be
 * used to establish VNC connection.
 */
class VncActivity : AppCompatActivity() {

    object KEY {
        const val BOOKMARK = "com.gaurav.avnc.bookmark"
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

        //Setup FrameView
        binding.frameView.activity = this
        binding.frameView.setEGLContextClientVersion(2)
        binding.frameView.setRenderer(Renderer(viewModel))
        binding.frameView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        viewModel.frameViewRef = WeakReference(binding.frameView)
        viewModel.credentialRequiredEvent.observe(this) { showCredentialDialog() }
        viewModel.clientInfo.observe(this, Observer { processClientInfo(it) })

        //Should be called after observers has been installed
        viewModel.connect(getBookmark())

        binding.kbToggle.setOnClickListener { toggleKb() }
    }

    /**
     * Extracts profile from Intent.
     */
    private fun getBookmark(): Bookmark = intent.getParcelableExtra(KEY.BOOKMARK)
            ?: throw IllegalStateException("No bookmark was passed to VncActivity")


    private fun processClientInfo(info: VncClient.Info) {

    }

    private fun showCredentialDialog() {
        CredentialFragment().show(supportFragmentManager, "CredentialDialog")
    }

    private fun toggleKb() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        binding.frameView.requestFocus()
        imm.showSoftInput(binding.frameView, 0)
    }
}