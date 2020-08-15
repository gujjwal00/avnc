/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityVncBinding
import com.gaurav.avnc.model.VncProfile

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = getProfile()
        val binding = DataBindingUtil.setContentView<ActivityVncBinding>(this, R.layout.activity_vnc)

        binding.msg.text = profile.displayName

    }

    /**
     * Extracts profile from Intent.
     */
    private fun getProfile(): VncProfile = intent.getParcelableExtra(KEY.PROFILE)
            ?: throw IllegalStateException("No profile was passed to VncActivity")

}