/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.ProgressAssertion
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.onToast
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.setClipboardHtml
import com.gaurav.avnc.setClipboardText
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.vnc.XKeySym
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    private fun Intent.extraList() = extras?.keySet()?.map {
        @Suppress("DEPRECATION")
        extras?.get(it)
    }

    @Test
    fun savedProfilesShouldBePassedByID() {
        val profile = ServerProfile(ID = 1234, host = "example.com")
        val intent = createVncIntent(targetContext, profile)
        val extras = intent.extraList()
        assertNotNull(extras)
        assertFalse(extras!!.contains(profile))
        assertTrue(extras.contains(profile.ID))
    }

    @Test
    fun unsavedProfilesShouldBePassedByValue() {
        val profile = ServerProfile(ID = 0, host = "example.com")
        val intent = createVncIntent(targetContext, profile)
        val extras = intent.extraList()
        assertNotNull(extras)
        assertTrue(extras!!.contains(profile))
        assertFalse(extras.contains(profile.ID))
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun invalidProfileID() {
        val profile = ServerProfile(ID = 12456789123, host = "example.com")
        val intent = createVncIntent(targetContext, profile)
        ActivityScenario.launch<VncActivity>(intent).use {
            onToast(withText("Error: Invalid Server ID")).checkWillBeDisplayed()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class VncActivityTest {

    private lateinit var testServer: TestServer
    private lateinit var profile: ServerProfile

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    //TODO: Simplify these tests
    private fun testWrapper(useDatabase: Boolean = false, profileModifier: ((ServerProfile) -> Unit)? = null,
                            block: (ActivityScenario<VncActivity>) -> Unit) {
        testServer = TestServer()
        testServer.start()

        profile = ServerProfile(host = testServer.host, port = testServer.port)
        profileModifier?.invoke(profile)
        if (useDatabase) {
            runBlocking {
                profile.ID = dbRule.db.serverProfileDao.insert(profile)
            }
        }
        val intent = createVncIntent(targetContext, profile)

        ActivityScenario.launch<VncActivity>(intent).use {
            onView(withId(R.id.frame_view)).checkWillBeDisplayed()            // Wait for connection
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.close()) // Suppress initial drawer open
            block(it)
        }

        testServer.awaitStop()
    }

    @Test
    fun openKeyboard() {
        testWrapper {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.keyboard_btn)).doClick()
            onIdle()

            val imm = targetContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            Assert.assertTrue(imm.isAcceptingText)
        }
    }

    @Test
    fun textInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        testWrapper {
            onView(withId(R.id.frame_view)).doTypeText(text)
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = testServer.receivedKeySyms.filter { it != XKeySym.XK_Shift_L }.toList()

        assertEquals(sentByClient, receivedOnServer)
    }

    @Test
    fun autoReconnectEnabled() {
        targetPrefs.edit { putBoolean("auto_reconnect", true) }
        try {
            val intent = createVncIntent(targetContext, ServerProfile(host = "CentralPerk.test"))
            ActivityScenario.launch<VncActivity>(intent).use {
                onView(withId(R.id.auto_reconnect_progress)).checkWillBeDisplayed()
                Thread.sleep(1500)
                onView(withId(R.id.auto_reconnect_progress)).check(ProgressAssertion { it > 0 })
            }
        } finally {
            targetPrefs.edit { putBoolean("auto_reconnect", false) }
        }
    }

    @Test
    fun autoReconnectDisabled() {
        targetPrefs.edit { putBoolean("auto_reconnect", false) }
        val intent = createVncIntent(targetContext, ServerProfile(host = "CentralPerk.test"))
        ActivityScenario.launch<VncActivity>(intent).use {
            onView(withId(R.id.auto_reconnect_progress)).checkIsNotDisplayed()
            Thread.sleep(1500)
            onView(withId(R.id.auto_reconnect_progress)).check(ProgressAssertion { it == 0 })
        }
    }

    @Test
    fun clientToServerClipboard() {
        val sample = "Pivot! Pivot! Pivot! Pivot!!!"
        setClipboardText(sample)
        testWrapper {
            pollingAssert { assertEquals(sample, testServer.receivedCutText) }
        }
    }

    @Test
    fun clientToServerClipboardWithHtmlClip() {
        val sample = "Pivot! Pivot! Pivot! Pivot!!!"
        setClipboardHtml(sample)
        testWrapper {
            pollingAssert { assertEquals(sample, testServer.receivedCutText) }
        }
    }


    /*************************** Toolbar *******************************************/
    @Test
    fun gestureStyleUiTouchpad() {
        testWrapper(profileModifier = { it.gestureStyle = "touchpad" }) {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_touchpad))
                    .checkWillBeDisplayed()
                    .check(matches(isChecked()))
        }
    }

    @Test
    fun gestureStyleUiTouchscreen() {
        testWrapper(profileModifier = { it.gestureStyle = "touchscreen" }) {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_touchscreen))
                    .checkWillBeDisplayed()
                    .check(matches(isChecked()))
        }
    }

    @Test
    fun gestureStyleChange() {
        testWrapper(useDatabase = true) {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_auto)).checkWillBeDisplayed()
            onView(withText(R.string.pref_gesture_style_auto)).check(matches(isChecked()))

            fun loadProfile() = runBlocking { dbRule.db.serverProfileDao.getByID(profile.ID) }

            // Test switching to touchpad
            onView(withId(R.id.gesture_style_touchpad)).doClick()
            pollingAssert { assertEquals("touchpad", loadProfile()?.gestureStyle) }
            it.onActivity { a -> assertEquals("touchpad", a.viewModel.activeGestureStyle.value) }
        }
    }
}