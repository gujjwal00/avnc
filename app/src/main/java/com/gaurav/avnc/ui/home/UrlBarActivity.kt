/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityUrlBinding
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.util.layoutBehindStatusBar
import com.gaurav.avnc.vnc.VncUri

/**
 * Activity allowing user to directly connect to a server.
 *
 * Possible future improvements:
 * - Keep history of recent entries
 * - Show suggestions from saved profiles
 * - Show suggestions from discovered servers.
 */
class UrlBarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityUrlBinding>(this, R.layout.activity_url)

        binding.back.setOnClickListener { onBackPressed() }
        binding.clear.setOnClickListener { binding.url.setText("") }
        binding.url.setOnEditorActionListener { _, _, _ -> go(binding.url.text.toString()) }
    }

    override fun onResume() {
        super.onResume()
        layoutBehindStatusBar(window.decorView)
    }

    private fun go(url: String): Boolean {
        if (url.isBlank())
            return false

        startVncActivity(this, VncUri(url))

        finish()
        return true
    }
}