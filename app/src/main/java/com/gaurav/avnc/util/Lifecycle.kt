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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer


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

/**
 * Returns a [LiveData] whose value is updated if any of the [dependencies] changes.
 *
 * - [generator] is used to calculate the new value
 * - If [generator] returns null, value is NOT updated
 * - No value is set unless one of the [dependencies] is set/changed
 */
fun <T : Any> monitor(vararg dependencies: LiveData<*>, generator: () -> T?): LiveData<T> {
    val mediator = MediatorLiveData<T>()
    val observer = Observer<Any> {
        generator()?.let {
            mediator.value = it
        }
    }
    dependencies.forEach { mediator.addSource(it, observer) }
    return mediator
}

/**
 * Checks if value of this LiveData is true.
 * Returns false if value is null.
 */
val LiveData<Boolean>.isTrue get() = (value == true)
