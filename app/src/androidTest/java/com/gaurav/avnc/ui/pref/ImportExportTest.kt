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
import com.gaurav.avnc.R
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.setupFileOpenIntent
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.util.DeviceAuthPrompt
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
    fun exportWithoutSecrets() {
        // This test requires an unlocked device
        activityRule.scenario.onActivity { Assume.assumeFalse(DeviceAuthPrompt(it).canLaunch()) }

        // Insert sample data
        val sampleName = "Days of our Lives"
        val sampleSecret = "Drake Ramoray"
        val profile = ServerProfile(name = sampleName, password = sampleSecret, sshPassword = sampleSecret, sshPrivateKey = sampleSecret)
        runBlocking { MainDb.getInstance(targetContext).serverProfileDao.save(profile) }

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
        val data = file.bufferedReader().use { it.readText() }
        Assert.assertTrue("Exported data: `$data` should contain `$sampleName`", data.contains(sampleName))
        Assert.assertFalse("Exported data: `$data` should not contain `$sampleSecret`", data.contains(sampleSecret))
    }


    @Test
    fun exportWithSecrets() {
        // This test requires an unlocked device
        activityRule.scenario.onActivity { Assume.assumeFalse(DeviceAuthPrompt(it).canLaunch()) }

        // Insert sample data
        val sampleName = "Days of our Lives"
        val sampleSecret = "Drake Ramoray"
        val profile = ServerProfile(name = sampleName, password = sampleSecret, sshPassword = sampleSecret, sshPrivateKey = sampleSecret)
        runBlocking { MainDb.getInstance(targetContext).serverProfileDao.save(profile) }

        // Setup export file
        val file = File.createTempFile("avnc", "test")
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_CREATE_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(file.toUri())))

        // Export
        onView(withText(R.string.title_export_passwords_and_keys)).doClick() // Check
        onView(withText(R.string.title_export)).doClick()
        onView(withText(R.string.msg_exported)).checkWillBeDisplayed()
        onView(withSubstring("Error")).checkIsNotDisplayed()

        // Verify exported data
        instrumentation.waitForIdleSync()
        val data = file.bufferedReader().use { it.readText() }
        Assert.assertTrue("Exported data: `$data` should contain `$sampleName`", data.contains(sampleName))
        Assert.assertTrue("Exported data: `$data` should contain `$sampleSecret`", data.contains(sampleSecret))
    }


    @Test
    fun importTest() {
        val sampleName = "Joey Tribbiani"
        val sampleJson = """{ "profiles": [{ "name": "$sampleName" }]}"""
        setupFileOpenIntent(sampleJson)

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