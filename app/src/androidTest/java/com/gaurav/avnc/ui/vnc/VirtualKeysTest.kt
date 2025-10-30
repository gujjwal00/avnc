/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.view.KeyEvent
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.ViewPagerActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doLongClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualKeysTest {

    @JvmField
    @Rule
    val prefsRule = CleanPrefsRule()

    private lateinit var testServer: TestServer

    //TODO: Simplify these tests
    private fun testWrapper(block: () -> Unit) {
        testServer = TestServer()
        testServer.start()

        val profile = ServerProfile(host = testServer.host, port = testServer.port)
        val intent = createVncIntent(targetContext, profile)

        ActivityScenario.launch<VncActivity>(intent).use {
            onView(withId(R.id.frame_view)).checkWillBeDisplayed()            // Wait for connection
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.close()) // Suppress initial drawer open
            block()
        }

        testServer.awaitStop()
    }

    @Before
    fun before() {
        targetPrefs.edit { putBoolean("run_info_has_shown_viewer_help", true) }
    }

    @Test
    fun basicTest() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()

            // Send Tab
            onView(withText("Tab")).doClick()
        }

        //Tab should be received by the server
        assertEquals(arrayListOf(XKeySym.XK_Tab), testServer.receivedKeyDowns)
    }

    @Test
    fun showAllKeys() {
        testWrapper {
            targetPrefs.edit { putBoolean("vk_show_all", true) }
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Insert")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("Delete")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("F1")).perform(scrollTo()).checkIsDisplayed()
        }
    }

    @Test
    fun openWithKeyboard() {
        testWrapper {
            targetPrefs.edit { putBoolean("vk_open_with_keyboard", true) }
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.keyboard_btn)).doClick()
            onIdle()

            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()
        }
    }

    @Test
    fun visibilityShouldBeSavedAcrossSessions() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()
            onIdle()
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()
        }

        testWrapper {
            // Was open previously, so expect it to open
            onIdle()
            onView(withText("Ctrl")).checkWillBeDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()

            // close it
            onView(withContentDescription("Close virtual keys")).doClick()
            onView(withText("Ctrl")).checkIsNotDisplayed()
        }

        testWrapper {
            // should remain closed
            onIdle()
            Thread.sleep(100)
            onView(withText("Ctrl")).check(doesNotExist())
        }
    }

    @Test
    fun textBoxInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()
            onView(withText("Ctrl")).checkIsDisplayed()

            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withHint(R.string.hint_send_text_to_server))
                    .checkIsDisplayed()
                    .doTypeText(text)
                    .perform(pressImeActionButton())
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = testServer.receivedKeyDowns.filter { it != XKeySym.XK_Shift_L }.toList()

        assertEquals(sentByClient, receivedOnServer)
    }

    @Test
    fun vkModifierKeysShouldApplyToKeyPressedOnKeyboard() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Shift")).checkWillBeDisplayed().doClick()
            onView(withId(R.id.frame_view)).doTypeText("a") // Should be sent as uppercase A to server
        }
        assertEquals(listOf(XKeySym.XK_Shift_R, XKeySym.XK_A), testServer.receivedKeyDowns)
    }

    @Test
    fun unlockedToggleKeysShouldBeReleasedWithNextKey() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Shift")).checkIsDisplayed().doClick()

            onView(withText("Shift")).check(matches(isChecked()))
            onView(withId(R.id.frame_view)).perform(pressKey(KeyEvent.KEYCODE_A))
            onView(withText("Shift")).check(matches(isNotChecked()))
        }
    }

    @Test
    fun lockedToggleKeysShouldRemainLockedAfterNextKeys() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Shift")).checkIsDisplayed().doLongClick() // Long-click locks the key

            onView(withText("Shift")).check(matches(isChecked()))
            onView(withId(R.id.frame_view))
                    .perform(pressKey(KeyEvent.KEYCODE_A))
                    .perform(pressKey(KeyEvent.KEYCODE_B))
                    .perform(pressKey(KeyEvent.KEYCODE_C))
            onView(withText("Shift")).check(matches(isChecked()))
        }
    }

    @Test
    fun defaultConfigTest() {
        val prefs = runOnMainSync { AppPreferences(targetContext) }
        val defaultKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        targetPrefs.edit { putBoolean("vk_show_all", true) }
        val defaultAllKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        Assert.assertNotEquals(defaultKeys, defaultAllKeys)

        // For now, duplicate keys are not allowed
        assertEquals(defaultKeys, defaultKeys.distinct())
        assertEquals(defaultAllKeys, defaultAllKeys.distinct())
    }

    @Test
    fun corruptedConfigTest() {
        targetPrefs.edit { putString("vk_keys_layout", "foobar") }
        val prefs = runOnMainSync { AppPreferences(targetContext) }

        val keys = VirtualKeyLayoutConfig.getLayout(prefs)
        val defaultKeys = VirtualKeyLayoutConfig.getDefaultLayout(prefs)

        // If for some reason layout pref is corrupted, default config should be loaded
        assertEquals(defaultKeys, keys)
    }
}