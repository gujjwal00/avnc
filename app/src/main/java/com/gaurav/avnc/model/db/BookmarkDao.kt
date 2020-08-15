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

@Dao
interface BookmarkDao {

    @Query("SELECT * from bookmarks")
    fun getAll(): LiveData<List<Bookmark>>

    @Insert
    fun insert(bookmark: Bookmark): Long

    @Update
    fun update(bookmark: Bookmark)

    @Delete
    fun delete(bookmark: Bookmark)
}