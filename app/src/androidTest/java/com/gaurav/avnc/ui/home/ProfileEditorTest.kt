/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import org.hamcrest.core.AllOf.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEditorTest {

    private val testProfile = ServerProfile(name = "foo", host = "bar")

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Rule
    @JvmField
    val dbRule = DatabaseRule()

    @Test
    fun createSimpleProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_add_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers_rv)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    @Test
    fun createAdvancedProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_advanced)).doClick()
        onView(withText(R.string.title_add_server_profile)).checkIsDisplayed()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers_rv)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    //TODO add more tests
}