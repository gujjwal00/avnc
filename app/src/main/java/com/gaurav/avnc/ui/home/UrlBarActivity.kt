/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.os.Bundle
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityUrlBinding
import com.gaurav.avnc.ui.vnc.startVncActivity
import com.gaurav.avnc.util.AppPreferences
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
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        val binding = DataBindingUtil.setContentView<ActivityUrlBinding>(this, R.layout.activity_url)

        binding.url.setOnEditorActionListener { _, _, _ -> go(binding.url.text.toString()) }
        binding.back.setOnClickListener { onBackPressed() }
        binding.clear.setOnClickListener {
            if (binding.url.text.isEmpty())
                finish()
            else
                binding.url.setText("")
        }

        binding.urlTip.isVisible = AppPreferences(this).ui.showTips
    }

    private fun go(url: String): Boolean {
        if (url.isBlank())
            return false

        val processed = processIPv6(url)
        val uri = VncUri(processed)

        if (uri.host.isEmpty()) {
            Toast.makeText(this, R.string.msg_invalid_vnc_uri, Toast.LENGTH_SHORT).show()
            return false
        }

        startVncActivity(this, uri)
        finish()
        return true
    }

    /**
     * For IPv6, [VncUri] expects host address to be wrapped in square brackets.
     * We apply some heuristics to detect IPv6 address and add brackets if they
     * are missing.
     */
    private fun processIPv6(url: String): String {
        //we only want to process IP address literals without path/query
        if (url.contains('/') || url.contains('?') || url.contains('#'))
            return url

        //might already contain brackets
        if (url.contains('[') || url.contains(']'))
            return url

        //handle most common cases
        if (url.contains("::") || url.count { it == ':' } > 2)
            return "[$url]"

        return url
    }
}