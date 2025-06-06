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
import kotlin.math.roundToInt // Added import

/**
 * Utility class for accessing app preferences
 */
class AppPreferences(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    inner class UI {
        val theme = StringLivePref("theme", "system")
        var preferAdvancedEditor by BooleanPref("prefer_advanced_editor", false)
        val sortServerList = BooleanLivePref("sort_server_list", false)
    }

    inner class Viewer {
        val orientation; get() = prefs.getString("viewer_orientation", "auto")
        val fullscreen; get() = prefs.getBoolean("fullscreen_display", true)
        val pipEnabled; get() = prefs.getBoolean("pip_enabled", false)
        val drawBehindCutout; get() = fullscreen && prefs.getBoolean("viewer_draw_behind_cutout", false)
        val keepScreenOn; get() = prefs.getBoolean("keep_screen_on", true)
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
        val style; get() = prefs.getString("gesture_style", "touchscreen")!!
        val tap1 = "left-click" //Preference UI was removed
        val tap2; get() = prefs.getString("gesture_tap2", "open-keyboard")!!
        val tap3; get() = prefs.getString("gesture_tap3", "none")!!
        val doubleTap; get() = prefs.getString("gesture_double_tap", "double-click")!!
        val longPress; get() = prefs.getString("gesture_long_press", "right-click")!!
        val swipe1; get() = prefs.getString("gesture_swipe1", "pan")!!
        val swipe2; get() = prefs.getString("gesture_swipe2", "pan")!!
        val swipe3; get() = prefs.getString("gesture_swipe3", "pan")!!
        val doubleTapSwipe; get() = prefs.getString("gesture_double_tap_swipe", "remote-drag")!!
        val longPressSwipe; get() = prefs.getString("gesture_long_press_swipe", "none")!!
        val longPressSwipeEnabled; get() = (longPressSwipe != "none" && longPress != "left-press")
        val longPressDetectionEnabled; get() = (longPress != "none" || longPressSwipeEnabled)
        val swipeSensitivity; get() = prefs.getInt("gesture_swipe_sensitivity", 10) / 10f
        val invertVerticalScrolling; get() = prefs.getBoolean("invert_vertical_scrolling", false)
    }

    inner class Input {
        val gesture = Gesture()

        val vkOpenWithKeyboard; get() = prefs.getBoolean("vk_open_with_keyboard", false)
        val vkShowAll; get() = prefs.getBoolean("vk_show_all", false)
        var vkLayout by StringPref("vk_keys_layout", null)

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
        val autoReconnect; get() = prefs.getBoolean("auto_reconnect", false)
        val discoveryAutorun; get() = prefs.getBoolean("discovery_autorun", true)
        val rediscoveryIndicator = BooleanLivePref("rediscovery_indicator", true)
    }

    /**
     * These are used for one-time features/tips, or UI state .
     * These are not exposed to user.
     */
    inner class RunInfo {
        var hasShownViewerHelp by BooleanPref("run_info_has_shown_viewer_help", false)
        var hasShownV2WelcomeMsg by BooleanPref("run_info_has_shown_v2_welcome_msg", false)
        var showVirtualKeys by BooleanPref("run_info_show_virtual_keys", false)
        var toolbarOpenerBtnVerticalBias by FloatPref("run_info_toolbar_opener_vertical_bias", .5f)
    }

    val ui = UI()
    val viewer = Viewer()
    val input = Input()
    val server = Server()
    val runInfo = RunInfo()
    val xr = XR() // Add instance of XR preferences

    /****************************** Helpers *******************************/
    open inner class Pref<T>(private val getter: SharedPreferences.() -> T, private val setter: SharedPreferences.Editor.(T) -> Unit) {
        operator fun getValue(a: Any, kp: KProperty<*>) = getter(prefs)
        operator fun setValue(a: Any, kp: KProperty<*>, value: T) = prefs.edit { setter(this, value) }
    }

    inner class BooleanPref(val key: String, default: Boolean) : Pref<Boolean>({ getBoolean(key, default) }, { putBoolean(key, it) })
    inner class StringPref(val key: String, default: String?) : Pref<String?>({ getString(key, default) }, { putString(key, it) })
    inner class FloatPref(val key: String, default: Float) : Pref<Float>({ getFloat(key, default) }, { putFloat(key, it) })
    inner class IntPref(val key: String, default: Int) : Pref<Int>({ getInt(key, default) }, { putInt(key, it) })

    /**
     * For some preference changes we want to provide live feedback to user.
     * This class is used for such scenarios. Based on [LiveData], it notifies
     * the observers whenever the value of given preference is changed.
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
            }
        }
    }

    inner class BooleanLivePref(key: String, default: Boolean) : LivePref<Boolean>(key, { getBoolean(key, default) })
    inner class StringLivePref(key: String, default: String) : LivePref<String>(key, { getString(key, default)!! })

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // Inner class for XR-specific preferences
    inner class XR {
        // Default "flat", values "flat", "cylindrical"
        var displayMode by StringPref("xr_display_mode", "flat")

        // Stored as Int (10-50), interpret as Float (1.0-5.0)
        // Default 20 (for 2.0f)
        val cylinderRadiusInt by IntPref("xr_cylinder_radius", 20)
        val cylinderRadius: Float
            get() = (cylinderRadiusInt / 10f).coerceIn(1.0f, 5.0f)

        // Default "rotation", values "rotation", "offset_surface"
        var panningMode by StringPref("xr_panning_mode", "rotation")

        // Panning input device enables
        var enableViturePanning by BooleanPref("xr_enable_viture_panning", true)
        var enableTouchPanning by BooleanPref("xr_enable_touch_panning", true) // Touch panning is often a primary input
        var enablePhoneImuDeltaPanning by BooleanPref("xr_enable_phone_imu_delta_panning", false) // Default to off to avoid conflict if rotation is preferred
        var enablePhoneRotationPanning by BooleanPref("xr_enable_phone_rotation_panning", false) // Default to off

        // Viture Panning Sensitivities
        var viturePanSensitivityX: Float
            get() {
                val intValue = prefs.getInt("xr_viture_sensitivity_x_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_viture_sensitivity_x_int", intValue) }
            }

        var viturePanSensitivityY: Float
            get() {
                val intValue = prefs.getInt("xr_viture_sensitivity_y_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_viture_sensitivity_y_int", intValue) }
            }

        // Phone IMU Delta Panning Sensitivities
        var phoneImuDeltaPanSensitivityX: Float
            get() {
                val intValue = prefs.getInt("xr_phone_imu_delta_sensitivity_x_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_phone_imu_delta_sensitivity_x_int", intValue) }
            }

        var phoneImuDeltaPanSensitivityY: Float
            get() {
                val intValue = prefs.getInt("xr_phone_imu_delta_sensitivity_y_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_phone_imu_delta_sensitivity_y_int", intValue) }
            }

        // Phone Rotation Panning Sensitivities
        var phoneRotationPanSensitivityX: Float
            get() {
                val intValue = prefs.getInt("xr_phone_rotation_sensitivity_x_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_phone_rotation_sensitivity_x_int", intValue) }
            }

        var phoneRotationPanSensitivityY: Float
            get() {
                val intValue = prefs.getInt("xr_phone_rotation_sensitivity_y_int", 60)
                return -5.0f + intValue * 0.1f
            }
            set(value) {
                val intValue = ((value + 5.0f) / 0.1f).roundToInt().coerceIn(0, 100)
                prefs.edit { putInt("xr_phone_rotation_sensitivity_y_int", intValue) }
            }
    }

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
    }
}