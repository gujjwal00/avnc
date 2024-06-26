/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel.service

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.core.content.ContextCompat
import com.gaurav.avnc.pollingAssert
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.targetContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class DiscoveryTest {
    private val TAG = "DiscoveryTest"
    private var nsdManager: NsdManager? = null
    private var listeners = mutableListOf<NsdManager.RegistrationListener>()
    private var addedServices = mutableListOf<String>()

    /**
     * If advertisement fails, this method will cause the calling test to be skipped.
     * Advertisement can fail if there is no suitable network on the device
     * (e.g. in Airplane mode on newer Android versions).
     */
    private fun advertiseService(advertisedName: String, advertisedPort: Int) {
        var registeredService: String? = null
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "Registration failed: si: $serviceInfo, error:$errorCode")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(TAG, "UnRegistration failed: si: $serviceInfo, error:$errorCode")
            }

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Registered si: $serviceInfo")
                registeredService = serviceInfo.serviceName
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "Unregistered si: $serviceInfo")
            }
        }
        val si = NsdServiceInfo().apply {
            serviceType = "_rfb._tcp"
            serviceName = advertisedName
            port = advertisedPort
        }
        nsdManager?.registerService(si, NsdManager.PROTOCOL_DNS_SD, listener)
        listeners.add(listener)

        Assume.assumeTrue(runCatching { pollingAssert { assertTrue(registeredService == advertisedName) } }.isSuccess)
        addedServices.add(advertisedName)
    }

    private fun assertDiscoveryState(test: Discovery.() -> Boolean) {
        pollingAssert { runOnMainSync { assertTrue(Discovery.test()) } }
    }

    @Before
    fun before() {
        nsdManager = ContextCompat.getSystemService(targetContext, NsdManager::class.java)
        addedServices.clear()
    }

    @After
    fun after() {
        assertDiscoveryState { Log.d(TAG, "After unregister: isDiscoveryRunning: ${isRunning.value}, count:${servers.value?.size ?: 0}}, list: ${servers.value?.joinToString("\n")}"); true }
        listeners.forEach { nsdManager?.unregisterService(it) }
        listeners.clear()
        assertDiscoveryState { servers.value!!.none { it.name in addedServices } }
        Discovery.stop()
        assertDiscoveryState { isRunning.value == false }
    }

    @Test
    fun startStop() {
        assertDiscoveryState { isRunning.value == false }
        Discovery.start(targetContext)
        assertDiscoveryState { isRunning.value == true }
        Discovery.stop()
        assertDiscoveryState { isRunning.value == false }
    }

    @Test
    fun singleService() {
        Discovery.start(targetContext)
        advertiseService("Server 1", 5999)
        assertDiscoveryState {
            isRunning.value == true && servers.value!!.find { it.port == 5999 } != null
        }
    }

    @Test
    fun multipleServices() {
        Discovery.start(targetContext)

        val count = 10
        for (i in 1..count)
            advertiseService("Server $i", 5900 + i)

        assertDiscoveryState {
            isRunning.value == true && servers.value!!.size >= count &&  /*Discovery can find more than we advertise here*/
            servers.value!!.find { it.port == 5901 } != null &&
            servers.value!!.find { it.port == 5904 } != null &&
            servers.value!!.find { it.port == 5908 } != null
        }
    }
}