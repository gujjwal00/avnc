/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner


/**
 * A lifecycle-aware variant of global layout listener
 */
fun addOnGlobalLayoutListener(owner: LifecycleOwner, view: View, listener: ViewTreeObserver.OnGlobalLayoutListener) {
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) {
            view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    })
}