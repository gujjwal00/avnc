/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.IntentReceiverActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/************************************************************************************
 * Shortcuts
 ************************************************************************************/

fun updateShortcuts(activity: ComponentActivity, profiles: List<ServerProfile>) {
    activity.lifecycleScope.launch(Dispatchers.IO) {
        runCatching {
            val sortedProfiles = profiles.sortedByDescending { it.useCount }
            updateShortcutState(activity, sortedProfiles)
            updateDynamicShortcuts(activity, sortedProfiles)
        }.onFailure {
            Log.e("Shortcuts", "Unable to update shortcuts", it)
        }
    }
}

private fun createShortcutId(profile: ServerProfile) = "shortcut:pid:${profile.ID}"

/**
 * Enable/Disable shortcuts based on availability in [profiles]
 */
private fun updateShortcutState(activity: ComponentActivity, profiles: List<ServerProfile>) {
    val pinnedShortcuts = ShortcutManagerCompat.getShortcuts(activity, ShortcutManagerCompat.FLAG_MATCH_PINNED)
    val disabledMessage = activity.getString(R.string.msg_shortcut_server_deleted)

    val possibleIds = profiles.map { createShortcutId(it) }
    val pinnedIds = pinnedShortcuts.map { it.id }
    val enabledIds = pinnedIds.intersect(possibleIds).toList()
    val enabledShortcuts = pinnedShortcuts.filter { it.id in enabledIds }
    val disabledIds = pinnedIds.subtract(enabledIds).toList()

    ShortcutManagerCompat.enableShortcuts(activity, enabledShortcuts)
    ShortcutManagerCompat.disableShortcuts(activity, disabledIds, disabledMessage)
}

/**
 * Updates dynamic shortcut list
 */
private fun updateDynamicShortcuts(activity: ComponentActivity, profiles: List<ServerProfile>) {
    val maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(activity)
    val shortcuts = profiles.take(maxShortcuts).mapIndexed { i, p ->
        ShortcutInfoCompat.Builder(activity, createShortcutId(p))
                .setIcon(IconCompat.createWithResource(activity, R.drawable.ic_computer_shortcut))
                .setShortLabel(p.name.ifBlank { p.host })
                .setLongLabel(p.name.ifBlank { p.host })
                .setRank(i)
                .setIntent(IntentReceiverActivity.createShortcutIntent(activity, p.ID))
                .build()
    }
    ShortcutManagerCompat.setDynamicShortcuts(activity, shortcuts)
}
