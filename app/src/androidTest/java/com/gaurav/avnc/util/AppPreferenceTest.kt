/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPreferenceTest {

    @Rule
    @JvmField
    val prefRule = CleanPrefsRule()


    // If user had turned off Direct Touch, new Gesture Style pref
    // should be set to 'touchpad'
    @Test
    fun gestureStyleMigrationTest() {
        targetPrefs.edit {
            remove("gesture_style")
            putBoolean("gesture_direct_touch", false)
        }

        val appPrefs = AppPreferences(targetContext)
        assertEquals(appPrefs.input.gesture.style, "touchpad")
    }

    @Test
    fun metaKeysRightToLeftMigration() {
        targetPrefs.edit { putString("vk_keys_layout", "RightAlt,RightShift,F1,Up") }
        val p1 = AppPreferences(targetContext)
        assertEquals("LeftAlt,LeftShift,F1,Up", p1.input.vkLayout)

        // If Right-variants are ever added in future, they should not be affected
        targetPrefs.edit { putString("vk_keys_layout", "LeftAlt,RightAlt,RightShift,F1,Up") }
        val p2 = AppPreferences(targetContext)
        assertEquals("LeftAlt,RightAlt,RightShift,F1,Up", p2.input.vkLayout)

    }
}