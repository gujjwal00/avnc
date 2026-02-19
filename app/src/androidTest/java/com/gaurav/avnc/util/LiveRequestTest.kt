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
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class LiveRequestTest {

    @Test
    fun simpleRequest() {
        val liveRequest = LiveRequest<Any, Any>()
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
            result = liveRequest.getResponseFor(requestData)
        }
        pollingAssert {
            Assert.assertSame(responseData, result)
        }
    }

    @Test
    fun requestShouldBeCanceledOnThreadInterrupt() {
        val liveRequest = LiveRequest<Any, Any>()

        thread {
            Assert.assertThrows(InterruptedException::class.java) {
                liveRequest.getResponseFor(Any())
            }
        }.let {
            Thread.sleep(100)
            it.interrupt()
            it.join()
        }
    }
}