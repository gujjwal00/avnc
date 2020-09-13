/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.util.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Base view model.
 */
open class BaseViewModel(app: Application) : AndroidViewModel(app) {

    /**
     * Database instance.
     */
    protected val db by lazy { MainDb.getInstance(app) }

    protected val bookmarkDao by lazy { db.bookmarkDao }

    protected val recentDao by lazy { db.recentDao }

    protected val clipboard by lazy { app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val pref by lazy { AppPreferences(app) }


    /**
     * Puts given text on clipboard.
     */
    fun toClipboard(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                clipboard.text = text
            } catch (t: Throwable) {
                Log.e(javaClass.simpleName, "Could not copy text to clipboard.", t)
            }
        }
    }

    /**
     * Executes given method asynchronously on IO thread
     */
    protected fun async(method: () -> Unit): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            method()
        }
    }

    /**
     * Executes given method asynchronously on IO thread
     *
     * Once given method has completed, 'afterMethod' will be executed
     * on main thread.
     */
    protected fun async(method: () -> Unit, afterMethod: () -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                method()
            }
            afterMethod()
        }
    }
}