/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */
package com.gaurav.avnc.ui.vnc

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.gaurav.avnc.R
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.vnc.VncUri
import kotlinx.coroutines.runBlocking

/**
 * Handles intents with 'vnc' URI.
 *
 * TODO:
 * - Add a prompt before connecting.
 * - Add ability to save connection info.
 */
class UriReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = getUri()

        if (uri.connectionName.isNotBlank())
            launchSavedProfile(uri.connectionName)
        else
            launchUri(uri)

        finish()
    }

    private fun getUri(): VncUri {
        if (intent.data?.scheme != "vnc") {
            Log.e(javaClass.simpleName, "Invalid intent!")
            return VncUri("")
        }

        return VncUri(intent.data!!)
    }

    private fun launchUri(uri: VncUri) {
        if (uri.host.isEmpty())
            Toast.makeText(this, R.string.msg_invalid_vnc_uri, Toast.LENGTH_LONG).show()
        else
            startVncActivity(this, uri)
    }

    private fun launchSavedProfile(name: String) {
        val dao = MainDb.getInstance(this).serverProfileDao
        val profile = runBlocking { dao.getByName(name) }.firstOrNull() // Blocking IO on Main thread

        if (profile == null)
            Toast.makeText(this, "No server found with name '$name'", Toast.LENGTH_LONG).show()
        else
            startVncActivity(this, profile)
    }
}