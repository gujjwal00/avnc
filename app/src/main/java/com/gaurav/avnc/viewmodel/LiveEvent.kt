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
 * This class implements the concept of single-fire observable events.
 * It is based on [LiveData] which does the heavy lifting for us.
 *
 * Single-shot
 * ===========
 * When this event is fired, it will notify all active observers (if any).
 * If there is no active observer we will queue the event and will fire
 * it when we have active observers. After that, it will be marked as 'handled'
 * and any observers attached in future will not be notified.
 *
 * This is the main difference between [LiveEvent] & [LiveData]. [LiveData] will
 * notify the future observers to bring them up-to date. This mainly happens during
 * Activity restarts where old observers are detached and new ones are attached.
 *
 * But there are some 'events' which should be handled only once (e.g starting
 * a fragment). This class is used used for those 'events'.
 *
 * Currently, [observeForever] & [removeObserver] are not implemented because we
 * are not using them.
 */
open class LiveEvent<T> : LiveData<T>() {

    /**
     * Whether we are currently firring the event. Observers will be notified
     * only when this is true.
     */
    private var firing = false

    /**
     * Whether someone has handled the last event fired.
     * This is used to implement the "queuing"  behaviour:
     *
     * 1. When event is fired, set this to false
     * 2. If observers are invoked, set this to true
     * 3. In [onActive], if this is still false, re-fire
     */
    private var handled = true


    /**
     * Fire this event with given value.
     * MUST be called from Main thread.
     */
    open fun fire(value: T?) = setValue(value)

    /**
     * Same as [fire], but can be called from any thread.
     */
    fun fireAsync(value: T?) = postValue(value)

    /**
     * Overridden to manage event state.
     */
    override fun setValue(value: T?) {
        firing = true
        handled = false
        super.setValue(value)
        firing = false
    }

    /**
     * Overridden to check for queued event.
     */
    override fun onActive() {
        super.onActive()

        if (!handled)
            fire(value)
    }

    /**
     * Registers an observer for this event.
     */
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        // Observer given to us is wrapped in another Observer
        // which checks current state before invoking given observer.
        super.observe(owner) {
            if (firing) {
                observer.onChanged(it)
                handled = true
            }
        }
    }
}