/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gaurav.avnc.model.ServerProfile

@Database(entities = [ServerProfile::class], version = 1)
abstract class MainDb : RoomDatabase() {
    abstract val serverProfileDao: ServerProfileDao

    companion object {
        /**
         * Database singleton.
         */
        private var instance: MainDb? = null

        /**
         * Returns database singleton.
         * If database is not yet created then it will be created on first call.
         */
        @Synchronized
        fun getInstance(context: Context): MainDb {
            if (instance == null) {
                instance = Room.databaseBuilder(context, MainDb::class.java, "main").build()
            }
            return instance!!
        }
    }
}