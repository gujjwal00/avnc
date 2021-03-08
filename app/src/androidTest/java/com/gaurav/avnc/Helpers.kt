/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import com.gaurav.avnc.viewmodel.BaseViewModel
import org.hamcrest.core.IsNot.not

/**
 * Global accessors
 */
val instrumentation; get() = InstrumentationRegistry.getInstrumentation()!!
val targetContext; get() = instrumentation.targetContext!!

/**
 * Shorthands
 */
fun ViewInteraction.checkIsDisplayed() = check(matches(isDisplayed()))!!
fun ViewInteraction.checkWillBeDisplayed() = checkWithTimeout(matches(isDisplayed()))
fun ViewInteraction.checkIsNotDisplayed() = check(matches(not(isDisplayed())))!!
fun ViewInteraction.doClick() = perform(click())!!
fun ViewInteraction.doLongClick() = perform(longClick())!!
fun ViewInteraction.doTypeText(text: String) = perform(typeText(text))!!


/**
 * Checks given [assertion] repeatedly until it succeeds, or timeout expires.
 *
 * This is very useful when dealing with animations & database operations.
 * With this, we can avoid having to implement random Idealing Resources
 * throughout the source code.
 */
fun ViewInteraction.checkWithTimeout(assertion: ViewAssertion, timeout: Int = 5000): ViewInteraction {
    val runUntil = SystemClock.elapsedRealtime() + timeout
    var t: Throwable? = null

    while (SystemClock.elapsedRealtime() < runUntil) {
        runCatching { check(assertion) }
                .onSuccess { return this }
                .onFailure {
                    t = it
                    Thread.sleep(20)
                }
    }

    throw Exception("Assertion did not become valid within timeout", t)
}

fun getClipboardText(): String? {
    var text: String? = null
    instrumentation.runOnMainSync {
        // Reusing BaseViewModel to access clipboard
        text = BaseViewModel(ApplicationProvider.getApplicationContext<App>()).getClipboardText()
    }
    return text
}