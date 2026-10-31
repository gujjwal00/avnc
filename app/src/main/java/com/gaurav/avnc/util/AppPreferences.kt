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
import kotlin.reflect.KProperty

/**
 * Utility class for accessing app preferences
 */
class AppPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Values from managed configuration take precedence over user preferences.
     */
    private val managed = ManagedConfig.obtain(context)

    private fun managedBoolean(key: String, default: Boolean) = managed.getBoolean(key, prefs.getBoolean(key, default))
    private fun managedString(key: String, default: String) = managed.getString(key, prefs.getString(key, default)!!)

    inner class UI {
        val theme = StringLivePref("theme", "system")
        var bell by BooleanPref("bell_enabled", true)
        var preferAdvancedEditor by BooleanPref("prefer_advanced_editor", false)
        val sortServerList = BooleanLivePref("sort_server_list", false)
    }

    inner class Viewer {
        val orientation; get() = managedString("viewer_orientation", "auto")
        val fullscreen; get() = managedBoolean("fullscreen_display", true)
        val pipEnabled; get() = prefs.getBoolean("pip_enabled", false)
        val drawBehindCutout; get() = fullscreen && prefs.getBoolean("viewer_draw_behind_cutout", false)
        val keepScreenOn; get() = managedBoolean("keep_screen_on", true)
        val toolbarAlignment; get() = prefs.getString("toolbar_alignment", "start")
        val toolbarOpenWithSwipe; get() = prefs.getBoolean("toolbar_open_with_swipe", true)
        val toolbarOpenWithButton; get() = prefs.getBoolean("toolbar_open_with_button", false)
        val zoomMax; get() = prefs.getInt("zoom_max", 500) / 100F
        val zoomMin; get() = prefs.getInt("zoom_min", 50) / 100F
        val perOrientationZoom; get() = prefs.getBoolean("per_orientation_zoom", true)
        val toolbarShowGestureStyleToggle; get() = prefs.getBoolean("toolbar_show_gesture_style_toggle", true)
        val pauseUpdatesInBackground; get() = prefs.getBoolean("pause_fb_updates_in_background", false)
    }

    inner class Gesture {
        val style; get() = managedString("gesture_style", "touchscreen")
        val tap1 = "left-click" //Preference UI was removed
        val tap2; get() = managedString("gesture_tap2", "open-keyboard")
        val tap3; get() = managedString("gesture_tap3", "none")
        val doubleTap; get() = managedString("gesture_double_tap", "double-click")
        val longPress; get() = managedString("gesture_long_press", "right-click")
        val swipe1; get() = managedString("gesture_swipe1", "pan")
        val swipe2; get() = managedString("gesture_swipe2", "pan")
        val swipe3; get() = managedString("gesture_swipe3", "pan")
        val doubleTapSwipe; get() = managedString("gesture_double_tap_swipe", "remote-drag")
        val longPressSwipe; get() = managedString("gesture_long_press_swipe", "none")
        val longPressSwipeEnabled; get() = (longPressSwipe != "none" && longPress != "left-press")
        val longPressDetectionEnabled; get() = (longPress != "none" || longPressSwipeEnabled)
        val swipeSensitivity; get() = managed.getInt("gesture_swipe_sensitivity", prefs.getInt("gesture_swipe_sensitivity", 10)).coerceIn(5, 15) / 10f
        val invertVerticalScrolling; get() = managedBoolean("invert_vertical_scrolling", false)
        val quickTap1Enabled; get() = ((doubleTap == "none" || doubleTap == "double-click") && doubleTapSwipe == "none")
    }

    inner class Input {
        val gesture = Gesture()

        val vkOpenWithKeyboard; get() = managedBoolean("vk_open_with_keyboard", false)
        val vkShowAll; get() = prefs.getBoolean("vk_show_all", false)
        var vkLayout by StringPref("vk_keys_layout", null)
        val vkRowCount; get() = managedString("vk_row_count", "2").toIntOrNull() ?: 2
        val vkUseSuperWithSingleTap; get() = managedBoolean("vk_use_super_with_single_tap", false)

        val mousePassthrough; get() = managedBoolean("mouse_passthrough", true)
        val capturePointer; get() = mousePassthrough && managedBoolean("capture_pointer", false)
        val hideLocalCursor; get() = managedBoolean("hide_local_cursor", false)
        val hideRemoteCursor; get() = managedBoolean("hide_remote_cursor", false)
        val mouseBack; get() = managedString("mouse_back", "right-click")
        val interceptMouseBack; get() = mouseBack != "default"

        val kmLanguageSwitchToSuper; get() = managedBoolean("km_language_switch_to_super", false)
        val kmRightAltToSuper; get() = managedBoolean("km_right_alt_to_super", false)
        val kmBackToEscape; get() = managedBoolean("km_back_to_escape", false)
    }

    inner class Server {
        val clipboardSync; get() = prefs.getBoolean("clipboard_sync", true)
        val lockSavedServer; get() = managedBoolean("lock_saved_server", false)
        val autoReconnect; get() = managedBoolean("auto_reconnect", false)
        val discoveryAutorun; get() = prefs.getBoolean("discovery_autorun", true)
        val rediscoveryIndicator = BooleanLivePref("rediscovery_indicator", true)
    }

    /**
     * These are used for one-time features/tips, or UI state .
     * These are not exposed to user.
     */
    inner class RunInfo {
        var hasShownViewerHelp by BooleanPref("run_info_has_shown_viewer_help", false)
        var hasShownV3WelcomeMsg by BooleanPref("run_info_has_shown_v3_welcome_msg", false)
        var showVirtualKeys by BooleanPref("run_info_show_virtual_keys", true)
        var virtualKeysTextBoxVisible by BooleanPref("run_info_virtual_keys_textbox_visible", false)
        var toolbarOpenerBtnVerticalBias by FloatPref("run_info_toolbar_opener_vertical_bias", .5f)
    }

    val ui = UI()
    val viewer = Viewer()
    val input = Input()
    val server = Server()
    val runInfo = RunInfo()

    /****************************** Helpers *******************************/
    open inner class Pref<T>(private val getter: SharedPreferences.() -> T, private val setter: SharedPreferences.Editor.(T) -> Unit) {
        operator fun getValue(a: Any, kp: KProperty<*>) = getter(prefs)
        operator fun setValue(a: Any, kp: KProperty<*>, value: T) = prefs.edit { setter(this, value) }
    }

    // Managed values take precedence. Setters still update user preferences.
    inner class BooleanPref(val key: String, default: Boolean) : Pref<Boolean>({ managed.getBoolean(key, getBoolean(key, default)) }, { putBoolean(key, it) })
    inner class StringPref(val key: String, default: String?) : Pref<String?>({ getString(key, default) }, { putString(key, it) })
    inner class FloatPref(val key: String, default: Float) : Pref<Float>({ getFloat(key, default) }, { putFloat(key, it) })

    /**
     * For some preference changes we want to provide live feedback to user.
     * This class is used for such scenarios. Based on [LiveData], it notifies
     * the observers whenever the value of given preference (or its managed value) is changed.
     */
    open inner class LivePref<T>(val key: String, private val getter: SharedPreferences.() -> T) : LiveData<T>() {
        private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (key == changedKey)
                value = getter(prefs)
        }

        override fun onActive() {
            if (!isInitialized) {
                value = getter(prefs)
                prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
                managed.liveRestrictions.observeForever { _ ->
                    val newValue = getter(prefs)
                    if (newValue != value) value = newValue
                }
            }
        }
    }

    inner class BooleanLivePref(key: String, default: Boolean) : LivePref<Boolean>(key, { managed.getBoolean(key, getBoolean(key, default)) })
    inner class StringLivePref(key: String, default: String) : LivePref<String>(key, { managed.getString(key, getString(key, default)!!) })


    /****************************** Migrations *******************************/
    init {
        if (!prefs.getBoolean("gesture_direct_touch", true)) prefs.edit {
            remove("gesture_direct_touch")
            putString("gesture_style", "touchpad")
        }

        if (!prefs.getBoolean("natural_scrolling", true)) prefs.edit {
            remove("natural_scrolling")
            putBoolean("invert_vertical_scrolling", true)
        }

        prefs.getString("gesture_drag", null)?.let {
            prefs.edit {
                remove("gesture_drag")
                putString("gesture_long_press_swipe", it)
            }
        }

        if (prefs.getBoolean("run_info_has_connected_successfully", false)) prefs.edit {
            remove("run_info_has_connected_successfully")
            putBoolean("run_info_has_shown_viewer_help", true)
        }

        if (!prefs.getBoolean("run_info_right_meta_keys_migrated", false)) prefs.edit {
            prefs.getString("vk_keys_layout", null)?.let { old ->
                val new = old
                        .replace("RightShift", "LeftShift")
                        .replace("RightCtrl", "LeftCtrl")
                        .replace("RightAlt", "LeftAlt")
                        .replace("RightSuper", "LeftSuper")
                putString("vk_keys_layout", new)
            }
            putBoolean("run_info_right_meta_keys_migrated", true)
        }
    }
}