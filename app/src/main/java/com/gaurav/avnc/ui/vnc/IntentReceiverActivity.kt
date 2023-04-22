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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.vnc.VncUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles intents with 'vnc' URI.
 */
class IntentReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = getUri()

        if (uri.connectionName.isNotBlank())
            launchSavedProfile(uri.connectionName)
        else
            launchUri(uri)
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

        finish()
    }

    private fun launchSavedProfile(name: String) {
        val context = this
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MainDb.getInstance(context).serverProfileDao
            val profile = dao.getByName(name).firstOrNull()
            withContext(Dispatchers.Main) {
                if (profile == null)
                    Toast.makeText(context, "No server found with name '$name'", Toast.LENGTH_LONG).show()
                else
                    startVncActivity(context, profile)

                finish()
            }
        }
    }
}