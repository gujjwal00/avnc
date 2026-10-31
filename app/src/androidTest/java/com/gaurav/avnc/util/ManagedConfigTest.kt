/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManagedConfigTest {

    @Test
    fun groupsAreFlattened() {
        val restrictions = Bundle().apply {
            putString("theme", "dark")
            putBundle("viewer", Bundle().apply {
                putString("viewer_orientation", "portrait")
                putBundle("nested", Bundle().apply { putInt("level", 2) })
            })
        }

        val flat = restrictions.flattenGroups()

        assertEquals("dark", flat.getString("theme"))
        assertEquals("portrait", flat.getString("viewer_orientation"))
        assertEquals(2, flat.getInt("level"))
        assertFalse(flat.containsKey("viewer"))
        assertFalse(flat.containsKey("nested"))
    }
}
