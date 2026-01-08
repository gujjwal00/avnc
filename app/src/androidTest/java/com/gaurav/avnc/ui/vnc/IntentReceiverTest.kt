/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.onToast
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.targetContext
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test


/**
 * Tests connection launch from external intents (e.g. via vnc:// URIs)
 */
class IntentReceiverTest {

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    // Once a toast is matched we need to wait for it to be closed so that
    // the the toast from the next test is displayed and matched properly
    class WaitForViewDetach : ViewAction {
        override fun getDescription() = "waiting for the view to detach from window"
        override fun getConstraints() = isDisplayed()!!
        override fun perform(uiController: UiController, view: View) {
            val timeout = SystemClock.uptimeMillis() + 5000
            do {
                if (!view.isAttachedToWindow)
                    return
                uiController.loopMainThreadForAtLeast(100)
            } while (SystemClock.uptimeMillis() < timeout)

            throw Exception("Timeout expired waiting for the view to detach from window")
        }
    }

    private fun assertToastIsShown(matcher: Matcher<View>) {
        pollingAssert { onToast(matcher).checkIsDisplayed().perform(WaitForViewDetach()) }
    }

    /******************************** VNC URIs ***************************************/

    private fun newUriIntent(uri: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(targetContext.packageName)   // Set package name to avoid activity chooser
        }
    }

    @Test
    fun simpleVncUri() {
        val server = TestServer().apply { start() }
        ActivityScenario.launch<Activity>(newUriIntent("vnc://localhost:${server.port}")).use {
            onView(withId(R.id.frame_view)).checkWillBeDisplayed()
            pollingAssert { assertEquals(Lifecycle.State.DESTROYED, it.state) }
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun invalidVncUri() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://:5900"))
        assertToastIsShown(withText(R.string.msg_invalid_vnc_uri))
    }

    @Test
    fun uriForSavedConnection() {
        val server = TestServer().apply { start() }
        runBlocking {
            dbRule.db.serverProfileDao.save(ServerProfile(name = "Example", host = "localhost", port = server.port))
        }
        ActivityScenario.launch<Activity>(newUriIntent("vnc://?ConnectionName=Example")).use {
            onView(withId(R.id.frame_view)).checkWillBeDisplayed()
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun uriWithUnknownConnectionName() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://?ConnectionName=NoSuchServer"))
        assertToastIsShown(withSubstring("No server found with name"))
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun saveUri() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://localhost?ConnectionName=Test&SaveConnection=true"))
        assertToastIsShown(withSubstring("Added new server for URI"))

        val profiles = runBlocking { dbRule.db.serverProfileDao.getList() }
        assertEquals(1, profiles.size)
        assertEquals("Test", profiles.first().name)
        assertEquals("localhost", profiles.first().host)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun saveUriWithoutExplicitName() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://localhost/?SaveConnection=true"))
        assertToastIsShown(withSubstring("Added new server for URI"))

        val profiles = runBlocking { dbRule.db.serverProfileDao.getList() }
        assertEquals(1, profiles.size)
        assertEquals("vnc://localhost", profiles.first().name) // Name should be generated using host
        assertEquals("localhost", profiles.first().host)
    }

    @Test
    fun updateProfileWithUri() {
        runBlocking { dbRule.db.serverProfileDao.save(ServerProfile(name = "Test", host = "10.0.0.1", password = "Hello")) }
        ActivityScenario.launch<Activity>(newUriIntent("vnc://localhost?SaveConnection=true&ConnectionName=Test&VncPassword=World"))
                .use { }

        val profiles = runBlocking { dbRule.db.serverProfileDao.getList() }
        assertEquals(1, profiles.size)
        assertEquals("Test", profiles.first().name)
        assertEquals("localhost", profiles.first().host)
        assertEquals("World", profiles.first().password)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun tryToSaveInvalidUri() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://?SaveConnection=true&ConnectionName=Test"))
        assertToastIsShown(withSubstring("No server found with name"))

        val profiles = runBlocking { dbRule.db.serverProfileDao.getList() }
        assertEquals(0, profiles.size)
    }


    /******************************** Shortcuts **************************************/

    private fun newShortcutIntent(profile: ServerProfile) = IntentReceiverActivity.createShortcutIntent(targetContext, profile.ID)

    @Test
    fun simpleShortcut() {
        val server = TestServer().apply { start() }
        val profile = ServerProfile(host = "localhost", port = server.port)
        runBlocking { profile.ID = dbRule.db.serverProfileDao.save(profile) }

        ActivityScenario.launch<Activity>(newShortcutIntent(profile)).use {
            onView(withId(R.id.frame_view)).checkWillBeDisplayed()
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun invalidShortcut() {
        ActivityScenario.launch<Activity>(newShortcutIntent(ServerProfile(ID = 123456)))
        assertToastIsShown(withText(R.string.msg_shortcut_server_deleted))
    }
}
