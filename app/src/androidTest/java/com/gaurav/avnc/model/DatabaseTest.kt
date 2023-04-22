/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import androidx.room.testing.MigrationTestHelper
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.model.db.MainDb
import org.junit.Rule
import org.junit.Test

class DatabaseTest {
    private val dbName = "Bond. James Bond."
    private val minVersion = 1
    private val maxVersion = 4

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MainDb::class.java)

    @Test
    fun migrations() {
        for (i in minVersion until maxVersion)
            for (j in i + 1..maxVersion)
                runCatching {
                    helper.createDatabase(dbName, i)
                    helper.runMigrationsAndValidate(dbName, j, false)
                }.onFailure {
                    throw Exception("Failed to migrate MainDb from [$i] to [$j]", it)
                }
    }
}