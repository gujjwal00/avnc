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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/**
 * Extension of [LiveEvent] to facilitate awaiting some response from observers.
 *
 * This simplifies the cases where a background thread needs some value from the user (i.e. UI thread),
 * and we want the background thread to block until that value is available.
 */
class LiveRequest<RequestType, ResponseType>() {

    private val liveEvent = LiveEvent<RequestType>()
    private val responses = Channel<ResponseType>(Channel.UNLIMITED)

    val value get() = liveEvent.value

    /**
     * Fires this request with given [value] and returns the response.
     * Will block until any response is available, or [scope] is cancelled.
     * Cannot be called from main thread.
     */
    fun getResponseFor(value: RequestType, scope: CoroutineScope): ResponseType {
        checkNotMainThread()
        liveEvent.fireAsync(value)

        return runBlocking(scope.coroutineContext) {
            responses.receive()
        }
    }

    /**
     * Sets response for current request.
     */
    fun offerResponse(response: ResponseType) {
        runBlocking { responses.send(response) }
    }

    /**
     * Adds observer to be notified when [getResponseFor] is executed.
     */
    fun observe(owner: LifecycleOwner, observer: Observer<RequestType>) = liveEvent.observe(owner, observer)
}