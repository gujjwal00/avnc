/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.ui.vnc.startVncActivity
import kotlinx.coroutines.runBlocking


/**
 * Used for launching app shortcuts.
 */
class ShortcutActivity : AppCompatActivity() {
    companion object {
        private const val PROFILE_ID_KEY = "com.gaurav.avnc.SHORTCUT_PROFILE_ID"

        fun createIntent(context: Context, profileId: Long) = Intent(context, ShortcutActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(PROFILE_ID_KEY, profileId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getLongExtra(PROFILE_ID_KEY, 0) ?: 0

        if (id == 0L)
            toast("Invalid shortcut ID")
        else
            runCatching {
                val dao = MainDb.getInstance(this).serverProfileDao
                val profile = runBlocking { dao.getByID(id) } // Blocking IO on Main thread
                if (profile == null)
                    toast("This server has been deleted")
                else
                    startVncActivity(this, profile)
            }.onFailure {
                Log.e(javaClass.simpleName, "Unable to launch shortcut [ID: $id]", it)
                toast("Unable to launch shortcut")
            }

        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}