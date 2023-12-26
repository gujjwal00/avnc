/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import androidx.lifecycle.Observer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.runOnMainSync
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveEventTest {

    /**
     * [LiveEvent] should NOT notify observer installed after event was fired.
     * This is the main difference between [LiveEvent] & [androidx.lifecycle.LiveData].
     */
    @Test
    fun exactlyOneObserverShouldBeNotified() {
        val testEvent = LiveEvent<Any>()
        var observer1Notified = false
        var observer2Notified = false
        var futureObserverNotified = false

        runOnMainSync {
            testEvent.observeForever { observer1Notified = true }
            testEvent.observeForever { observer2Notified = true }
            testEvent.fire(Any())
            testEvent.observeForever { futureObserverNotified = true }
        }

        Assert.assertTrue(observer1Notified)
        Assert.assertFalse(observer2Notified)
        Assert.assertFalse(futureObserverNotified)
    }


    /**
     * But if no observer is active when event is fired, event should be queued,
     * and observers should be notified when they are active.
     */
    @Test
    fun eventShouldNotBeLost() {
        val testEvent = LiveEvent<Any>()
        var observerNotified = false

        runOnMainSync {
            testEvent.fire(Any())
            testEvent.observeForever { observerNotified = true }
        }

        Assert.assertTrue(observerNotified)
    }

    @Test
    fun testObserverRemoval() {
        val testEvent = LiveEvent<Any>()
        var notified = false

        runOnMainSync {
            val observer = Observer<Any> { notified = true }
            testEvent.observeForever(observer)
            testEvent.removeObserver(observer)
            testEvent.fire(Any())
        }

        Assert.assertFalse(notified)
    }

    @Test
    fun testAsyncFiring() {
        val testEvent = LiveEvent<Int>()
        var observedValue = 0

        runOnMainSync {
            testEvent.observeForever { observedValue = it }
        }

        testEvent.fireAsync(1)
        pollingAssert {
            Assert.assertEquals(1, observedValue)
            Assert.assertEquals(1, testEvent.value)
        }
    }
}