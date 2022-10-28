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
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.PositionAssertions.isCompletelyAbove
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
import kotlinx.coroutines.runBlocking
import org.hamcrest.core.AllOf.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class tests working of the server profile list.
 *
 * Before each test, database is cleared and [testProfile] is inserted.
 */
@RunWith(AndroidJUnit4::class)
class ServerListTest {

    private val testProfile = ServerProfile(name = "Test Profile", host = "123.123.123.123")
    private fun testProfileMatcher() = allOf(
            withParent(withId(R.id.servers_rv)),
            hasDescendant(withText(testProfile.name)),
            hasDescendant(withText(testProfile.host)),
    )


    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Rule
    @JvmField
    val dbRule = DatabaseRule()

    @Before
    fun before() {
        runBlocking { dbRule.db.serverProfileDao.insert(testProfile) }
    }

    @Test
    fun launchConnection() {
        // Setup expected intent
        Intents.init()
        Intents.intending(hasComponent(VncActivity::class.qualifiedName))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        onView(testProfileMatcher()).doClick()

        Intents.intended(hasComponent(VncActivity::class.qualifiedName))
        Intents.assertNoUnverifiedIntents()
        Intents.release()
    }


    @Test
    fun launchEditor() {
        onView(testProfileMatcher()).doLongClick()
        onView(withText(R.string.title_edit)).doClick()

        onView(withText(R.string.title_edit_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.name)).inRoot(isDialog()).checkIsDisplayed()
        onView(withText(testProfile.host)).inRoot(isDialog()).checkIsDisplayed()
    }

    @Test
    fun deleteProfile() {
        onView(testProfileMatcher()).doLongClick()
        onView(withText(R.string.title_delete)).doClick()

        onView(withText(R.string.msg_server_profile_deleted)).checkWillBeDisplayed()
        onView(withText(R.string.tip_empty_server_list)).checkIsDisplayed()

        // Test Undo
        onView(withText(R.string.title_undo)).checkWithTimeout(matches(isCompletelyDisplayed())).doClick()

        onView(testProfileMatcher()).checkWillBeDisplayed()
        onView(withText(R.string.tip_empty_server_list)).checkIsNotDisplayed()
    }

    @Test
    fun copyHost() {
        onView(testProfileMatcher()).doLongClick()
        onView(withText(R.string.title_copy_host)).doClick()

        assertEquals(testProfile.host, getClipboardText())
        onView(withText(R.string.msg_copied_to_clipboard)).checkIsDisplayed()
    }

    @Test
    fun sortServers() {
        val prefEditor = PreferenceManager.getDefaultSharedPreferences(targetContext).edit()

        with(dbRule.db.serverProfileDao) {
            runBlocking {
                deleteAll()
                insert(ServerProfile(name = "pqr"))
                insert(ServerProfile(name = "abc"))
            }
        }

        //Without sorting, "pqr" should be above "abc", as it was inserted first
        prefEditor.putBoolean("sort_server_list", false).apply()
        onView(withText("pqr")).checkWithTimeout(isCompletelyAbove(withText("abc")))


        //With sorting, "abc" should be above "pqr"
        prefEditor.putBoolean("sort_server_list", true).commit()
        onView(withText("abc")).checkWithTimeout(isCompletelyAbove(withText("pqr")))
    }
}