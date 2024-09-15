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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue

/**
 * Extension of [LiveEvent] to facilitate awaiting some response from observers.
 *
 * This simplifies the cases where a background thread needs some value from the user (i.e. UI thread),
 * and we want the background thread to block until that value is available.
 *
 * If a request is canceled then [requestResponse] will return [cancellationValue].
 *
 * @param scope can be specified to auto-cancel this request on scope cancellation.
 */
class LiveRequest<RequestType, ResponseType>(private val cancellationValue: ResponseType, scope: CoroutineScope?) {

    private val liveEvent = LiveEvent<RequestType>()
    private val responses = LinkedBlockingQueue<ResponseType>()

    init {
        scope?.launch { awaitCancellation() }?.invokeOnCompletion { offerResponse(cancellationValue) }
    }

    val value get() = liveEvent.value

    /**
     * Fires this request with given value and returns the response.
     * Will block until any response is available.
     * Can be called from any threads.
     */
    fun requestResponse(value: RequestType): ResponseType {
        liveEvent.fireAsync(value)
        return responses.take() //Blocking call
    }

    /**
     * Sets response for current request.
     */
    fun offerResponse(response: ResponseType) = responses.offer(response)

    /**
     * Adds observer to be notified when [requestResponse] is executed.
     */
    fun observe(owner: LifecycleOwner, observer: Observer<RequestType>) = liveEvent.observe(owner, observer)
}