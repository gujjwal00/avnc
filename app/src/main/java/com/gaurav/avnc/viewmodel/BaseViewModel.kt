/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.util.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Base view model.
 */
open class BaseViewModel(val app: Application) : AndroidViewModel(app) {

    protected val db by lazy { MainDb.getInstance(app) }

    protected val serverProfileDao by lazy { db.serverProfileDao }

    val pref by lazy { AppPreferences(app) }

    /**
     * Launches [block] in [viewModelScope], on Main dispatcher.
     */
    protected fun launchMain(block: suspend CoroutineScope.() -> Unit) = launch(EmptyCoroutineContext, block)

    /**
     * Launches [block] in [viewModelScope], with [Dispatchers.IO].
     */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit) = launch(Dispatchers.IO, block)

    private fun launch(context: CoroutineContext, block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelScope.launch(context, CoroutineStart.DEFAULT, block)
    }
}