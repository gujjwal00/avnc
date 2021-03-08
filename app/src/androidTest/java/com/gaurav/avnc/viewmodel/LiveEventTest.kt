/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import androidx.test.espresso.Espresso.onIdle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.instrumentation
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
    fun futureObserversShouldNotBeNotified() {
        val testEvent = LiveEvent<Boolean>()
        var observer1Notified = false
        var observer2Notified = false
        var futureObserverNotified = false

        instrumentation.runOnMainSync {
            testEvent.observeForever { observer1Notified = true }       // Observer 1
            testEvent.observeForever { observer2Notified = true }       // Observer 2
            testEvent.fire(true)
            testEvent.observeForever { futureObserverNotified = true }  // Observer installed after event was fired
        }

        Assert.assertTrue(observer1Notified)
        Assert.assertTrue(observer2Notified)
        Assert.assertFalse(futureObserverNotified)
    }


    /**
     * But if no observer is active when event is fired, event should be queued,
     * and observers should be notified when they are active.
     */
    @Test
    fun eventShouldNotBeLost() {
        val testEvent = LiveEvent<Boolean>()
        var observerNotified = false

        instrumentation.runOnMainSync {
            testEvent.fire(true)
            testEvent.observeForever { observerNotified = true }
        }

        Assert.assertTrue(observerNotified)
    }

    /**
     * Working of [LiveEvent] relies on setValue() being called in response to fireAsync().
     * This test verifies that assumption.
     */
    @Test
    fun checkSetValueIsCalledAfterFireAsync() {
        val testValue = Any()
        var setValueCalled = false
        var setValueCalledWith: Any? = Any()

        val testEvent = object : LiveEvent<Any>() {
            override fun setValue(value: Any?) {
                super.setValue(value)
                setValueCalled = true
                setValueCalledWith = value
            }
        }

        testEvent.fireAsync(testValue)
        onIdle()

        Assert.assertTrue(setValueCalled)
        Assert.assertSame(testValue, setValueCalledWith)
    }
}