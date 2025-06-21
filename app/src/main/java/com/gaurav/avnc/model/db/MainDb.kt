/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gaurav.avnc.model.ServerProfile

@Database(entities = [ServerProfile::class], version = MainDb.VERSION, exportSchema = true, autoMigrations = [
    AutoMigration(from = 1, to = 2, spec = MainDb.MigrationSpec1to2::class),  // in v2.0.0
    AutoMigration(from = 2, to = 3, spec = MainDb.MigrationSpec2to3::class),  // in v2.1.0
    AutoMigration(from = 3, to = 4),                                          // in v2.2.2
    AutoMigration(from = 4, to = 5, spec = MainDb.MigrationSpec4to5::class),  // in v2.3.0
    AutoMigration(from = 5, to = 6),                                          // in v2.x.x
    AutoMigration(from = 6, to = 7, spec = MainDb.MigrationSpec6to7::class),  // in v3.0.0                                          // in v2.x.x
])
abstract class MainDb : RoomDatabase() {
    abstract val serverProfileDao: ServerProfileDao

    companion object {
        /**
         * Current database version
         */
        const val VERSION = 7

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

    /******************************** Migrations ***********************************/
    // Added in v2.0.0
    class MigrationSpec1to2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE profiles SET imageQuality = 5")
        }
    }

    // Added in v2.1.0
    @RenameColumn(tableName = "profiles", fromColumnName = "keyCompatMode", toColumnName = "compatFlags")
    class MigrationSpec2to3 : AutoMigrationSpec

    // Added in v2.3.0
    @RenameColumn(tableName = "profiles", fromColumnName = "compatFlags", toColumnName = "flags")
    @RenameColumn(tableName = "profiles", fromColumnName = "shortcutRank", toColumnName = "useCount")
    class MigrationSpec4to5 : AutoMigrationSpec

    // Added in v3.0.0
    @RenameColumn(tableName = "profiles", fromColumnName = "viewOnly", toColumnName = "viewMode")
    @DeleteColumn(tableName = "profiles", columnName = "sshPrivateKeyPassword")
    class MigrationSpec6to7 : AutoMigrationSpec
}