/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * For now, only purpose of this activity is to have a dark theme.
 * It avoids a white flash during app start.
 *
 * In future this can show a real splash screen or maybe show a welcome screen
 * on first app start.
 */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}