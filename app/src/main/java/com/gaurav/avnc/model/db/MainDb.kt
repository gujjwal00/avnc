/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * Released under the terms of GPLv3 (or later).
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.model.Recent

@Database(entities = [Bookmark::class, Recent::class], version = 1)
abstract class MainDb : RoomDatabase() {
    abstract val bookmarkDao: BookmarkDao
    abstract val recentDao: RecentDao

    companion object {
        /**
         * Database singleton.
         */
        private var instance: MainDb? = null

        /**
         * Returns database singleton.
         * If database is not yet created then it will be created on first call.
         *
         * @param context
         * @return
         */
        @Synchronized
        fun getInstance(context: Context?): MainDb {
            if (instance == null) {
                instance = Room.databaseBuilder(context!!, MainDb::class.java, "main").build()
            }
            return instance!!
        }
    }
}