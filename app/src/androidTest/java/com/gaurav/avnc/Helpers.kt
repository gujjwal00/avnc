/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.ProgressBar
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.AssertionFailedError
import org.hamcrest.core.IsNot.not
import org.junit.Assert
import java.io.BufferedWriter
import java.io.File

/**
 * Global accessors
 */
val instrumentation; get() = InstrumentationRegistry.getInstrumentation()!!
val targetContext; get() = instrumentation.targetContext!!
val targetPrefs by lazy { PreferenceManager.getDefaultSharedPreferences(targetContext)!! }

/**
 * Shorthands
 */
fun ViewInteraction.checkIsDisplayed() = check(matches(isDisplayed()))!!
fun ViewInteraction.checkWillBeDisplayed() = checkWithTimeout(matches(isDisplayed()))
fun ViewInteraction.checkIsNotDisplayed() = check(matches(not(isDisplayed())))!!
fun ViewInteraction.doClick() = perform(click())!!
fun ViewInteraction.doLongClick() = perform(longClick())!!
fun ViewInteraction.doTypeText(text: String) = perform(typeText(text)).perform(closeSoftKeyboard())!!
fun ViewInteraction.inDialog() = inRoot(RootMatchers.isDialog())!!


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

/**
 * Runs given [block] in context of scenario's activity.
 * It simplifies the pattern of getting some value using activity.
 */
fun <A : Activity, R> ActivityScenario<A>.withActivity(block: A.() -> R): R {
    var r: R? = null
    onActivity { r = it.block() }
    return r!!
}

/**
 * Runs given block synchronously on main thread.
 */
fun <R> runOnMainSync(block: () -> R): R {
    var r: R? = null
    instrumentation.runOnMainSync { r = block() }
    return r!!
}

fun getClipboardText(): String? {
    var text: String? = null
    instrumentation.runOnMainSync {
        (targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let {
            text = it.primaryClip?.getItemAt(0)?.text?.toString()
        }
    }
    return text
}

fun setClipboardText(text: String) {
    instrumentation.runOnMainSync {
        (targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(null, text))
    }
}

/**
 * Asserts that given [test] passes against current progress of a [ProgressBar].
 */
class ProgressAssertion(private val test: (Int) -> Boolean) : ViewAssertion {
    override fun check(view: View?, noViewFoundException: NoMatchingViewException?) {
        noViewFoundException?.let { throw it }
        if (view !is ProgressBar) throw AssertionFailedError("View is not a ProgressBar")
        Assert.assertTrue("Progress test failed for '${view.progress}'", test(view.progress))
    }
}

/**
 * Helper for testing file selection by user.
 *
 * It creates a temporary file, populated by invoking [fileWriter]
 * Then Intent response is hooked up to return that file.
 *
 * Note: [Intents.init] must have been called before using this function
 */
fun setupFileOpenIntent(fileWriter: BufferedWriter.() -> Unit) {
    val file = File.createTempFile("avnc", "test")
    file.bufferedWriter().use { it.fileWriter() }
    Intents.intending(IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(file.toUri())))
}

fun setupFileOpenIntent(fileContent: String) = setupFileOpenIntent { write(fileContent) }