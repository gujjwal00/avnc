/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

/**
 * This class extends LiveData by implementing the concept of events.
 *
 * LiveData can invoke the observer multiple times for the same change. It can
 * happen if observer is detached and attached again (ex. during Activity restart).
 * But there are some 'events' which should be handled only once.
 *
 * LiveEvent tracks whether someone has handled the current value/change in LiveData.
 * Once handled, observers will not be notified for the same value.
 */
class LiveEvent<T> : LiveData<T>() {

    /**
     * Whether current value has been handled.
     */
    private var handled = false

    /**
     * Override to reset state.
     */
    override fun setValue(value: T) {
        handled = false
        super.setValue(value)
    }

    /**
     * Sets new value for this event.
     *
     * This must be called on main thread.
     */
    fun set(value: T) {
        setValue(value)
    }

    /**
     * Sets new value for this event.
     *
     * This can be called from other threads.
     */
    fun post(value: T) {
        postValue(value)
    }


    /**
     * Registers an observer for this event.
     *
     * Observer will only be called if event is not yet handled and
     * new value in not null.
     */
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        //Observer given to us is wrapped in another Observer which checks current state
        super.observe(owner, Observer {
            if (!handled && it != null) {
                observer.onChanged(it)
                handled = true
            }
        })
    }

    /**
     * Overload of `observe` which allows directly passing a lambda.
     */
    fun observe(owner: LifecycleOwner, observer: (data: T) -> Unit) {
        super.observe(owner, Observer {
            if (!handled && it != null) {
                observer(it)
                handled = true
            }
        })
    }
}