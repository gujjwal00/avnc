/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.runOnMainSync
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveEventTest {

    // A lifecycle owner which is in resumed state
    class ActiveOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle get() = registry

        init {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

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
            testEvent.observe(ActiveOwner()) { observer1Notified = true }
            testEvent.observe(ActiveOwner()) { observer2Notified = true }
            testEvent.fire(Any())
            testEvent.observe(ActiveOwner()) { futureObserverNotified = true }
        }

        Assert.assertTrue(observer1Notified)
        Assert.assertFalse(observer2Notified)
        Assert.assertFalse(futureObserverNotified)
    }


    /**
     * But if no observer is active when event is fired, event should be queued,
     * and the observer should be notified when it becomes active.
     */
    @Test
    fun eventShouldNotBeLost() {
        val testEvent = LiveEvent<Any>()
        var observerNotified = false

        runOnMainSync {
            testEvent.fire(Any())
            testEvent.observe(ActiveOwner()) { observerNotified = true }
        }

        Assert.assertTrue(observerNotified)
    }

    @Test
    fun testAsyncFiring() {
        val testEvent = LiveEvent<Int>()
        var observedValue = 0

        runOnMainSync {
            testEvent.observe(ActiveOwner()) { observedValue = it }
        }

        testEvent.fireAsync(1)
        pollingAssert {
            Assert.assertEquals(1, observedValue)
            Assert.assertEquals(1, testEvent.value)
        }
    }
}