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
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager

/**
 * Utility class for accessing app preferences
 */
class AppPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    inner class UI {
        val theme = LivePref("theme", "system")
        val preferAdvancedEditor; get() = prefs.getBoolean("prefer_advanced_editor", false)
        val sortServerList = LivePref("sort_server_list", false)
    }

    inner class Viewer {
        val orientation; get() = prefs.getString("viewer_orientation", "auto")
        val fullscreen; get() = prefs.getBoolean("fullscreen_display", true)
        val pipEnabled; get() = prefs.getBoolean("pip_enabled", false)
        val toolbarAlignment; get() = prefs.getString("toolbar_alignment", "start")
        val zoomMax; get() = prefs.getInt("zoom_max", 500) / 100F
        val zoomMin; get() = prefs.getInt("zoom_min", 50) / 100F
        val perOrientationZoom; get() = prefs.getBoolean("per_orientation_zoom", true)
    }

    inner class Gesture {
        val style; get() = prefs.getString("gesture_style", "touchscreen")!!
        val tap1 = "left-click" //Preference UI was removed
        val tap2; get() = prefs.getString("gesture_tap2", "open-keyboard")!!
        val doubleTap; get() = prefs.getString("gesture_double_tap", "double-click")!!
        val longPress; get() = prefs.getString("gesture_long_press", "right-click")!!
        val swipe1; get() = prefs.getString("gesture_swipe1", "pan")!!
        val swipe2; get() = prefs.getString("gesture_swipe2", "pan")!!
        val drag; get() = prefs.getString("gesture_drag", "none")!!
        val dragEnabled; get() = (drag != "none")
        val swipeSensitivity; get() = prefs.getInt("gesture_swipe_sensitivity", 10) / 10f
        val invertVerticalScrolling; get() = prefs.getBoolean("invert_vertical_scrolling", false)
    }

    inner class Input {
        val gesture = Gesture()

        val vkOpenWithKeyboard; get() = prefs.getBoolean("vk_open_with_keyboard", false)
        val vkShowAll; get() = prefs.getBoolean("vk_show_all", false)

        val mousePassthrough; get() = prefs.getBoolean("mouse_passthrough", true)
        val hideLocalCursor; get() = prefs.getBoolean("hide_local_cursor", false)
        val hideRemoteCursor; get() = prefs.getBoolean("hide_remote_cursor", false)
        val mouseBack; get() = prefs.getString("mouse_back", "right-click")!!
        val interceptMouseBack; get() = mouseBack != "default"

        val kmLanguageSwitchToSuper; get() = prefs.getBoolean("km_language_switch_to_super", false)
        val kmRightAltToSuper; get() = prefs.getBoolean("km_right_alt_to_super", false)
        val kmBackToEscape; get() = prefs.getBoolean("km_back_to_escape", false)
    }

    inner class Server {
        val clipboardSync; get() = prefs.getBoolean("clipboard_sync", true)
        val lockSavedServer; get() = prefs.getBoolean("lock_saved_server", false)
        val discoveryAutorun; get() = prefs.getBoolean("discovery_autorun", true)
        val rediscoveryIndicator = LivePref("rediscovery_indicator", true)
    }

    inner class Experimental {
        val drawBehindCutout; get() = prefs.getBoolean("viewer_draw_behind_cutout", false)
    }

    /**
     * These are used for one-time features/tips.
     * These are not exposed to user.
     */
    inner class RunInfo {
        var hasConnectedSuccessfully: Boolean
            get() = prefs.getBoolean("run_info_has_connected_successfully", false)
            set(value) = prefs.edit { putBoolean("run_info_has_connected_successfully", value) }

        var hasShownV2WelcomeMsg
            get() = prefs.getBoolean("run_info_has_shown_v2_welcome_msg", false)
            set(value) = prefs.edit { putBoolean("run_info_has_shown_v2_welcome_msg", value) }
    }

    val ui = UI()
    val viewer = Viewer()
    val input = Input()
    val server = Server()
    val experimental = Experimental()
    val runInfo = RunInfo()


    /**
     * For some preference changes we want to provide live feedback to user.
     * This class is used for such scenarios. Based on [LiveData], it notifies
     * the observers whenever the value of given preference is changed.
     *
     * For now, each [LivePref] creates a separate change listener, but if
     * number of [LivePref]s grow, we can optimize by sharing a single listener.
     */
    inner class LivePref<T>(val key: String, private val defValue: T) : LiveData<T>() {
        private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (key == changedKey)
                updateValue()
        }

        private var initialized = false

        override fun onActive() {
            if (!initialized) {
                initialized = true
                updateValue()
                prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
            }
        }

        private fun updateValue() {
            @Suppress("UNCHECKED_CAST")
            when (defValue) {
                is Boolean -> value = prefs.getBoolean(key, defValue) as T
                is String -> value = prefs.getString(key, defValue) as T
                is Int -> value = prefs.getInt(key, defValue) as T
                is Long -> value = prefs.getLong(key, defValue) as T
                is Float -> value = prefs.getFloat(key, defValue) as T
            }
        }
    }


    /****************************** Migrations *******************************/
    init {
        if (!prefs.getBoolean("gesture_direct_touch", true)) {
            prefs.edit().putString("gesture_style", "touchpad")
                    .remove("gesture_direct_touch")
                    .apply()
        }

        if (!prefs.getBoolean("natural_scrolling", true)) prefs.edit {
            remove("natural_scrolling")
            putBoolean("invert_vertical_scrolling", true)
        }
    }
}