/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.base.DefaultFailureHandler
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasDataString
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.VncSessionScenario
import com.gaurav.avnc.doClick
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.ui.about.AboutActivity
import com.gaurav.avnc.ui.prefs.PrefsActivity
import org.hamcrest.Matchers.containsString
import org.hamcrest.core.AllOf.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicHomeTest {

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Before
    fun before() {
        Intents.init()
    }

    @After
    fun after() {
        Intents.assertNoUnverifiedIntents()
        Intents.release()
    }

    private fun openDrawer() = onView(withId(R.id.navigation_btn)).doClick()

    @Test
    fun openSettings_byUrlbarButton() {
        onView(withId(R.id.settings_btn)).doClick()
        Intents.intended(hasComponent(PrefsActivity::class.qualifiedName))
    }

    @Test
    fun openSettings_byDrawerItem() {
        openDrawer()
        onView(withId(R.id.settings)).doClick()
        Intents.intended(hasComponent(PrefsActivity::class.qualifiedName))
    }

    @Test
    fun openAbout() {
        openDrawer()
        onView(withId(R.id.about)).doClick()
        Intents.intended(hasComponent(AboutActivity::class.qualifiedName))
    }

    @Test
    fun openUrlbar() {
        onView(withId(R.id.urlbar)).doClick()
        Intents.intended(hasComponent(UrlBarActivity::class.qualifiedName))
    }

    @Test
    fun bugReport() {
        // Expecting the bug report url to be launched
        Intents.intending(hasAction(Intent.ACTION_VIEW))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

        openDrawer()
        onView(withId(R.id.report_bug)).doClick()

        Intents.intended(allOf(
                hasAction(Intent.ACTION_VIEW),
                hasDataString(containsString(AboutActivity.BUG_REPORT_URL))
        ))
        Intents.assertNoUnverifiedIntents()
    }
}

class AutoConnectTest {
    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Test
    fun autoConnectOnStartup() {
        Espresso.setFailureHandler(DefaultFailureHandler(targetContext, false))
        val vncSession = VncSessionScenario()
        vncSession.profile.fConnectOnAppStart = true
        vncSession.saveProfileToDB(dbRule.db)
                .suppressViewerHelp()
                .startServer()
        ActivityScenario.launch(HomeActivity::class.java).use {
            // Starting HomeActivity should automatically launch the connection
            vncSession.assertConnected()
            Espresso.pressBack()
        }
        vncSession.stop()
    }
}