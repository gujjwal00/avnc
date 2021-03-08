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
    val serverProfiles by lazy { serverProfileDao.getAll() }

    /**
     * Used to find new servers.
     */
    val discovery by lazy { Discovery(app) }

    /**
     * This event is used for editing/creating server profiles.
     *
     * Home activity observes this event and starts profile editor when it is fired.
     */
    val profileEditEvent = LiveEvent<ServerProfile>()

    /**
     * Used for notifying observers when a profile is deleted.
     * This is used for notifying the user and potentially
     * undo this deletion.
     */
    val profileDeletedEvent = LiveEvent<ServerProfile>()

    /**
     * Used for starting new VNC connections.
     */
    val newConnectionEvent = LiveEvent<ServerProfile>()

    init {
        if (pref.server.discoveryAutoStart)
            startDiscovery()
    }

    /**
     * Starts creating a new server profile.
     */
    fun onNewProfile() = onNewProfile(ServerProfile())

    /**
     * Starts creating a new profile using [source] as starting point.
     */
    fun onNewProfile(source: ServerProfile) = onEditProfile(source)

    /**
     * Starts editing given profile.
     */
    fun onEditProfile(profile: ServerProfile) = profileEditEvent.set(profile)

    /**
     * Starts creating a copy of the given profile.
     */
    fun onDuplicateProfile(original: ServerProfile) {
        val duplicate = original.copy(ID = 0)
        duplicate.name += " (Copy)"
        onEditProfile(duplicate)
    }

    /**
     * Inserts given profile in database.
     */
    fun insertProfile(profile: ServerProfile) = async { serverProfileDao.insert(profile) }

    /**
     * Saves given profile to database.
     */
    fun updateProfile(profile: ServerProfile) = async { serverProfileDao.update(profile) }

    /**
     * Deletes given profile
     */
    fun deleteProfile(profile: ServerProfile) = async({ serverProfileDao.delete(profile) }, {
        profileDeletedEvent.set(profile)
    })

    /**
     * Starts new connection to given profile.
     */
    fun startConnection(profile: ServerProfile) = newConnectionEvent.set(profile)

    /**
     * Starts discovery service
     */
    fun startDiscovery() = discovery.start(viewModelScope, pref.server.discoveryTimeout)
}