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
 * This class implements single-shot observable events.
 * It is based on [LiveData] which does the heavy lifting for us.
 *
 * Single-shot
 * ===========
 * When this event is fired, it will notify all active observers.
 * If there is no active observer, it will wait for active observers,
 * so that the event is not "lost". After notifying observers, it will
 * be marked as 'handled' and any future observers will NOT be notified.
 *
 * This is the main difference between this class & [LiveData]. [LiveData] will
 * notify the future observers to bring them up-to date. This can happen during
 * Activity restarts where old observers are detached and new ones are attached.
 *
 * This class is used for events which should be handled only once.
 * e.g starting a fragment.
 *
 * Calling [removeObserver] on [LiveEvent] is NOT supported because we wrap
 * the observer given to us in a custom observer, which is currently not
 * exposed to callers (see [wrapObserver]).
 */
open class LiveEvent<T> : LiveData<T>() {

    /**
     * Whether we are currently firing the event. Observers will be notified
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


    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        super.observe(owner, wrapObserver(observer))
    }

    override fun observeForever(observer: Observer<in T>) {
        super.observeForever(wrapObserver(observer))
    }

    /**
     * Observer given to us is wrapped in another Observer
     * which checks current state before invoking real observer.
     */
    private fun <T> wrapObserver(real: Observer<in T>): Observer<T> {
        return Observer {
            if (firing) {
                real.onChanged(it)
                handled = true
            }
        }
    }
}