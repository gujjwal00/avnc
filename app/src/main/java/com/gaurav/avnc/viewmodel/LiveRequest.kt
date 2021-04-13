/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import java.util.concurrent.LinkedBlockingQueue

/**
 * Extension of [LiveEvent] to facilitate passing some response back to caller.
 *
 * This simplifies the cases where we need some value from User (UI thread)
 * and we want the background thread to block until that value is available.
 */
class LiveRequest<RequestType, ResponseType>() : LiveEvent<RequestType>() {

    private val responses = LinkedBlockingQueue<ResponseType>()

    /**
     * Triggers the request with given value.
     */
    override fun set(value: RequestType) {
        responses.clear()
        super.set(value)
    }

    /**
     * Sets response for current request.
     */
    fun offerResponse(response: ResponseType) = responses.offer(response)

    /**
     * Returns the response set by [offerResponse].
     * Will block until response is available.
     */
    fun takeResponse() = responses.take()
}