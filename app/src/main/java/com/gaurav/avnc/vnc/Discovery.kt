/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.gaurav.avnc.model.VncProfile
import kotlinx.coroutines.*

/**
 * Discovers VNC servers available on local network.
 */
class Discovery(private val context: Context) {

    private object Config {
        const val SERVICE_TYPE = "_rfb._tcp"
        const val TIMEOUT = 10000L   // 10 sec
    }

    /**
     * List of found servers.
     * This will be automatically updated as servers are found/lost.
     */
    val servers = MutableLiveData(ArrayList<VncProfile>())

    /**
     * Status of discovery.
     */
    val isRunning = MutableLiveData(false)


    private var nsdManager: NsdManager? = null
    private val listener = DiscoveryListener()

    /**
     * Starts discovery.
     *
     * Must be called from main thread. It will return immediately if discovery
     * is already running. Once started, discovery will be automatically stopped
     * after [Config.TIMEOUT].
     *
     * Status changes:
     *
     *        [start] : isRunning = true
     *           |
     *           +-----------------------------> [start failed] : isRunning = false
     *           |
     *           V
     *       [started]
     *           |
     *           | (timeout)
     *           V
     *        [stop]
     *           |
     *           +-----------------------------> [stop failed]
     *           |
     *           V
     *       [stopped] : isRunning = false
     *
     *
     * Because [NsdManager] starts/stops service discovery asynchronously,
     * we set [isRunning] to true 'optimistically' in [start] without waiting
     * for confirmation in [listener].
     * This way we don't have to track intermediate states.
     */
    fun start(scope: CoroutineScope) {
        if (isRunning.value == true) {
            return
        }

        isRunning.value = true
        servers.value = ArrayList() //Forget known servers

        //Construction NSD manager is done on a background thread because it appears to be quite heavy.
        scope.launch(Dispatchers.Default) {
            if (nsdManager == null)
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

            nsdManager?.discoverServices(Config.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }

        scope.launch(Dispatchers.Main) {
            try {
                delay(Config.TIMEOUT)
            } finally {
                if (isRunning.value == true) {
                    nsdManager?.stopServiceDiscovery(listener)
                }
            }
        }
    }

    /**
     * Adds a new profile with given details to list.
     */
    private fun addProfile(name: String, host: String, port: Int) {
        val profile = VncProfile().apply {
            this.displayName = name
            this.host = host
            this.port = port
        }

        runBlocking(Dispatchers.Main) {
            val currentList = servers.value!!

            if (!currentList.contains(profile)) {
                val newList = ArrayList(currentList)
                newList.add(profile)
                servers.value = newList
            }
        }
    }

    /**
     * Remove given profile from list.
     */
    private fun removeProfile(name: String) {
        runBlocking(Dispatchers.Main) {
            val newList = ArrayList(servers.value!!)
            val profiles = newList.filter { it.displayName == name }
            newList.removeAll(profiles)
            servers.value = newList
        }
    }

    /**
     * Listener for discovery process.
     */
    private inner class DiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            nsdManager?.resolveService(serviceInfo, ResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            removeProfile(serviceInfo.serviceName)
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.w(javaClass.simpleName, "Service discovery failed to stop [E: $errorCode ]")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(javaClass.simpleName, "Service discovery failed to start [E: $errorCode ]")

            runBlocking(Dispatchers.Main) {
                isRunning.value = false //Go Back
            }
        }

        override fun onDiscoveryStarted(serviceType: String?) {}

        override fun onDiscoveryStopped(serviceType: String?) {
            runBlocking(Dispatchers.Main) {
                isRunning.value = false
            }
        }
    }

    /**
     * Listener for service resolution result.
     */
    private inner class ResolveListener : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            Log.d(javaClass.simpleName, "Resolved service: ${serviceInfo.serviceName}")
            addProfile(serviceInfo.serviceName, serviceInfo.host.hostAddress, serviceInfo.port)
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w(javaClass.simpleName, "Service resolution failed for '${serviceInfo}'")
        }
    }
}