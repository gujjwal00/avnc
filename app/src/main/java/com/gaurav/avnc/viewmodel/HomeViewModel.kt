/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.vnc.Discovery

class HomeViewModel(app: Application) : BaseViewModel(app) {
    /**
     * Vnc server url. Used by the top URL box.
     */
    val serverUrl = MutableLiveData("")

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

    /**
     * Starts creating a new server profile.
     */
    fun onNewProfile(source: ServerProfile = ServerProfile()) = onEditProfile(source)

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
     * Saves given profile in database asynchronously.
     */
    fun saveProfile(profile: ServerProfile) = async { serverProfileDao.insertOrUpdate(profile) }

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
    fun startDiscovery() = discovery.start(viewModelScope)
}