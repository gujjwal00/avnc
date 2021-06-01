/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.vnc.Discovery

class HomeViewModel(app: Application) : BaseViewModel(app) {

    /**
     * [ServerProfile]s stored in database.
     */
    val serverProfiles by lazy { serverProfileDao.getAllLive() }

    /**
     * Used to find new servers.
     */
    val discovery by lazy { Discovery(app) }

    /**
     * Used for starting new VNC connections.
     */
    val newConnectionEvent = LiveEvent<ServerProfile>()

    /**
     * This event is used for editing/creating server profiles.
     * Home activity observes this event and starts profile editor when it is fired.
     */
    val editProfileEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a new profile is saved to database.
     * Can be used to highlight the new profile in UI.
     */
    val profileInsertedEvent = LiveEvent<ServerProfile>()

    /**
     * Fired when a profile is deleted from database.
     * This is used for notifying the user and potentially undo the deletion.
     */
    val profileDeletedEvent = LiveEvent<ServerProfile>()


    init {
        if (pref.server.discoveryAutoStart)
            startDiscovery()
    }

    /**
     * Starts new connection to given profile.
     */
    fun startConnection(profile: ServerProfile) = newConnectionEvent.fire(profile)

    /**
     * Starts discovery service
     */
    fun startDiscovery() = discovery.start(viewModelScope, pref.server.discoveryTimeout)


    /**************************************************************************
     * Profile editing/creating
     *
     * These are invoked from UI on user actions. We simply fire [editProfileEvent]
     * with appropriate profile, causing the profile editor to be shown.
     *
     * NOTE: We need to make a copy of given profile because the instance
     * given to [editProfileEvent] can be modified by the editor.
     **************************************************************************/

    fun onNewProfile() = editProfileEvent.fire(ServerProfile())
    fun onNewProfile(source: ServerProfile) = editProfileEvent.fire(source.copy(ID = 0))
    fun onEditProfile(profile: ServerProfile) = editProfileEvent.fire(profile.copy())

    fun onDuplicateProfile(profile: ServerProfile) {
        val duplicate = profile.copy(ID = 0)
        duplicate.name += " (Copy)"
        editProfileEvent.fire(duplicate)
    }

    /**************************************************************************
     * Profile persistence
     *
     * These operations are asynchronous.
     **************************************************************************/

    fun insertProfile(profile: ServerProfile) = asyncIO({ serverProfileDao.insert(profile) }, {
        profileInsertedEvent.fire(profile)
    })

    fun updateProfile(profile: ServerProfile) = asyncIO { serverProfileDao.update(profile) }
    fun deleteProfile(profile: ServerProfile) = asyncIO({ serverProfileDao.delete(profile) }, {
        profileDeletedEvent.fire(profile)
    })
}