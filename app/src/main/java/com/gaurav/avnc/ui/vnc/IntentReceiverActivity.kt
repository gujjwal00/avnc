/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */
package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Handles "external" intents and launches [VncActivity] with appropriate profiles.
 *
 * Current intent types:
 *  - vnc:// URIs
 *  - App shortcuts
 */
class IntentReceiverActivity : AppCompatActivity() {

    companion object {
        private const val SHORTCUT_PROFILE_ID_KEY = "com.gaurav.avnc.shortcut_profile_id"

        fun createShortcutIntent(context: Context, profileId: Long): Intent {
            check(profileId != 0L) { "Cannot create shortcut with profileId = 0." }
            return Intent(context, IntentReceiverActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(SHORTCUT_PROFILE_ID_KEY, profileId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.data?.scheme == "vnc")
            launchFromVncUri(VncUri(intent.data!!))
        else if (intent.hasExtra(SHORTCUT_PROFILE_ID_KEY))
            launchFromProfileId(intent.getLongExtra(SHORTCUT_PROFILE_ID_KEY, 0))
        else
            handleUnknownIntent()
    }

    private fun launchFromVncUri(uri: VncUri) {
        if (uri.connectionName.isNotBlank())
            launchFromProfileName(uri.connectionName)
        else
            launchVncUri(uri)
    }

    private fun launchVncUri(uri: VncUri) {
        if (uri.host.isEmpty())
            toast(getString(R.string.msg_invalid_vnc_uri))
        else
            startVncActivity(this, uri)

        finish()
    }

    private fun launchFromProfileName(name: String) {
        val context = this
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MainDb.getInstance(context).serverProfileDao
            val profile = dao.getByName(name).firstOrNull()
            withContext(Dispatchers.Main) {
                if (profile == null)
                    toast("No server found with name '$name'")
                else
                    startVncActivity(context, profile)

                finish()
            }
        }
    }

    private fun launchFromProfileId(profileId: Long) {
        val context = this
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MainDb.getInstance(context).serverProfileDao
            val profile = dao.getByID(profileId)
            withContext(Dispatchers.Main) {
                if (profile == null)
                    toast(getString(R.string.msg_shortcut_server_deleted))
                else
                    startVncActivity(context, profile)
                finish()
            }
        }
    }

    private fun handleUnknownIntent() {
        toast("Invalid intent: Server info is missing!")
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}