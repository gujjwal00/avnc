/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.databinding.BindingAdapter


@BindingAdapter("isVisible")
fun visibilityAdapter(view: View, isVisible: Boolean) {
    view.isVisible = isVisible
}

@BindingAdapter("isInVisible")
fun inVisibilityAdapter(view: View, isInVisible: Boolean) {
    view.isInvisible = isInVisible
}
