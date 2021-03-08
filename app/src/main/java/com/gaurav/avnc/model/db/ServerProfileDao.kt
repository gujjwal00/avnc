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
    fun getAll(): LiveData<List<ServerProfile>>

    @Query("SELECT username, password FROM profiles")
    fun getCredentials(): LiveData<List<UserCredential>>

    @Insert
    fun insert(profile: ServerProfile): Long

    @Update
    fun update(profile: ServerProfile)

    @Delete
    fun delete(profile: ServerProfile)
}