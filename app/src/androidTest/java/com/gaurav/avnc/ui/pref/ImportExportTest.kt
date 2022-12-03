/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.pref

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.gaurav.avnc.*
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.util.DeviceAuthPrompt
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class ImportExportTest {
    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(PrefsActivity::class.java)

    @Before
    fun openImportExportScreen() {
        onView(withText(R.string.pref_tools)).doClick()
        onView(withText(R.string.pref_import_export)).doClick()
        onView(withText(R.string.title_import)).checkIsDisplayed()
        onView(withText(R.string.title_export)).checkIsDisplayed()
    }

    @Before
    fun initIntents() = Intents.init()

    @After
    fun releaseIntents() = Intents.release()


    @Test
    fun exportTest() {
        // This test requires an unlocked device
        activityRule.scenario.onActivity { Assume.assumeFalse(DeviceAuthPrompt(it).canLaunch()) }

        // Insert sample data
        val sampleName = "Days of our Lives"
        runBlocking { MainDb.getInstance(targetContext).serverProfileDao.insert(ServerProfile(name = sampleName)) }

        // Setup export file
        val file = File.createTempFile("avnc", "test")
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(file.toUri())))

        // Export
        onView(withText(R.string.title_export)).doClick()
        onView(withText(R.string.msg_exported)).checkWillBeDisplayed()
        onView(withSubstring("Error")).checkIsNotDisplayed()

        // Verify exported data
        instrumentation.waitForIdleSync()
        val data = file.bufferedReader().readText()
        Assert.assertTrue("Exported data: `$data` contains `$sampleName`", data.contains(sampleName))
    }


    @Test
    fun importTest() {
        val sampleName = "Joey Tribbiani"
        val sampleJson = """{ "profiles": [{ "name": "$sampleName" }]}"""

        // Setup import file
        val file = File.createTempFile("avnc", "test")
        file.bufferedWriter().use { it.write(sampleJson) }
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(file.toUri())))

        // Import
        onView(withText(R.string.title_delete_servers_before_import)).doClick()
        onView(withText(R.string.title_import)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
        onView(withSubstring("Error")).checkIsNotDisplayed()

        // Verify imported data
        instrumentation.waitForIdleSync()
        val profiles = runBlocking { MainDb.getInstance(targetContext).serverProfileDao.getList() }
        Assert.assertEquals(1, profiles.size)
        Assert.assertEquals(sampleName, profiles.first().name)
    }
}