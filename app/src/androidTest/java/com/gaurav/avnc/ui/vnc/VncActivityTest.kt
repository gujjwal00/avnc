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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.ProgressAssertion
import com.gaurav.avnc.R
import com.gaurav.avnc.SshTunnelScenario
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.checkIsDisplayed
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
import com.gaurav.avnc.util.forgetKnownHosts
import com.gaurav.avnc.vnc.XKeySym
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
class VncActivityTest : VncSessionTest() {

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Rule
    @JvmField
    val prefRule = CleanPrefsRule()

    private fun loadProfileFromDB() = runBlocking {
        dbRule.db.serverProfileDao.getByID(vncSession.profile.ID)
    }


    @Test
    fun openKeyboard() {
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.keyboard_btn)).doClick()
            onIdle()

            val imm = targetContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            assertTrue(imm.isAcceptingText)
        }
    }

    @Test
    fun textInput() {
        val text = "abcxyzABCXYZ1234567890{}[]()`~@#$%^&*_+-=/*"

        vncSession.run {
            onView(withId(R.id.frame_view)).doTypeText(text)
        }

        val sentByClient = text.toCharArray().map { it.code }.toList()
        val receivedOnServer = vncSession.server.receivedKeyDowns.filter { it != XKeySym.XK_Shift_L }.toList()

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
        vncSession.run {
            pollingAssert { assertEquals(sample, vncSession.server.receivedCutText) }
        }
    }

    @Test
    fun clientToServerClipboardWithHtmlClip() {
        val sample = "Pivot! Pivot! Pivot! Pivot!!!"
        setClipboardHtml(sample)
        vncSession.run {
            pollingAssert { assertEquals(sample, vncSession.server.receivedCutText) }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    fun remoteBackPressOnMouseBack() {
        targetPrefs.edit { putString("mouse_back", "remote-back-press") }
        vncSession.run {
            vncSession.onActivity { activity ->
                val mouseDevice = mockk<InputDevice>()
                every { mouseDevice.supportsSource(InputDevice.SOURCE_MOUSE) } returns true
                mockkStatic(InputDevice::getDevice) {
                    every { InputDevice.getDevice(any()) } returns mouseDevice

                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                }
            }
        }
        assertEquals(XKeySym.XF86XK_Back, vncSession.server.receivedKeyDowns.getOrNull(0))
    }


    /*************************** Toolbar *******************************************/
    @Test
    fun gestureStyleUiTouchpad() {
        vncSession.profile.gestureStyle = "touchpad"
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_touchpad))
                    .checkWillBeDisplayed()
                    .check(matches(isChecked()))
        }
    }

    @Test
    fun gestureStyleUiTouchscreen() {
        vncSession.profile.gestureStyle = "touchscreen"
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_touchscreen))
                    .checkWillBeDisplayed()
                    .check(matches(isChecked()))
        }
    }

    @Test
    fun gestureStyleChange() {
        vncSession.saveProfileToDB(dbRule.db)
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.gesture_style_toggle)).checkWillBeDisplayed().doClick()
            onView(withText(R.string.pref_gesture_style_auto)).checkWillBeDisplayed()
            onView(withText(R.string.pref_gesture_style_auto)).check(matches(isChecked()))

            // Test switching to touchpad
            onView(withId(R.id.gesture_style_touchpad)).doClick()
            pollingAssert { assertEquals("touchpad", loadProfileFromDB()?.gestureStyle) }
            vncSession.onActivity { a -> assertEquals("touchpad", a.viewModel.activeGestureStyle.value) }
        }
    }

    @Test
    fun normalViewMode() {
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.view_modes_toggle)).checkWillBeDisplayed().doClick()
            onView(withContentDescription(R.string.desc_view_mode_normal))
                    .checkWillBeDisplayed()
                    .check(matches(isChecked())) // Normal mode is the default

            onView(withContentDescription(R.string.desc_view_mode_no_input)).check(matches(isNotChecked()))
            onView(withContentDescription(R.string.desc_view_mode_no_video)).check(matches(isNotChecked()))
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())

            onView(withId(R.id.frame_view)).checkIsDisplayed()
            onView(withText(R.string.msg_video_disabled)).check(doesNotExist())
        }
    }

    @Test
    fun noInputMode() {
        vncSession.saveProfileToDB(dbRule.db)
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.view_modes_toggle)).checkWillBeDisplayed().doClick()
            onView(withContentDescription(R.string.desc_view_mode_no_input))
                    .checkWillBeDisplayed()
                    .check(matches(isNotChecked()))
                    .doClick()

            onView(withContentDescription(R.string.desc_view_mode_normal)).check(matches(isNotChecked()))
            onView(withContentDescription(R.string.desc_view_mode_no_input)).check(matches(isChecked()))
            onView(withContentDescription(R.string.desc_view_mode_no_video)).check(matches(isNotChecked()))
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())

            onView(withText(R.string.msg_video_disabled)).check(doesNotExist())
            onView(withId(R.id.frame_view)).checkIsDisplayed().doTypeText("abc")

            pollingAssert { assertEquals(ServerProfile.VIEW_MODE_NO_INPUT, loadProfileFromDB()?.viewMode) }
            vncSession.onActivity { a -> assertEquals(ServerProfile.VIEW_MODE_NO_INPUT, a.viewModel.activeViewMode.value) }
        }

        assertEquals(0, vncSession.server.receivedKeyDowns.size)
    }

    @Test
    fun noVideoMode() {
        vncSession.saveProfileToDB(dbRule.db)
        vncSession.run {
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withId(R.id.view_modes_toggle)).checkWillBeDisplayed().doClick()
            onView(withContentDescription(R.string.desc_view_mode_no_video))
                    .checkWillBeDisplayed()
                    .check(matches(isNotChecked()))
                    .doClick()

            onView(withContentDescription(R.string.desc_view_mode_normal)).check(matches(isNotChecked()))
            onView(withContentDescription(R.string.desc_view_mode_no_input)).check(matches(isNotChecked()))
            onView(withContentDescription(R.string.desc_view_mode_no_video)).check(matches(isChecked()))
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.close())

            onView(withText(R.string.msg_video_disabled)).checkWillBeDisplayed()

            pollingAssert { assertEquals(ServerProfile.VIEW_MODE_NO_VIDEO, loadProfileFromDB()?.viewMode) }
            vncSession.onActivity { a ->
                assertEquals(ServerProfile.VIEW_MODE_NO_VIDEO, a.viewModel.activeViewMode.value)
                repeat(10) {
                    a.viewModel.refreshFrameBuffer()
                }
            }
        }

        // no-video mode is implemented by stopping incremental updates
        assertEquals(1, vncSession.server.receivedIncrementalUpdateRequests)
    }

    @Test
    fun openToolbarWithButton() {
        targetPrefs.edit {
            putBoolean("toolbar_open_with_button", true)
        }

        vncSession.run {
            onView(withId(R.id.open_toolbar_btn)).checkWillBeDisplayed().doClick()
            onView(withId(R.id.keyboard_btn)).checkWillBeDisplayed()
            onView(withId(R.id.virtual_keys_btn)).checkIsDisplayed()
            onView(withId(R.id.zoom_options_toggle)).checkIsDisplayed()
        }
    }
}

@SdkSuppress(minSdkVersion = 26) // Mina SSHD requires NIO classes
class SshTunnelTest {
    companion object {
        const val USER = "Ross"
        const val PASSWORD = "Pivot!"
        const val KEY = """
                        -----BEGIN OPENSSH PRIVATE KEY-----
                        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAaAAAABNlY2RzYS
                        1zaGEyLW5pc3RwMjU2AAAACG5pc3RwMjU2AAAAQQSsy1odRW+GqZckvbcZ83gb57HbGeqE
                        /PwUGZJ4nbE/hUSCKi8P84Nt4F8eXXUZNbyD1316oxhcvI46kUXijn7cAAAAqADu8ZMA7v
                        GTAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBKzLWh1Fb4aplyS9
                        txnzeBvnsdsZ6oT8/BQZknidsT+FRIIqLw/zg23gXx5ddRk1vIPXfXqjGFy8jjqRReKOft
                        wAAAAgDYCqrzLv6vvAVb9hsyTpfT38eFTJfewpJjtLKMio5eAAAAAPZ2F1cmF2QGVsZWN0
                        cm9uAQ==
                        -----END OPENSSH PRIVATE KEY-----
                        """

        const val ENCRYPTED_KEY = """
                        -----BEGIN OPENSSH PRIVATE KEY-----
                        b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABBwn299AK
                        nRIs6CuasauHZ3AAAAGAAAAAEAAABoAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlz
                        dHAyNTYAAABBBIWxgMc+OMzJX7ZGmluw5jWmCIHg2xrLvQFXBtPmEfi08ZyfNi+bny2R9U
                        LD4RWmnqkW2AjnZQKbTBei7nQSKOkAAACwmuKiwE391rPtzSJBezBv4+TTKk2Eadkd/w85
                        nROToV6IJWYWn6mG2wHrJ5OqqWnrMBj9cOph+86JFJZ8/EYeCZqgDEDsl5mbo/fIaqQ/jD
                        1Yc2jQLCUqaTlgxZIsU6B4+m3OeqfHvCdcZZZdSpn/quPFdcO6uGdLypL8uVQ84C1pJxFf
                        xry5mdsKdaUiC1ILpwf/+2chAA6h81E/G+RiDN8KuMNEkmbQf4xnj9IL3XE=
                        -----END OPENSSH PRIVATE KEY-----
                        """
        const val ENCRYPTED_KEY_PASSWORD = "1234"
    }

    @Before
    fun before() {
        forgetKnownHosts(targetContext)
    }

    @Test
    fun sshTunnelWithPassword() {
        SshTunnelScenario().apply {
            setupAuthWithPassword(USER, PASSWORD)
            start()
            checkAndTrustHostFingerprint()
            vncSession.assertConnected()
            stop()
        }
    }

    @Test
    fun sshTunnelWithoutSavingPassword() {
        SshTunnelScenario().apply {
            setupAuthWithPassword(USER, PASSWORD)
            profile.sshPassword = "" // Clear password
            start()
            checkAndTrustHostFingerprint()

            onView(withText(R.string.title_ssh_login)).checkWillBeDisplayed()
            onView(withHint(R.string.hint_password)).doTypeText(PASSWORD)
            onView(withText(android.R.string.ok)).doClick()

            vncSession.assertConnected()
            stop()
        }
    }

    @Test
    fun sshTunnelWithKey() {
        SshTunnelScenario().apply {
            setupAuthWithKey(USER, KEY, null)
            start()
            checkAndTrustHostFingerprint()
            vncSession.assertConnected()
            stop()
        }
    }

    @Test
    fun sshTunnelWithEncryptedKey() {
        SshTunnelScenario().apply {
            setupAuthWithKey(USER, ENCRYPTED_KEY, ENCRYPTED_KEY_PASSWORD)
            start()
            checkAndTrustHostFingerprint()

            onView(withText(R.string.title_unlock_private_key)).checkWillBeDisplayed()
            onView(withHint(R.string.hint_key_password)).doTypeText(ENCRYPTED_KEY_PASSWORD)
            onView(withText(android.R.string.ok)).doClick()

            vncSession.assertConnected()
            stop()
        }
    }

    @Test
    fun knownHost() {
        SshTunnelScenario().apply {
            setupAuthWithPassword(USER, PASSWORD)
            start()
            checkAndTrustHostFingerprint()
            vncSession.assertConnected()
            stop()
        }

        SshTunnelScenario().apply {
            setupAuthWithPassword(USER, PASSWORD)
            start()
            // Unknown hosts dialog should not be triggered now
            vncSession.assertConnected()
            stop()
        }
    }
}