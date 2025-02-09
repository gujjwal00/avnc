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
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.onToast
import com.gaurav.avnc.targetContext
import kotlinx.coroutines.runBlocking
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
            assertEquals(Lifecycle.State.DESTROYED, it.state)
        }
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun invalidVncUri() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://:5900"))
        onToast(withText(R.string.msg_invalid_vnc_uri)).checkWillBeDisplayed()
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
        onToast(withSubstring("No server found with name")).checkWillBeDisplayed()
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
        onToast(withText(R.string.msg_shortcut_server_deleted)).checkWillBeDisplayed()
    }
}
