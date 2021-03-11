/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.view.View
import android.widget.AdapterView
import android.widget.Spinner
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener


@BindingAdapter("isVisible")
fun visibilityAdapter(view: View, isVisible: Boolean) {
    view.isVisible = isVisible
}


@BindingAdapter("isInVisible")
fun inVisibilityAdapter(view: View, isInVisible: Boolean) {
    view.isInvisible = isInVisible
}

/********************************************************************
 * Spinner Bindings
 * These allows us to use label-value pairs as data source for a
 * Spinner and to bind the selected value to an int variable.
 *
 * To use these, three attributes should be specified:
 * android:entries  - String array of labels
 * app:entryValues  - Int array of values
 * app:value        - Field to bind with selected value of spinner
 *
 ********************************************************************/

@BindingAdapter("entryValues", "value")
fun spinnerEntryValuesAdapter(spinner: Spinner, values: IntArray, value: Int) {
    spinner.tag = values
    spinner.setSelection(values.indexOf(value))
}

@InverseBindingAdapter(attribute = "value")
fun spinnerValueInverseAdapter(spinner: Spinner): Int {
    val values = spinner.tag
    if (values != null && values is IntArray) {
        return values[spinner.selectedItemPosition]
    }
    return 0
}

@BindingAdapter("valueAttrChanged")
fun spinnerValueListener(spinner: Spinner, listener: InverseBindingListener) {
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            listener.onChange()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            listener.onChange()
        }
    }
}
