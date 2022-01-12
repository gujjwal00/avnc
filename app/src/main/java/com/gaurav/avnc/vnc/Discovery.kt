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
import com.gaurav.avnc.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Discovers VNC servers advertising themselves on the network.
 */
class Discovery(private val context: Context) {

    /**
     * List of servers found by Discovery.
     */
    val servers = MutableLiveData(ArrayList<ServerProfile>())

    /**
     * Status of discovery.
     */
    val isRunning = MutableLiveData(false)


    private val service = "_rfb._tcp"
    private var nsdManager: NsdManager? = null
    private val listener = DiscoveryListener()

    /**
     * Starts discovery.
     * Must be called on main thread.
     *
     * [NsdManager] starts/stops service discovery asynchronously, and notifies
     * us through callbacks in [listener]. Also, it does not allow us to request
     * start/stop if a previous start/stop request is yet to complete.
     *
     * So, we set [isRunning] to true 'optimistically' in [start] without waiting
     * for the confirmation in [listener], and revert it if starting fails.
     * This way we don't have to track previously issued start/stop requests.
     *
     * Status change:
     *-
     *-        [start]   :isRunning = true
     *-           |
     *-           +-----------------------------> start failed   :isRunning = false
     *-           |
     *-           V
     *-        started
     *-           |
     *-           |
     *-           V
     *-        [stop]
     *-           |
     *-           +-----------------------------> stop failed
     *-           |
     *-           V
     *-        stopped   :isRunning = false
     *-
     */
    fun start() {
        if (isRunning.value == true) {
            return
        }

        isRunning.value = true
        servers.value = ArrayList() //Forget known servers

        // Construction of NSD manager is done on a background thread because it appears to be quite heavy.
        GlobalScope.launch(Dispatchers.Default) {
            if (nsdManager == null)
                nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

            nsdManager?.discoverServices(service, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    /**
     * Stops discovery.
     * Must be called on main thread.
     */
    fun stop() {
        if (isRunning.value == true) {
            nsdManager?.stopServiceDiscovery(listener)
        }
    }

    /**
     * Adds a new profile with given details to list.
     */
    private fun addProfile(name: String, host: String, port: Int) {
        val profile = ServerProfile(
                ID = (name + host + port).hashCode().toLong(),
                name = name,
                host = host,
                port = port
        )

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
            val profiles = newList.filter { it.name == name }
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
            addProfile(serviceInfo.serviceName, serviceInfo.host.hostAddress, serviceInfo.port)
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w(javaClass.simpleName, "Service resolution failed for '${serviceInfo}' [E: $errorCode]")
        }
    }
}