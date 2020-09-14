/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

/**
 * Bookmark entity
 */
@Entity(tableName = "bookmarks")
@Parcelize
data class Bookmark(

        @PrimaryKey(autoGenerate = true)
        var ID: Long = 0,

        @Embedded
        var profile: VncProfile = VncProfile()
) : Parcelable {
    /**
     * Creates a new `Recent` instance form this bookmark.
     */
    fun toRecent() = Recent(profile = profile.copy())
}
