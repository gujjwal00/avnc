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
import com.gaurav.avnc.model.Recent

@Dao
interface RecentDao {

    /**
     * Returned items are sorted by time of connection so that most recent
     * connection appears on top.
     */
    @Query("SELECT * FROM recents ORDER BY connectedAt DESC")
    fun getAll(): LiveData<List<Recent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recent: Recent): Long

    @Delete
    fun delete(recent: Recent)

    @Query("DELETE FROM recents")
    fun deleteAll()
}