/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity to represent recent connections.
 */
@Entity(tableName = "recents", indices = [Index(value = ["displayName", "host", "port"], unique = true)])
data class Recent(

        @PrimaryKey(autoGenerate = true)
        var ID: Long = 0,

        @Embedded
        var profile: VncProfile = VncProfile(),

        /**
         * Time of this connection.
         * Represented as seconds since epoch.
         */
        var connectedAt: Long = 0
) {
    /**
     * Create a new `Bookmark` instance from this.
     */
    fun toBookmark() = Bookmark(profile = profile.copy())

}