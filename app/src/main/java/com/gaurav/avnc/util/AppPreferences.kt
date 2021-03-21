/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/**
 * Utility class for accessing app preferences
 *
 * - All timeouts are in milliseconds.
 */
class AppPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    inner class Appearance {
        val nightMode
            get() = when (prefs.getString("theme", "system")) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
    }

    inner class Display {
        val fullscreen; get() = prefs.getBoolean("fullscreen_display", true)
        val pipEnabled; get() = prefs.getBoolean("pip_enabled", false)
        val zoomMax; get() = prefs.getInt("zoom_max", 500) / 100F
        val zoomMin; get() = prefs.getInt("zoom_min", 50) / 100F
    }

    inner class Gesture {
        val singleTap; get() = prefs.getString("gesture_single_tap", "left-click")!!
        val doubleTap; get() = prefs.getString("gesture_double_tap", "double-click")!!
        val longPress; get() = prefs.getString("gesture_long_press", "right-click")!!
        val swipe1; get() = prefs.getString("gesture_swipe1", "pan")!!
        val swipe2; get() = prefs.getString("gesture_swipe2", "pan")!!
        val drag; get() = prefs.getString("gesture_drag", "none")!!
        val dragEnabled; get() = (drag != "none")
    }

    inner class Input {
        val gesture = Gesture()

        val vkOpenWithKeyboard; get() = prefs.getBoolean("vk_open_with_keyboard", false)
        val vkShowAll; get() = prefs.getBoolean("vk_show_all", false)
    }

    inner class Server {
        val discoveryTimeout; get() = prefs.getInt("discovery_timeout", 10) * 1000L
        val discoveryAutoStart; get() = prefs.getBoolean("discovery_auto_start", true)
        val discoveryRestart; get() = prefs.getBoolean("discovery_restart", false)
        val discoveryRestartDelay; get() = prefs.getInt("discovery_restart_delay", 5) * 60 * 1000L

        val credAutocomplete; get() = prefs.getBoolean("cred_autocomplete", false)
        val clipboardSync; get() = prefs.getBoolean("clipboard_sync", true)
    }

    inner class Experimental {
        val indicator; get() = prefs.getBoolean("experimental_indicator", false)
    }

    val appearance = Appearance()
    val display = Display()
    val input = Input()
    val server = Server()
    val experimental = Experimental()
}