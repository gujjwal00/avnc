/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.pref

import android.net.Uri
import androidx.core.content.edit
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.targetApp
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.viewmodel.PrefsViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrefsExportTest {

    @Rule
    @JvmField
    val prefsRule = CleanPrefsRule()

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Test
    fun preferencesRoundTrip() = runBlocking {
        targetPrefs.edit {
            putBoolean("rt_bool", true)
            putString("rt_str", "hello")
            putInt("rt_int", 7)
            putFloat("rt_float", 1.5f)
        }

        val vm = PrefsViewModel(targetApp)
        val file = File(targetContext.cacheDir, "prefs-export.json")
        val uri = Uri.fromFile(file)

        vm.export(uri, true)
        pollingAssert { assertTrue("Export file not written", file.exists() && file.length() > 0) }

        targetPrefs.edit {
            remove("rt_bool")
            remove("rt_str")
            remove("rt_int")
            remove("rt_float")
        }

        vm.import(uri, false)
        pollingAssert {
            assertTrue(targetPrefs.getBoolean("rt_bool", false))
            assertEquals("hello", targetPrefs.getString("rt_str", null))
            assertEquals(7, targetPrefs.getInt("rt_int", 0))
            assertEquals(1.5f, targetPrefs.getFloat("rt_float", 0f))
        }
    }
}
