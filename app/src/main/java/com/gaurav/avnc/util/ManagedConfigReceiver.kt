/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.Keep
import com.gaurav.avnc.App

/**
 * Re-applies EMM restrictions when they are changed by the device policy controller.
 */
@Keep
class ManagedConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
            return
        val app = context.applicationContext as? App ?: return
        app.managedConfig.applyRestrictions()
    }
}

