/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.app.Application
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.util.ManagedConfigManager

class App : Application() {

    @Keep
    lateinit var prefs: AppPreferences

    @Keep
    lateinit var managedConfig: ManagedConfigManager

    override fun onCreate() {
        super.onCreate()
        configureLeakCanary()

        prefs = AppPreferences(this)
        managedConfig = ManagedConfigManager(this)
        prefs.ui.theme.observeForever { updateNightMode(it) }
    }

    private fun updateNightMode(theme: String) {
        val nightMode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun configureLeakCanary() {
        if (BuildConfig.DEBUG) {
            Class.forName("com.gaurav.avnc.LeakCanaryInitializer")
                    .getMethod("initialize", Application::class.java)
                    .invoke(null, this)
        }
    }
}