/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * Released under the terms of GPLv3 (or later).
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.view.View
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter


@BindingAdapter("app:isVisible")
fun visibilityAdapter(view: View, isVisible: Boolean) {
    view.isVisible = isVisible
}