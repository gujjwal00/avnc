/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityUrlBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.vnc.VncUri

/**
 * Activity allowing user to directly connect to a server.
 *
 * Possible future improvements:
 * - Keep history of recent entries
 * - Show suggestions from saved profiles
 * - Show suggestions from discovered servers.
 */
class UrlActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityUrlBinding>(this, R.layout.activity_url)

        binding.back.setOnClickListener { onBackPressed() }
        binding.clear.setOnClickListener { binding.url.setText("") }
        binding.url.setOnEditorActionListener { _, _, _ -> go(binding.url.text.toString()) }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun go(url: String): Boolean {
        if (url.isBlank())
            return false

        val profile = ServerProfile(VncUri(url))
        val intent = Intent(this, VncActivity::class.java)
        intent.putExtra(VncActivity.KEY.PROFILE, profile)
        startActivity(intent)

        finish()
        return true
    }
}