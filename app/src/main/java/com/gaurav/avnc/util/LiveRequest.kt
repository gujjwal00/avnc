/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Extension of [LiveEvent] to facilitate awaiting some response from observers.
 *
 * This simplifies the cases where a background thread needs some value from the user (i.e. UI thread),
 * and we want the background thread to block until that value is available.
 */
class LiveRequest<RequestType, ResponseType> {

    private val liveEvent = LiveEvent<RequestType>()
    private val responses = LinkedBlockingQueue<ResponseType>()

    val value get() = liveEvent.value

    /**
     * Fires this request with given value and returns the response.
     * Will block until any response is available.
     * Cannot be called from main thread.
     * To cancel an ongoing request, interrupt this thread.
     */
    fun getResponseFor(value: RequestType): ResponseType {
        checkNotMainThread()
        liveEvent.fireAsync(value)
        return responses.take() //Blocking call
    }

    /**
     * Sets response for current request.
     */
    fun offerResponse(response: ResponseType) = responses.offer(response)

    /**
     * Adds observer to be notified when [getResponseFor] is executed.
     */
    fun observe(owner: LifecycleOwner, observer: Observer<RequestType>) = liveEvent.observe(owner, observer)
}