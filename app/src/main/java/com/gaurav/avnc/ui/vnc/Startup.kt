/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.model.db.ServerProfileDao
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.VncUri


/**********************************************************************************************
 * [VncActivity] startup utilities
 *********************************************************************************************/
private const val PROFILE_KEY = "com.gaurav.avnc.server_profile"
private const val PROFILE_ID_KEY = "com.gaurav.avnc.server_profile_id"

fun createVncIntent(context: Context, profile: ServerProfile): Intent {
    return Intent(context, VncActivity::class.java).apply {
        if (profile.isSaved())
            putExtra(PROFILE_ID_KEY, profile.ID)
        else
            putExtra(PROFILE_KEY, profile)
    }
}

fun startVncActivity(source: Activity, profile: ServerProfile) {
    source.startActivity(createVncIntent(source, profile))
}

fun startVncActivity(source: Activity, uri: VncUri) {
    startVncActivity(source, uri.toServerProfile())
}


/**********************************************************************************************
 * Argument parser
 *********************************************************************************************/
sealed class StartupArg {
    data class Profile(val profile: ServerProfile) : StartupArg()
    data class ProfileId(val id: Long) : StartupArg()
}

class MissingStartupArgException : Exception()

fun parseStartupArg(intent: Intent, savedState: Bundle?): StartupArg {
    // Prefer to use profile if available to keep changes across activity restarts.
    @Suppress("DEPRECATION")
    val profile = savedState?.getParcelable(PROFILE_KEY)
                  ?: intent.getParcelableExtra<ServerProfile?>(PROFILE_KEY)
    if (profile != null)
        return StartupArg.Profile(profile.copy())  //Create a copy to avoid modification to source profile


    val id = intent.getLongExtra(PROFILE_ID_KEY, 0)
    if (id != 0L)
        return StartupArg.ProfileId(id)

    throw MissingStartupArgException()
}

/**********************************************************************************************
 * Session start
 *********************************************************************************************/

class InvalidProfileIdException(val id: Long) : Exception("Error: Invalid Server ID")

suspend fun startSession(startupArg: StartupArg, viewModel: VncViewModel) {
    val dao = MainDb.getInstance(viewModel.app).serverProfileDao
    val profile = loadProfile(startupArg, dao)
    viewModel.initConnection(profile)
}

private suspend fun loadProfile(startupArg: StartupArg, dao: ServerProfileDao): ServerProfile {
    when (startupArg) {
        is StartupArg.Profile -> return startupArg.profile
        is StartupArg.ProfileId -> return dao.getByID(startupArg.id)
                                          ?: throw InvalidProfileIdException(startupArg.id)
    }
}