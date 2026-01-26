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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.DisableAnimationsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.VncSessionTest
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.inDialog
import com.gaurav.avnc.pollingAssert
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val SAMPLE_USERNAME = "Chandler"
private const val SAMPLE_PASSWORD = "Bing"

@SdkSuppress(minSdkVersion = 30) // due to DisableAnimationsRule
class LoginFragmentTest : VncSessionTest() {

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
}
