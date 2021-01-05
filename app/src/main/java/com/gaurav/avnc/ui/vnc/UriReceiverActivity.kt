/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */
package com.gaurav.avnc.ui.vnc

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.vnc.VncUri

/**
 * Handles intents with 'vnc' URI.
 *
 * TODO:
 * - Add a prompt before connecting.
 * - Add ability to save connection info.
 */
class UriReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = getUri()
        startVncActivity(this, uri)

        finish()
    }

    private fun getUri(): VncUri {
        if (intent.data?.scheme == "vnc") {
            return VncUri(intent.data!!)
        }

        Log.e(javaClass.simpleName, "Invalid intent!")
        return VncUri("")
    }
}