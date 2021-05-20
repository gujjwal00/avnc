/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.gaurav.avnc.util.AppPreferences

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val nightMode = AppPreferences(this).ui.nightMode
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}