/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */
package com.gaurav.avnc

import android.app.Application
import android.util.Log
import androidx.annotation.Keep
import androidx.lifecycle.Observer
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.viewmodel.VncViewModel.State
import leakcanary.AppWatcher
import leakcanary.ReachabilityWatcher

/**
 * Configures LeakCanary
 * Dynamically loaded by [App].
 */
@Keep
object LeakCanaryInitializer {

    @JvmStatic
    fun initialize(app: Application) {
        val reachabilityWatcher = ReachabilityWatcher { watchedObject, description ->
            if (!handleVncViewModelInstance(watchedObject, description))
                AppWatcher.objectWatcher.expectWeaklyReachable(watchedObject, description)
        }

        val watchers = AppWatcher.appDefaultWatchers(app, reachabilityWatcher)
        AppWatcher.manualInstall(app, watchersToInstall = watchers)
    }

    /**
     * [VncViewModel] instance can remain in memory for a long time if VNC/SSH client
     * is connecting to server because we have no way to cancel these operations.
     * These will be eventually fail after a timeout.
     *
     * So we defer watching [VncViewModel] instance if it is stuck in Connecting state.
     */
    private fun handleVncViewModelInstance(obj: Any, description: String): Boolean {
        if (obj !is VncViewModel || obj.state.value != State.Connecting)
            return false

        Log.d("VncViewModelWatcher", "VncViewModel is connecting, deferring leak detection")
        deferVncViewModelWatch(obj, description)
        return true
    }

    private fun deferVncViewModelWatch(vm: VncViewModel, description: String) {
        vm.state.observeForever(object : Observer<State> {
            override fun onChanged(value: State) {
                if (value != State.Connecting) {
                    Log.d("VncViewModelWatcher", "VncViewModel is no longer connecting, adding it to watched objects")
                    vm.state.removeObserver(this)
                    AppWatcher.objectWatcher.expectWeaklyReachable(vm, description)
                }
            }
        })
    }
}
