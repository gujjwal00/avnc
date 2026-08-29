/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context.RESTRICTIONS_SERVICE
import android.content.ContextWrapper
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManagedConfigTest {

    @Rule
    @JvmField
    val prefRule = CleanPrefsRule()

    private fun managerWith(bundle: Bundle): ManagedConfigManager {
        val restrictionsManager = mockk<RestrictionsManager>()
        every { restrictionsManager.applicationRestrictions } returns bundle
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getSystemService(name: String): Any? =
                    if (name == RESTRICTIONS_SERVICE) restrictionsManager else super.getSystemService(name)
        }
        return ManagedConfigManager(context)
    }

    @Test
    fun boolRestrictionOverridesUserPref() {
        targetPrefs.edit { putBoolean("bell_enabled", false) }
        managerWith(Bundle().apply { putBoolean("emm_bell", true) }).applyRestrictions()

        assertTrue(targetPrefs.getBoolean("bell_enabled", false))
    }

    @Test
    fun choiceRestrictionWritesValidValue() {
        managerWith(Bundle().apply { putString("emm_theme", "dark") }).applyRestrictions()
        assertEquals("dark", targetPrefs.getString("theme", null))
    }

    @Test
    fun choiceRestrictionIgnoresInvalidValue() {
        managerWith(Bundle().apply { putString("emm_theme", "spaceship") }).applyRestrictions()
        assertEquals("system", targetPrefs.getString("theme", "system"))
    }

    @Test
    fun intRestrictionIsClampedToRange() {
        managerWith(Bundle().apply { putInt("emm_zoom_min", 9999) }).applyRestrictions()
        assertEquals(100, targetPrefs.getInt("zoom_min", 0))
    }

    @Test
    fun managedKeyIsReported() {
        val mgr = managerWith(Bundle().apply { putBoolean("emm_pip", true) })
        mgr.applyRestrictions()
        assertTrue(mgr.isManaged("pip_enabled"))
    }

    @Test
    fun restoringRemovedRestrictionRecoversUserValue() {
        targetPrefs.edit { putBoolean("bell_enabled", false) }
        val mgr = managerWith(Bundle().apply { putBoolean("emm_bell", true) })
        mgr.applyRestrictions()
        assertTrue(targetPrefs.getBoolean("bell_enabled", false))

        // EMM removes the restriction
        mgr.applyRestrictions()
        assertFalse(targetPrefs.getBoolean("bell_enabled", true))
        assertFalse(mgr.isManaged("bell_enabled"))
    }
}
