/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatSpinner
import com.google.android.material.elevation.ElevationOverlayProvider

/**
 * This class extends spinner to handle some quirks and add some utility features.
 */
class SpinnerEx(context: Context, attrs: AttributeSet? = null) : AppCompatSpinner(context, attrs) {

    init {
        setupElevationOverlay()
    }


    /**
     * Popup window of the Spinner does not support elevation overlay
     * which makes it hard to differentiate between popup & rest of the controls in dark theme.
     *
     * So we manually apply the overlay to popup background.
     */
    private fun setupElevationOverlay() {
        // Elevation is hardcoded to 16dp because we don't have access to popup
        val popupElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                                                       16F,
                                                       resources.displayMetrics)

        val overlay = ElevationOverlayProvider(context)
                .compositeOverlayWithThemeSurfaceColorIfNeeded(popupElevation)

        val background = popupBackground
        if (background is GradientDrawable)
            background.setColor(overlay)
        else
            background.setTint(overlay)
    }

    /**
     * This method allows setting a Key-Value map as data source for this spinner.
     * Items in the Spinner are populated using [keys] and whenever an item is selected,
     * [selectionListener] will be called with corresponding value from [values].
     *
     * Using this method will replace the old Adapter & OnItemSelectionListener.
     *
     * [keys] & [values] must have the same size.
     */
    fun <T> setEntries(keys: Array<String>, values: Array<T>, initialValue: T, selectionListener: (T) -> Unit) {
        check(keys.size == values.size)

        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, keys)

        setSelection(values.indexOf(initialValue))

        onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectionListener(values[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * Helper for Integer maps
     */
    fun setEntries(entries: Map<String, Int>, initialValue: Int, selectionListener: (Int) -> Unit) {
        setEntries(entries.keys.toTypedArray(), entries.values.toTypedArray(), initialValue, selectionListener)
    }
}