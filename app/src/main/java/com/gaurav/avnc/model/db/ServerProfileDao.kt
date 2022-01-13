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
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.model.ServerProfile

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM profiles")
    fun getLiveList(): LiveData<List<ServerProfile>>

    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE")
    fun getSortedLiveList(): LiveData<List<ServerProfile>>

    //Synchronous version
    @Query("SELECT * FROM profiles")
    fun getList(): List<ServerProfile>

    @Query("SELECT name, host, username, password FROM profiles")
    fun getCredentials(): LiveData<List<LoginInfo>>

    @Insert
    fun insert(profile: ServerProfile): Long

    @Insert
    fun insert(profiles: List<ServerProfile>)

    @Update
    fun update(profile: ServerProfile)

    @Delete
    fun delete(profile: ServerProfile)

    @Query("DELETE FROM profiles")
    fun deleteAll()
}