/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

/**
 * List preference with some extra features.
 */
class ListPreferenceEx(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    /**
     * Summary used when preference is disabled.
     */
    var disabledStateSummary: CharSequence? = null

    override fun getSummary(): CharSequence? {
        if (!isEnabled && disabledStateSummary != null)
            return disabledStateSummary
        return super.getSummary()
    }
}