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
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.vnc.UserCredential

@Dao
interface ServerProfileDao {

    @Query("SELECT * FROM profiles")
    fun getAllLive(): LiveData<List<ServerProfile>>

    //Synchronous version
    @Query("SELECT * FROM profiles")
    fun getAll(): List<ServerProfile>

    @Query("SELECT username, password FROM profiles")
    fun getCredentials(): LiveData<List<UserCredential>>

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