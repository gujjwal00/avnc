/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.vnc.UserCredential

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks")
    fun getAll(): LiveData<List<Bookmark>>

    @Query("SELECT username, password FROM bookmarks")
    fun getCredentials(): LiveData<List<UserCredential>>

    @Insert
    fun insert(bookmark: Bookmark): Long

    @Update
    fun update(bookmark: Bookmark)

    @Delete
    fun delete(bookmark: Bookmark)
}