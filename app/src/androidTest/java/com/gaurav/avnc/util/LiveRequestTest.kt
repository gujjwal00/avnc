/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.runOnMainSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(AndroidJUnit4::class)
class LiveRequestTest {

    @Test
    fun simpleRequest() {
        val liveRequest = LiveRequest<Any, Any>(Any(), null)
        val requestData = Any()
        val responseData = Any()
        var result: Any? = null

        runOnMainSync {
            liveRequest.observe(LiveEventTest.ActiveOwner()) {
                Assert.assertSame(requestData, liveRequest.value)
                liveRequest.offerResponse(responseData)
            }
        }
        thread {
            result = liveRequest.requestResponse(requestData)
        }
        pollingAssert {
            Assert.assertSame(responseData, result)
        }
    }

    @Test
    fun requestShouldBeCanceledOnScopeTermination() {
        val scope = CoroutineScope(EmptyCoroutineContext)
        val canceledValue = Any()
        val liveRequest = LiveRequest<Any, Any>(canceledValue, scope)
        var result: Any? = null

        thread {
            result = liveRequest.requestResponse(Any())
        }

        scope.cancel()
        pollingAssert {
            Assert.assertSame(canceledValue, result)
        }
    }
}