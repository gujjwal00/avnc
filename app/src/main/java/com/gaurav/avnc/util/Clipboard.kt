/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clipboard access is slightly more complex in AVNC, because clip data can be
 * unusually large. It includes stuff coming from server, and app logs.
 * As clipboard access involves binder IPC, it can lead to ANR issues. So we
 * use a background thread for accessing clipboard.
 */


/**
 * Should be called from Main thread because ClipboardManager can only be created
 * from a Looper thread.
 */
fun getClipboard(context: Context): ClipboardManager {
    // Use application context to create ClipboardManager to
    // - avoid leaking the context
    // - have single instance
    return ContextCompat.getSystemService(context.applicationContext, ClipboardManager::class.java)!!
}

/**
 * Puts given text on the clipboard.
 */
suspend fun setClipboardText(context: Context, text: String): Boolean {
    var success = false
    try {
        withContext(Dispatchers.Main.immediate) { getClipboard(context) }.let {
            withContext(Dispatchers.IO) {
                it.setPrimaryClip(ClipData.newPlainText(null, text))
                success = true
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.e("ClipboardUtil", "Could not copy text to clipboard.", t)
    }
    return success
}


/**
 * Returns current clipboard text.
 */
suspend fun getClipboardText(context: Context): String? {
    var result: String? = null
    try {
        withContext(Dispatchers.Main.immediate) { getClipboard(context) }.let {
            withContext(Dispatchers.IO) {
                result = it.primaryClip?.getItemAt(0)?.text?.toString()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.e("ClipboardUtil", "Could not retrieve text from clipboard.", t)
    }
    return result
}

/**
 * Helper utility to set clip text & show a snack bar with confirmation
 */
fun AppCompatActivity.setClipboardTextWithNotification(text: String) {
    lifecycleScope.launch {
        val snackHost = findViewById<View>(android.R.id.content)
        if (setClipboardText(this@setClipboardTextWithNotification, text))
            Snackbar.make(snackHost, R.string.msg_copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
        else
            Snackbar.make(snackHost, "Unable to copy text", Snackbar.LENGTH_SHORT).show()
    }
}