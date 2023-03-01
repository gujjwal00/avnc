/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.vnc.XKeySym
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VncActivityTest {

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
    fun virtualKeys() {
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
        Assert.assertEquals(arrayListOf(XKeySym.XK_Tab), testServer.receivedKeySyms)
    }

    @Test
    fun autoOpenVirtualKeys() {
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
    fun textInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        testWrapper {
            onView(withId(R.id.frame_view)).doTypeText(text)
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = testServer.receivedKeySyms.filter { it != XKeySym.XK_Shift_L }.toList()

        Assert.assertEquals(sentByClient, receivedOnServer)
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


}