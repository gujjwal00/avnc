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
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class DiscoveryListTest {

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    private val testProfile = ServerProfile(name = "Test Profile", host = "123.123.123.123")
    private fun testProfileMatcher() = allOf(
            withParent(withId(R.id.discovered_rv)),
            hasDescendant(withText(testProfile.name)),
            hasDescendant(withText(testProfile.host)),
    )

    private fun addTestProfile() {
        // Directly modify internal list to simulate discovery
        activityRule.scenario.onActivity { it.viewModel.discovery.servers.postValue(arrayListOf(testProfile)) }
        onView(testProfileMatcher()).checkWillBeDisplayed()
    }


    @Before
    fun before() {
        // Stop discovery, otherwise tests will hang due to progressbar animation
        // We can't even use the stop button
        activityRule.scenario.onActivity { it.viewModel.stopDiscovery() }

        onView(withId(R.id.pager)).perform(swipeLeft())
        onView(withId(R.id.discovered_rv)).checkWithTimeout(matches(isCompletelyDisplayed()))
        onView(withContentDescription(R.string.desc_discovery_btn)).checkWillBeDisplayed()
    }

    @Test
    fun launchConnection() {
        Intents.init()
        Intents.intending(hasComponent(VncActivity::class.qualifiedName))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        addTestProfile()
        onView(testProfileMatcher()).doClick()

        Intents.intended(hasComponent(VncActivity::class.qualifiedName))
        Intents.assertNoUnverifiedIntents()
        Intents.release()
    }


    @Test
    fun launchProfileSave() {
        addTestProfile()
        onView(allOf(withParent(testProfileMatcher()), withId(R.id.save_btn))).doClick()

        onView(withText(R.string.title_add_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.name)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.host)).inRoot(isDialog()).checkIsDisplayed()
    }

    @Test
    fun copyHost() {
        addTestProfile()
        onView(testProfileMatcher()).doLongClick()
        onView(withText(R.string.title_copy_host)).doClick()

        assertEquals(testProfile.host, getClipboardText())
        onView(withText(R.string.msg_copied_to_clipboard)).checkIsDisplayed()
    }

    @Test
    fun noServerFound() {
        activityRule.scenario.onActivity { it.viewModel.discovery.servers.postValue(arrayListOf()) }
        onView(withText(R.string.tip_discovery)).checkIsDisplayed()
    }
}