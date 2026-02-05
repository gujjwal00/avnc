/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.view.View
import androidx.core.content.edit
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.DisableAnimationsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.SshTunnelScenario
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.checkDoesNotExist
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.inDialog
import com.gaurav.avnc.inPopup
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.util.forgetKnownHosts
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.Matchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val SAMPLE_USERNAME = "Chandler"
private const val SAMPLE_PASSWORD = "Bing"

@SdkSuppress(minSdkVersion = 30) // due to DisableAnimationsRule
class LoginFragmentTest : VncSessionTest() {

    @Rule
    @JvmField
    val cleanPref = CleanPrefsRule()

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Rule
    @JvmField
    val noAnim = DisableAnimationsRule()

    @Test
    fun vncPasswordLogin() {
        vncSession.apply {
            server.setupVncAuth(SAMPLE_PASSWORD)
            startServer()
            startActivity()

            onView(withId(R.id.password)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_PASSWORD)
            onView(withId(R.id.username)).inDialog().checkIsNotDisplayed()
            onView(withId(R.id.remember)).inDialog().checkIsNotDisplayed()
            onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

            assertConnected()
            stop()
        }
    }

    @Test
    fun vncUsernameAndPasswordLogin() {
        vncSession.apply {
            server.setupDHAuth(SAMPLE_USERNAME, SAMPLE_PASSWORD)
            startServer()
            startActivity()

            onView(withId(R.id.username)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_USERNAME)
            onView(withId(R.id.password)).inDialog().checkIsDisplayed().doTypeText(SAMPLE_PASSWORD)
            onView(withId(R.id.remember)).inDialog().checkIsNotDisplayed()
            onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

            assertConnected()
            stop()
        }
    }

    @Test
    fun rememberLoginDetails() {
        vncSession.apply {
            saveProfileToDB(dbRule.db)
            server.setupDHAuth(SAMPLE_USERNAME, SAMPLE_PASSWORD)
            startServer()
            startActivity()

            onView(withId(R.id.username)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_USERNAME)
            onView(withId(R.id.password)).inDialog().checkIsDisplayed().doTypeText(SAMPLE_PASSWORD)
            onView(withId(R.id.remember)).inDialog().checkIsDisplayed().doClick()
            onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

            assertConnected()
            pollingAssert {
                val saved = runBlocking { dbRule.db.serverProfileDao.getByID(profile.ID) }
                assertEquals(SAMPLE_USERNAME, saved?.username)
                assertEquals(SAMPLE_PASSWORD, saved?.password)
            }
            stop()
        }
    }

    @Test
    fun autocomplete() {
        runBlocking {
            dbRule.db.serverProfileDao.save(listOf(
                    ServerProfile(name = "N1", host = "H1", username = SAMPLE_USERNAME, password = "P1"),
                    ServerProfile(name = "N2", host = "H2", username = "U2", password = SAMPLE_PASSWORD),
            ))
        }
        vncSession.apply {
            server.setupDHAuth(SAMPLE_USERNAME, SAMPLE_PASSWORD)
            startServer()
            startActivity()

            onView(withText(R.string.title_vnc_login)).inDialog().checkWillBeDisplayed()

            // Select username
            onView(withId(R.id.username_layout)).inDialog().checkEndIconModeIs(TextInputLayout.END_ICON_DROPDOWN_MENU)
            onView(withId(R.id.username)).inDialog().checkIsDisplayed().doClick()
            Espresso.closeSoftKeyboard()
            onView(withSubstring("P1")).inPopup().checkDoesNotExist()           // No passwords
            onView(withSubstring(SAMPLE_PASSWORD)).inPopup().checkDoesNotExist()
            onView(withText("U2")).inPopup().checkWillBeDisplayed()
            onView(withText(SAMPLE_USERNAME)).inPopup().checkWillBeDisplayed().doClick()

            // Select password
            onView(withId(R.id.password_layout)).inDialog().checkEndIconModeIs(TextInputLayout.END_ICON_DROPDOWN_MENU)
            onView(withId(R.id.password)).inDialog().checkIsDisplayed().doClick()
            Espresso.closeSoftKeyboard()
            // For passwords, profile name and host name should be displayed, not the actual password
            onView(withSubstring("P1")).inPopup().checkDoesNotExist()
            onView(withSubstring(SAMPLE_PASSWORD)).inPopup().checkDoesNotExist()
            onView(withSubstring("N1")).inPopup().checkWillBeDisplayed()
            onView(withSubstring("H2")).inPopup().checkWillBeDisplayed().doClick()

            onView(withText(android.R.string.ok)).doClick()
            assertConnected()
            stop()
        }
    }

    @Test
    fun autocompleteSshPassword() {
        runBlocking {
            dbRule.db.serverProfileDao.save(listOf(
                    ServerProfile(name = "N1", sshHost = "SH1", sshPassword = "SP1"),
                    ServerProfile(name = "N2", sshHost = "SH2", sshPassword = SAMPLE_PASSWORD),
            ))
        }
        SshTunnelScenario().apply {
            setupAuthWithPassword(SAMPLE_USERNAME, SAMPLE_PASSWORD)
            profile.sshPassword = ""
            forgetKnownHosts(targetContext)
            start()
            checkAndTrustHostFingerprint()

            onView(withText(R.string.title_ssh_login)).inDialog().checkWillBeDisplayed()

            onView(withId(R.id.password_layout)).inDialog().checkEndIconModeIs(TextInputLayout.END_ICON_DROPDOWN_MENU)
            onView(withId(R.id.password)).inDialog().checkIsDisplayed().doClick()
            Espresso.closeSoftKeyboard()

            // For passwords, profile name and host name should be displayed, not the actual password
            onView(withSubstring("SP1")).inPopup().checkDoesNotExist()
            onView(withSubstring(SAMPLE_PASSWORD)).inPopup().checkDoesNotExist()
            onView(withSubstring("N1")).inPopup().checkWillBeDisplayed()
            onView(withSubstring("SH2")).inPopup().checkWillBeDisplayed().doClick()

            onView(withText(android.R.string.ok)).doClick()
            vncSession.assertConnected()
            stop()
        }
    }

    @Test
    fun disableAutoCompleteIfServersAreLocked() {
        runBlocking {
            dbRule.db.serverProfileDao.save(listOf(
                    ServerProfile(name = "N1", host = "H1", username = "U1", password = "P1"),
                    ServerProfile(name = "N2", host = "H2", username = "U2", password = "P2"),
            ))
        }
        targetPrefs.edit { putBoolean("lock_saved_server", true) }
        vncSession.apply {
            server.setupVncAuth(SAMPLE_PASSWORD)
            startServer()
            startActivity()

            onView(withText(R.string.title_vnc_login)).inDialog().checkWillBeDisplayed()
            onView(withId(R.id.password_layout)).inDialog().checkEndIconModeIs(TextInputLayout.END_ICON_NONE)

            stop()
        }
    }

    /**
     * Matcher for end icon mode of [TextInputLayout]
     */
    private class HasEndIconMode(endIconMode: Int)
        : BoundedDiagnosingMatcher<View, TextInputLayout>(TextInputLayout::class.java) {
        private val matcher = Matchers.`is`(endIconMode)

        override fun matchesSafely(item: TextInputLayout, mismatchDescription: Description): Boolean {
            if (!matcher.matches(item.endIconMode)) {
                mismatchDescription.appendText("TextInputLayout.endIconMode ")
                matcher.describeMismatch(item.endIconMode, mismatchDescription)
                return false
            }
            return true
        }

        override fun describeMoreTo(description: Description?) {
            description?.appendText("TextInputLayout.endIconMode ")
            matcher.describeTo(description)
        }
    }

    private fun ViewInteraction.checkEndIconModeIs(@TextInputLayout.EndIconMode endIconMode: Int) = check(matches(HasEndIconMode(endIconMode)))
}
