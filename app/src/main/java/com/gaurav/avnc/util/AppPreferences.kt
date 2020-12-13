/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Utility class for accessing app preferences
 *
 * TODO: Planned Preferences
 *       - Clipboard Sync (On/Off)
 */
class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    inner class Zoom {
        val max; get() = prefs.getInt("pref_zoom_max", 500) / 100F
        val min; get() = prefs.getInt("pref_zoom_min", 50) / 100F
    }

    val zoom = Zoom()

    inner class Gesture {
        val singleTap; get() = prefs.getString("pref_gesture_single_tap", "left-click")!!
        val doubleTap; get() = prefs.getString("pref_gesture_double_tap", "double-click")!!
        val longPress; get() = prefs.getString("pref_gesture_long_press", "right-click")!!
        val swipe1; get() = prefs.getString("pref_gesture_swipe1", "pan")!!
        val swipe2; get() = prefs.getString("pref_gesture_swipe2", "pan")!!
    }

    val gesture = Gesture()

    inner class Credential {
        val autocomplete; get() = prefs.getBoolean("pref_cred_autocomplete", true)
    }

    val cred = Credential()
}