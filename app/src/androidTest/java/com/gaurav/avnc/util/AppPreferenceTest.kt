/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.targetContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferenceTest {

    // If user had turned off Direct Touch, new Gesture Style pref
    // should be set to 'touchpad'
    @Test
    fun gestureStyleMigrationTest() {
        PreferenceManager.getDefaultSharedPreferences(targetContext).edit()
                .remove("gesture_style")
                .putBoolean("gesture_direct_touch", false)
                .apply()

        val appPrefs = AppPreferences(targetContext)
        assertEquals(appPrefs.input.gesture.style, "touchpad")
    }
}