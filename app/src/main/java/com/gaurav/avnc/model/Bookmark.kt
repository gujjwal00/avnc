/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * Released under the terms of GPLv3 (or later).
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bookmark entity
 */
@Entity(tableName = "bookmarks")
data class Bookmark(

        @PrimaryKey(autoGenerate = true)
        var ID: Long = 0,

        @Embedded
        var profile: VncProfile = VncProfile()
) {
    /**
     * Creates a new `Recent` instance form this bookmark.
     */
    fun toRecent() = Recent(profile = profile.copy())
}
