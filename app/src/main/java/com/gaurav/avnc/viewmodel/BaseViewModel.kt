/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.util.AppPreferences
import kotlinx.coroutines.*

/**
 * Base view model.
 */
open class BaseViewModel(val app: Application) : AndroidViewModel(app) {

    protected val db by lazy { MainDb.getInstance(app) }

    protected val serverProfileDao by lazy { db.serverProfileDao }

    protected val clipboard by lazy { app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val pref by lazy { AppPreferences(app) }


    /**
     * Puts given text on the clipboard.
     */
    fun setClipboardText(text: String) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        } catch (t: Throwable) {
            Log.e(javaClass.simpleName, "Could not copy text to clipboard.", t)
        }
    }


    /**
     * Returns current clipboard text.
     */
    fun getClipboardText(): String? {
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0)
            return null

        val text = clip.getItemAt(0).text
        if (text == null || text.isBlank())
            return null

        return text.toString()
    }

    /**
     * Launches a new coroutine using [viewModelScope], and executes [block] in that coroutine.
     */
    protected fun async(dispatcher: CoroutineDispatcher, block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(dispatcher) { this.block() }
    }

    protected fun asyncMain(block: suspend CoroutineScope.() -> Unit) = async(Dispatchers.Main, block)
    protected fun asyncIO(block: suspend CoroutineScope.() -> Unit) = async(Dispatchers.IO, block)
}