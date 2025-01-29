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
import android.view.View
import android.widget.ImageButton
import androidx.core.content.withStyledAttributes
import androidx.fragment.app.FragmentActivity
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import com.gaurav.avnc.R
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.util.debugCheck

/**
 * List preference with some extra features.
 */
class ListPreferenceEx(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    /**
     * Summary used when preference is disabled.
     */
    var disabledStateSummary: CharSequence? = null

    /**
     * Message shown in a dialog, when help button of the preference is clicked.
     * This will only work if [R.layout.help_btn] is used as widget layout.
     */
    private var helpMessage: CharSequence? = null

    private val helpClickListener = View.OnClickListener {
        helpMessage?.let { helpMessage ->
            (context as? FragmentActivity)?.let { fragmentActivity ->
                MsgDialog.show(fragmentActivity.supportFragmentManager,
                               fragmentActivity.getString(R.string.desc_help_btn),
                               helpMessage)
            }
        }
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.ListPreferenceEx) {
            helpMessage = getText(R.styleable.ListPreferenceEx_helpMessage)
        }
    }

    override fun getSummary(): CharSequence? {
        if (!isEnabled && disabledStateSummary != null)
            return disabledStateSummary
        return super.getSummary()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val helpButton = (holder.findViewById(R.id.help_btn) as? ImageButton)
        debugCheck((helpButton == null && helpMessage == null) || (helpButton != null && helpMessage != null))
        helpButton?.setOnClickListener(helpClickListener)
    }
}