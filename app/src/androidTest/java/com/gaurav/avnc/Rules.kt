/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.content.edit
import com.gaurav.avnc.model.db.MainDb
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * JUnit rule to clear database before running tests.
 * It also provides access to database instance through [db]
 */
class EmptyDatabaseRule : ExternalResource() {
    val db by lazy { MainDb.getInstance(targetContext) }

    override fun before() {
        runBlocking { db.serverProfileDao.deleteAll() }
    }
}

/**
 * JUnit rule to clear all preferences
 */
class CleanPrefsRule : ExternalResource() {
    override fun before() {
        targetPrefs.edit { clear() }
    }
}


/**
 * JUnit rule to disable animations
 */
class DisableAnimationsRule : ExternalResource() {
    private val animationPrefs = listOf(Settings.Global.WINDOW_ANIMATION_SCALE,
                                        Settings.Global.TRANSITION_ANIMATION_SCALE,
                                        Settings.Global.ANIMATOR_DURATION_SCALE)

    private val defaultValues = mutableMapOf<String, String>()

    private val enabledValue = "1.0"
    private val disabledValue = "0.0"

    override fun before() {
        check(Build.VERSION.SDK_INT >= 30) { "Disabling animations only works on newer versions" }
        animationPrefs.forEach {
            defaultValues[it] = getPref(it).ifBlank { enabledValue }
            setPref(it, disabledValue)
        }
    }

    override fun after() {
        animationPrefs.forEach {
            setPref(it, defaultValues[it] ?: enabledValue)
        }
    }

    private fun getPref(name: String) = runCmd("settings get global $name")
    private fun setPref(name: String, value: String) = runCmd("settings put global $name $value")

    private fun runCmd(cmd: String): String {
        return instrumentation.uiAutomation.executeShellCommand(cmd).use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                stream.reader().use { reader ->
                    reader.readText()
                }
            }
        }
    }

}