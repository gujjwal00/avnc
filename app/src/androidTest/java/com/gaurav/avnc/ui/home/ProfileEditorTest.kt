/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.core.content.edit
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ScrollToAction
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import org.hamcrest.core.AllOf.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicEditorTest {

    private val testProfile = ServerProfile(name = "foo", host = "bar")

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

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

    private fun checkAdvancedModeIsOpen() {
        //This checkbox is only shown in advanced mode
        onView(withText(R.string.title_view_only_mode)).checkWillBeDisplayed()
    }

    @Test
    fun createAdvancedProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()
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
    fun dataSharingBetweenSimpleAndAdvanceMode() {
        // If user switches to advanced mode after making some changes in simple mode,
        // those changes should not be lost.
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)

        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()

        checkAdvancedModeIsOpen()
        onView(allOf(withHint(R.string.hint_host), withText(testProfile.host))).checkIsDisplayed()
        onView(allOf(withHint(R.string.hint_server_name), withText(testProfile.name))).checkIsDisplayed()
    }

    @Test
    fun directlyOpenAdvancedMode() {
        try {
            targetPrefs.edit { putBoolean("prefer_advanced_editor", true) }
            onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
            checkAdvancedModeIsOpen()
        } finally {
            targetPrefs.edit { putBoolean("prefer_advanced_editor", false) }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PrivateKeyTest {

    private val SAMPLE_KEY = """
        -----BEGIN EC PRIVATE KEY-----
        MHcCAQEEIOlzJCBaTq2RpkLatMn+PMwsQ/ncijqK5M6y5kPns+BAoAoGCCqGSM49
        AwEHoUQDQgAE5vfo9Is5+pnk+6dC3NlXA9rKNy0X6PdifyeI2OKKTGVA4cdhEOBh
        MXTgJKarp26FiFnsFuWEGOO61Q/Plmv8Wg==
        -----END EC PRIVATE KEY-----
    """.trimIndent()

    private val SAMPLE_ENCRYPTED_KEY = """
        -----BEGIN EC PRIVATE KEY-----
        Proc-Type: 4,ENCRYPTED
        DEK-Info: AES-128-CBC,D18B845F2B9D4B9FEB3472809F270AD2

        fNNjQEFap5uf3+gaQNI+A2ait7WZDjxPO6sT6MfOTQ34/HRxm7El4FWhhYMfIiGl
        5j/A30Je2OMbOEqTpiKZKYxKxWIZ27te8HpxsQkC9xvU9rxPPsd0Vt/7na5rbx1S
        Iqfy2ows7NJY3f3s/GlMY2ZmaAwk6TJ3DAJ+YK0KMrc=
        -----END EC PRIVATE KEY-----
    """.trimIndent()

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Before
    fun before() {
        Intents.init()
        targetPrefs.edit { putBoolean("prefer_advanced_editor", true) }
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withId(R.id.use_ssh_tunnel)).performWithTimeout(ScrollToAction())
        onView(withId(R.id.use_ssh_tunnel)).doClick()
        onView(withId(R.id.key_import_btn)).performWithTimeout(ScrollToAction())
    }

    @After
    fun after() {
        targetPrefs.edit { putBoolean("prefer_advanced_editor", false) }
        Intents.release()
    }

    @Test
    fun importValidPK() {
        setupFileOpenIntent(SAMPLE_KEY)
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
    }

    @Test
    fun importEncryptedPK() {
        setupFileOpenIntent(SAMPLE_ENCRYPTED_KEY)
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
    }

    @Test
    fun importInvalidFile() {
        setupFileOpenIntent("This is not a Private Key, right?")
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_invalid_key_file)).checkWillBeDisplayed()
    }
}