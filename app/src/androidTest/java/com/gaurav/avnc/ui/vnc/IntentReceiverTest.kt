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
import android.view.View
import android.view.WindowManager.LayoutParams.TYPE_TOAST
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import kotlinx.coroutines.runBlocking
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test


/**
 * Tests connection launch from external intents (e.g. via vnc:// URIs)
 */
class IntentReceiverTest {

    @Rule
    @JvmField
    val dbRule = DatabaseRule()

    /**
     * Matching toast doesn't work on API 30+
     * So all tests which checks toasts will only work on lower APIs.
     */
    private fun onToast(matcher: Matcher<View>): ViewInteraction {
        class ToastRootMatcher : TypeSafeMatcher<Root>() {
            override fun describeTo(description: Description) {
                description.appendText("is toast")
            }

            @Suppress("DEPRECATION")
            override fun matchesSafely(root: Root) = root.windowLayoutParams.get().type == TYPE_TOAST
        }

        return onView(matcher).noActivity().inRoot(ToastRootMatcher())
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
        ActivityScenario.launch<Activity>(newUriIntent("vnc://localhost:${server.port}"))
        onView(withId(R.id.frame_view)).checkWillBeDisplayed()
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
            dbRule.db.serverProfileDao.insert(ServerProfile(name = "Example", host = "localhost", port = server.port))
        }
        ActivityScenario.launch<Activity>(newUriIntent("vnc://?ConnectionName=Example"))
        onView(withId(R.id.frame_view)).checkWillBeDisplayed()
    }

    @Test
    @SdkSuppress(maxSdkVersion = 29)
    fun uriWithUnknownConnectionName() {
        ActivityScenario.launch<Activity>(newUriIntent("vnc://?ConnectionName=NoSuchServer"))
        onToast(withSubstring("No server found with name")).checkWillBeDisplayed()
    }
}
