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
import com.gaurav.avnc.VncSessionScenario
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doLongClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualKeysTest : VncSessionTest() {

    @JvmField
    @Rule
    val prefsRule = CleanPrefsRule()

    @Test
    fun basicTest() {
        vncSession.run {
            // Should be visible
            onView(withText("Ctrl")).checkIsDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()

            // Send Tab
            onView(withText("Tab")).doClick()
        }

        //Tab should be received by the server
        assertEquals(arrayListOf(XKeySym.XK_Tab), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun showAllKeys() {
        targetPrefs.edit { putBoolean("vk_show_all", true) }

        vncSession.run {
            onView(withText("Insert")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("Delete")).perform(scrollTo()).checkIsDisplayed()
            onView(withText("F1")).perform(scrollTo()).checkIsDisplayed()
        }
    }


    @Test
    fun openWithToolbar() {
        targetPrefs.edit { putBoolean("run_info_show_virtual_keys", false) }

        vncSession.run {
            onView(withText("Ctrl")).check(doesNotExist())
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
        assertEquals(arrayListOf(XKeySym.XK_Tab), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun openAlongWithKeyboard() {
        targetPrefs.edit {
            putBoolean("vk_open_with_keyboard", true)
            putBoolean("run_info_show_virtual_keys", false)
        }

        vncSession.run {
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
        VncSessionScenario().run {
            // Should open by default, so close it
            onView(withContentDescription("Close virtual keys")).checkWillBeDisplayed().doClick()
            onView(withText("Ctrl")).checkIsNotDisplayed()
        }

        VncSessionScenario().run {
            // Was closed, so should remain closed
            onIdle()
            Thread.sleep(400)
            onView(withText("Ctrl")).check(doesNotExist())


            // Now open it
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.virtual_keys_btn)).doClick()

            onView(withText("Ctrl")).checkWillBeDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
        }

        VncSessionScenario().run {
            // Should open by default now
            onView(withText("Ctrl")).checkWillBeDisplayed()
            onView(withText("Alt")).checkIsDisplayed()
            onView(withText("Tab")).checkIsDisplayed()
        }
    }

    @Test
    fun textBoxVisibilityShouldBeSavedAcrossSessions() {
        VncSessionScenario().run {
            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withHint(R.string.hint_send_text_to_server)).checkIsDisplayed()
        }

        VncSessionScenario().run {
            onView(withHint(R.string.hint_send_text_to_server)).checkWillBeDisplayed()
        }
    }

    @Test
    fun textBoxInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        vncSession.run {
            onView(withText("Ctrl")).checkWillBeDisplayed()

            onView(withId(R.id.pager)).perform(ViewPagerActions.scrollToLast(false))
            onView(withHint(R.string.hint_send_text_to_server))
                    .checkIsDisplayed()
                    .doTypeText(text)
                    .perform(pressImeActionButton())
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = vncSession.server.receivedKeyDowns.filter { it != XKeySym.XK_Shift_L }.toList()

        assertEquals(sentByClient, receivedOnServer)
    }

    @Test
    fun superWithSingleTap() {
        targetPrefs.edit { putBoolean("vk_use_super_with_single_tap", true) }
        vncSession.run {
            onView(withContentDescription("Super")).checkWillBeDisplayed().doClick()
        }

        assertEquals(listOf(Pair(XKeySym.XK_Super_L, true), Pair(XKeySym.XK_Super_L, false)),
                     vncSession.server.receivedKeySyms)
    }

    @Test
    fun vkModifierKeysShouldApplyToKeyPressedOnKeyboard() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doClick()
            onView(withId(R.id.frame_view)).doTypeText("a") // Should be sent as uppercase A to server
        }
        assertEquals(listOf(XKeySym.XK_Shift_L, XKeySym.XK_A), vncSession.server.receivedKeyDowns)
    }

    @Test
    fun unlockedToggleKeysShouldBeReleasedWithNextKey() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doClick()

            onView(withText("Shift")).check(matches(isChecked()))
            onView(withId(R.id.frame_view)).perform(pressKey(KeyEvent.KEYCODE_A))
            onView(withText("Shift")).check(matches(isNotChecked()))
        }
    }

    @Test
    fun lockedToggleKeysShouldRemainLockedAfterNextKeys() {
        vncSession.run {
            onView(withText("Shift")).checkWillBeDisplayed().doLongClick() // Long-click locks the key

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