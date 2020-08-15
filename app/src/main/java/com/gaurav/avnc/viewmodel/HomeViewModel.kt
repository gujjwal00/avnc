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
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.model.Recent
import com.gaurav.avnc.model.VncProfile

class HomeViewModel(application: Application) : BaseViewModel(application) {
    /**
     * Vnc server url. Used by the top URL box.
     */
    val serverUrl = MutableLiveData("")

    /**
     * Bookmark list
     */
    val bookmarks by lazy { bookmarkDao.getAll() }

    /**
     * Recent connections list
     */
    val recents by lazy { recentDao.getAll() }

    /**
     * List of discovered servers
     */
    val discoveredServers = ArrayList<VncProfile>()

    /**
     * Whether we are currently searching for new servers using AutoDiscovery.
     */
    val isDiscoveringServers = MutableLiveData(false)

    /**
     * This event is used for editing/creating bookmarks.
     *
     * Home activity observes this event and shows Bookmark editor when it is set.
     * Bookmark editor will update/insert the Bookmark instance of this event to database.
     */
    val bookmarkEditEvent = LiveEvent<Bookmark>()

    /**
     * Used for notifying observers when a bookmark is deleted.
     * This is used for notifying the user and potentially
     * undo this deletion.
     */
    val bookmarkDeletedEvent = LiveEvent<Bookmark>()

    /**
     * Used for starting new VNC connections.
     */
    val newConnectionEvent = LiveEvent<VncProfile>()

    /**
     * Starts creating a new bookmark based on the given source.
     */
    fun onNewBookmark(source: Bookmark = Bookmark()) = onEditBookmark(source)


    /**
     * Starts editing given bookmark.
     */
    fun onEditBookmark(bookmark: Bookmark) = bookmarkEditEvent.set(bookmark)

    /**
     * Starts creating a copy of the given bookmark.
     */
    fun onDuplicateBookmark(original: Bookmark) {
        val duplicate = original.copy(ID = 0, profile = original.profile.copy())
        duplicate.profile.displayName += " (Copy)"
        onEditBookmark(duplicate)
    }

    /**
     * Starts new connection to given profile.
     */
    fun startConnection(vncProfile: VncProfile) = newConnectionEvent.set(vncProfile)

    /**
     * Inserts given bookmark in database asynchronously.
     */
    fun insertBookmark(bookmark: Bookmark) = async { bookmarkDao.insert(bookmark) }

    /**
     * Updates given bookmark asynchronously.
     */
    fun updateBookmark(bookmark: Bookmark) = async { bookmarkDao.update(bookmark) }

    /**
     * Deletes given bookmark
     */
    fun deleteBookmark(bookmark: Bookmark) = async({ bookmarkDao.delete(bookmark) }, {
        bookmarkDeletedEvent.set(bookmark)
    })

    /**
     * Deletes given Recent entity asynchronously.
     */
    fun deleteRecent(recent: Recent) = async { recentDao.delete(recent) }


}