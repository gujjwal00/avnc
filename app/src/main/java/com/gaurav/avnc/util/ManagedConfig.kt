/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Provides access to managed configuration (a.k.a. app restrictions) pushed by
 * an EMM/MDM solution. See `res/xml/app_restrictions.xml` for supported keys.
 *
 * Keys of restrictions which override a preference are same as the preference key.
 */
class ManagedConfig private constructor(context: Context) {

    private val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager

    /**
     * Current restrictions. Empty if device is not managed.
     * Groups are flattened, see [flattenGroups].
     */
    @Volatile
    var restrictions = readRestrictions(); private set

    private val liveRestrictionsData = MutableLiveData(restrictions)

    /**
     * Emits current restrictions, and then a new value whenever restrictions are changed.
     */
    val liveRestrictions: LiveData<Bundle> = liveRestrictionsData

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            restrictions = readRestrictions()
            liveRestrictionsData.value = restrictions
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun readRestrictions() = restrictionsManager?.applicationRestrictions?.flattenGroups() ?: Bundle()

    /**
     * Returns true if a value is enforced for given key.
     * Empty strings & 0 are used for "Defined by user", so they are not considered values.
     */
    fun isManaged(key: String): Boolean {
        @Suppress("DEPRECATION")
        return when (val value = restrictions.get(key)) {
            null -> false
            is String -> value.isNotEmpty()
            is Int -> value != 0
            else -> true
        }
    }

    /**
     * Returns enforced values of all 'string' & 'choice' restrictions.
     */
    fun enforcedStrings(): Map<String, String> {
        @Suppress("DEPRECATION")
        return restrictions.keySet()
                .mapNotNull { key -> (restrictions.get(key) as? String)?.takeIf { it.isNotEmpty() }?.let { key to it } }
                .toMap()
    }

    // These return the default if value is not enforced, or has an unexpected type
    fun getString(key: String, default: String) = restrictions.getString(key)?.takeIf { it.isNotEmpty() } ?: default
    fun getInt(key: String, default: Int) = restrictions.getInt(key, 0).takeIf { it != 0 } ?: default

    /**
     * Booleans which can be "Defined by user" are 'choice' restrictions, with string values.
     */
    fun getBoolean(key: String, default: Boolean): Boolean {
        @Suppress("DEPRECATION")
        return when (val value = restrictions.get(key)) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    companion object {
        @Volatile
        private var instance: ManagedConfig? = null

        fun obtain(context: Context): ManagedConfig {
            return instance ?: synchronized(this) {
                instance ?: ManagedConfig(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Moves values of nested bundles to the top level.
 * In managed configuration schema, 'bundle' restrictions are only used to group
 * restrictions into sections for EMM consoles, so they are irrelevant for reading values.
 */
internal fun Bundle.flattenGroups(): Bundle {
    val result = Bundle(this)
    for (key in keySet()) {
        @Suppress("DEPRECATION") // Typed getter would log a warning for non-bundle values
        val group = get(key) as? Bundle ?: continue
        result.remove(key)
        result.putAll(group.flattenGroups())
    }
    return result
}
