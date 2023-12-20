/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import com.gaurav.avnc.model.db.MainDb
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * JUnit rule to clear database before running tests.
 * It also provides access to database instance through [db]
 */
class EmptyDatabaseRule : ExternalResource() {
    val db by lazy { MainDb.getInstance(targetContext) }

    override fun before() {
        runBlocking { db.serverProfileDao.deleteAll() }
    }
}