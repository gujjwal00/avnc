/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.content.RestrictionsManager
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager

/**
 * Declarative description of an EMM manageable preference.
 *
 * A managed (EMM) value is written into the default [SharedPreferences] under [prefKey],
 * exactly like a normal user preference. Because [AppPreferences] reads the default
 * preferences, the managed value is picked up automatically without any other code change.
 */
sealed interface RestrictionDefinition {
    val emmKey: String
    val prefKey: String
}

data class BoolR(override val emmKey: String, override val prefKey: String) : RestrictionDefinition
data class ChoiceR(override val emmKey: String, override val prefKey: String, val values: List<String>) : RestrictionDefinition
data class IntR(override val emmKey: String, override val prefKey: String, val min: Int, val max: Int) : RestrictionDefinition

/**
 * Single source of truth for all EMM manageable preferences.
 *
 * The [app_restrictions.xml] file is a static mirror of [ALL] for EMM consoles.
 * Keep them in sync, but never branch on individual keys anywhere else — all
 * logic is data-driven over this list.
 */
object Restrictions {
    val ALL: List<RestrictionDefinition> = listOf(
        // Appearance
        ChoiceR("emm_theme", "theme", listOf("system", "light", "dark")),
        BoolR("emm_bell", "bell_enabled"),
        BoolR("emm_sort_server_list", "sort_server_list"),
        BoolR("emm_prefer_advanced_editor", "prefer_advanced_editor"),

        // Viewer
        ChoiceR("emm_screen_orientation", "viewer_orientation", listOf("auto", "portrait", "landscape")),
        BoolR("emm_keep_screen_on", "keep_screen_on"),
        BoolR("emm_fullscreen", "fullscreen_display"),
        BoolR("emm_draw_behind_cutout", "viewer_draw_behind_cutout"),
        BoolR("emm_pip", "pip_enabled"),
        BoolR("emm_pause_fb_background", "pause_fb_updates_in_background"),
        IntR("emm_zoom_min", "zoom_min", 10, 100),
        IntR("emm_zoom_max", "zoom_max", 100, 1000),
        BoolR("emm_per_orientation_zoom", "per_orientation_zoom"),
        ChoiceR("emm_toolbar_alignment", "toolbar_alignment", listOf("start", "end")),
        BoolR("emm_toolbar_open_swipe", "toolbar_open_with_swipe"),
        BoolR("emm_toolbar_open_button", "toolbar_open_with_button"),
        BoolR("emm_toolbar_show_gesture_toggle", "toolbar_show_gesture_style_toggle"),

        // Input
        ChoiceR("emm_gesture_style", "gesture_style", listOf("touchscreen", "touchpad")),
        ChoiceR("emm_gesture_double_tap", "gesture_double_tap", listOf("none", "double-click", "middle-click", "right-click")),
        ChoiceR("emm_gesture_long_press", "gesture_long_press", listOf("none", "double-click", "middle-click", "right-click", "left-press")),
        ChoiceR("emm_gesture_tap2", "gesture_tap2", listOf("none", "right-click", "open-keyboard")),
        ChoiceR("emm_gesture_tap3", "gesture_tap3", listOf("none", "right-click", "middle-click")),
        ChoiceR("emm_gesture_swipe1", "gesture_swipe1", listOf("none", "pan", "remote-scroll", "remote-drag")),
        ChoiceR("emm_gesture_swipe2", "gesture_swipe2", listOf("none", "pan", "remote-scroll")),
        ChoiceR("emm_gesture_swipe3", "gesture_swipe3", listOf("none", "pan", "remote-scroll")),
        ChoiceR("emm_gesture_double_tap_swipe", "gesture_double_tap_swipe", listOf("none", "remote-drag", "remote-drag-middle", "pan", "remote-scroll")),
        ChoiceR("emm_gesture_long_press_swipe", "gesture_long_press_swipe", listOf("none", "remote-drag", "remote-drag-middle", "pan", "remote-scroll")),
        IntR("emm_gesture_swipe_sensitivity", "gesture_swipe_sensitivity", 5, 15),
        BoolR("emm_invert_scrolling", "invert_vertical_scrolling"),
        BoolR("emm_mouse_passthrough", "mouse_passthrough"),
        BoolR("emm_capture_pointer", "capture_pointer"),
        BoolR("emm_hide_local_cursor", "hide_local_cursor"),
        BoolR("emm_hide_remote_cursor", "hide_remote_cursor"),
        ChoiceR("emm_mouse_back", "mouse_back", listOf("default", "middle-click", "right-click", "remote-back-press")),
        BoolR("emm_vk_open_with_keyboard", "vk_open_with_keyboard"),
        BoolR("emm_vk_use_super_single_tap", "vk_use_super_with_single_tap"),
        ChoiceR("emm_vk_row_count", "vk_row_count", listOf("1", "2", "3")),
        BoolR("emm_km_right_alt_super", "km_right_alt_to_super"),
        BoolR("emm_km_language_switch_super", "km_language_switch_to_super"),
        BoolR("emm_km_back_escape", "km_back_to_escape"),

        // Server
        BoolR("emm_clipboard_sync", "clipboard_sync"),
        BoolR("emm_lock_saved_server", "lock_saved_server"),
        BoolR("emm_auto_reconnect", "auto_reconnect"),
        BoolR("emm_discovery_autorun", "discovery_autorun"),
        BoolR("emm_rediscovery_indicator", "rediscovery_indicator"),
    )

    val byPrefKey: Map<String, RestrictionDefinition> = ALL.associateBy { it.prefKey }
}

/**
 * Applies EMM (managed) configurations to the app preferences.
 *
 * - EMM values take priority over user values.
 * - The original user value of a preference is backed up (once) before being
 *   overwritten, so it can be restored if the EMM restriction is removed.
 * - The set of currently managed preferences is exposed via [managedKeys].
 */
class ManagedConfigManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    private val backupPrefs = appContext.getSharedPreferences("managed_config_backup", Context.MODE_PRIVATE)
    private val statePrefs = appContext.getSharedPreferences("managed_config_state", Context.MODE_PRIVATE)

    private val _managedKeys = MutableLiveData<Set<String>>(statePrefs.getStringSet("managed_keys", emptySet())!!.toSet())
    val managedKeys: LiveData<Set<String>> = _managedKeys

    init {
        applyRestrictions()
    }

    fun isManaged(prefKey: String): Boolean = _managedKeys.value?.contains(prefKey) == true

    /**
     * Reads current EMM restrictions and applies them to the preferences.
     * Safe to call multiple times (e.g. on every broadcast).
     */
    fun applyRestrictions() {
        val bundle = (appContext.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager)
                ?.applicationRestrictions ?: Bundle.EMPTY

        val newManaged = mutableSetOf<String>()

        for (def in Restrictions.ALL) {
            when (def) {
                is BoolR -> if (bundle.containsKey(def.emmKey)) {
                    applyIfNeeded(def) { putBoolean(def.prefKey, bundle.getBoolean(def.emmKey)) }
                    newManaged += def.prefKey
                }

                is ChoiceR -> bundle.getString(def.emmKey)?.let { value ->
                    if (value in def.values) {
                        applyIfNeeded(def) { putString(def.prefKey, value) }
                        newManaged += def.prefKey
                    }
                }

                is IntR -> if (bundle.containsKey(def.emmKey)) {
                    val value = bundle.getInt(def.emmKey).coerceIn(def.min, def.max)
                    applyIfNeeded(def) { putInt(def.prefKey, value) }
                    newManaged += def.prefKey
                }
            }
        }

        val previouslyManaged = _managedKeys.value ?: emptySet()
        for (prefKey in previouslyManaged - newManaged)
            restore(prefKey)

        // Always persist & notify, so observers (e.g. PrefsActivity) refresh even when
        // only a managed value changed (same set of keys).
        statePrefs.edit { putStringSet("managed_keys", newManaged) }
        _managedKeys.value = newManaged
    }

    /**
     * Backs up the current user value before the first override of [def.prefKey],
     * then applies the EMM value.
     */
    private fun applyIfNeeded(def: RestrictionDefinition, write: SharedPreferences.Editor.() -> Unit) {
        val managed = managedKeys.value ?: emptySet()
        if (def.prefKey !in managed && !backupPrefs.contains(def.prefKey))
            backupCurrentValue(def)
        prefs.edit(write)
    }

    private fun backupCurrentValue(def: RestrictionDefinition) {
        when (def) {
            is BoolR -> backupPrefs.edit { putBoolean(def.prefKey, prefs.getBoolean(def.prefKey, false)) }
            is IntR -> backupPrefs.edit { putInt(def.prefKey, prefs.getInt(def.prefKey, 0)) }
            is ChoiceR -> prefs.getString(def.prefKey, null)?.let { backupPrefs.edit { putString(def.prefKey, it) } }
        }
    }

    private fun restore(prefKey: String) {
        val def = Restrictions.byPrefKey[prefKey]
        if (def == null) {
            // Key is no longer declared as manageable: drop the EMM value so the
            // app default applies, and discard its backup.
            prefs.edit { remove(prefKey) }
            backupPrefs.edit { remove(prefKey) }
            return
        }
        prefs.edit {
            when (def) {
                is BoolR -> if (backupPrefs.contains(prefKey)) putBoolean(prefKey, backupPrefs.getBoolean(prefKey, false)) else remove(prefKey)
                is IntR -> if (backupPrefs.contains(prefKey)) putInt(prefKey, backupPrefs.getInt(prefKey, 0)) else remove(prefKey)
                is ChoiceR -> if (backupPrefs.contains(prefKey)) putString(prefKey, backupPrefs.getString(prefKey, null)) else remove(prefKey)
            }
        }
        backupPrefs.edit { remove(prefKey) }
    }
}
