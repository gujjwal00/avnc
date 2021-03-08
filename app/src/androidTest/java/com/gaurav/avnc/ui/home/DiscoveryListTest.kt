/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Activity
import android.app.Instrumentation
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.vnc.Discovery
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


/**
 * For ease of testing, we directly modify the [discovery] instance.
 * TODO: Find better approach to this
 * TODO: Test discovery start/stop
 */
@RunWith(AndroidJUnit4::class)
class DiscoveryListTest {

    private val testProfile = ServerProfile(name = "Test Profile", host = "123.123.123.123")
    private fun testProfileMatcher() = allOf(
            withParent(withId(R.id.discovered_rv)),
            hasDescendant(withText(testProfile.name)),
            hasDescendant(withText(testProfile.host)),
    )

    private lateinit var discovery: Discovery


    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)


    @Before
    fun before() {
        activityRule.scenario.onActivity {
            discovery = it.viewModel.discovery
            discovery.stop()
        }

        onView(withId(R.id.pager)).perform(swipeLeft())
        onView(withId(R.id.discovered_rv)).checkWithTimeout(matches(isCompletelyDisplayed()))
        onView(withContentDescription(R.string.desc_discovery_btn)).checkWillBeDisplayed()
        discovery.servers.postValue(arrayListOf(testProfile))
        onView(testProfileMatcher()).checkWillBeDisplayed()
    }


    @Test
    fun launchConnection() {
        Intents.init()
        Intents.intending(hasComponent(VncActivity::class.qualifiedName))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        onView(testProfileMatcher()).doClick()

        Intents.intended(hasComponent(VncActivity::class.qualifiedName))
        Intents.assertNoUnverifiedIntents()
        Intents.release()
    }


    @Test
    fun launchProfileSave() {
        onView(allOf(withParent(testProfileMatcher()), withId(R.id.save_btn))).doClick()

        onView(withText(R.string.title_add_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.name)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.host)).inRoot(isDialog()).checkIsDisplayed()
    }

    @Test
    fun copyHost() {
        onView(testProfileMatcher()).doLongClick()
        onView(withText(R.string.title_copy_host)).doClick()

        assertEquals(testProfile.host, getClipboardText())
        onView(withText(R.string.msg_copied_to_clipboard)).checkIsDisplayed()
    }

    @Test
    fun emptyListTest() {
        discovery.servers.postValue(arrayListOf())

        onView(withContentDescription(R.string.desc_discovery_btn)).checkWillBeDisplayed()
        onView(withText(R.string.tip_discovery)).checkIsDisplayed()
    }
}