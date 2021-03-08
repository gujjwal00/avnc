/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import com.gaurav.avnc.model.db.MainDb
import org.junit.rules.ExternalResource

/**
 * JUnit rule preparing database for tests.
 * Database is cleared before each test.
 * It also provides access to database instance through [db]
 */
class DatabaseRule : ExternalResource() {
    val db by lazy { MainDb.getInstance(targetContext) }

    override fun before() {
        db.serverProfileDao.deleteAll()
    }
}