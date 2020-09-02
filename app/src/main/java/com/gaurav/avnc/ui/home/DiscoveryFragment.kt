/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gaurav.avnc.databinding.FragmentDiscoveryBinding
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.ui.home.adapter.DiscoveryAdapter
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Fragment for detecting and displaying VNC servers advertising themselves on current network.
 *
 * TODO: Cleanup service discovery
 */
class DiscoveryFragment : Fragment() {

    private object DiscoveryConfig {
        const val SERVICE_TYPE = "_rfb._tcp"
        const val TIMEOUT = 10000L   // 10 sec
    }

    val viewModel by activityViewModels<HomeViewModel>()

    private val nsdManager by lazy { requireContext().getSystemService(Context.NSD_SERVICE) as NsdManager }

    private val discoveryListener = DiscoveryListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentDiscoveryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.discoveredRv.layoutManager = LinearLayoutManager(requireContext())
        binding.discoveredRv.adapter = discoveryAdapter
        binding.discoveredRv.setHasFixedSize(true)

        binding.discoverFab.setOnClickListener { startDiscovery() }

        viewModel.isDiscoveringServers.observe(viewLifecycleOwner, Observer {
            if (it == true)
                binding.root.handler.postDelayed({ stopDiscovery() }, DiscoveryConfig.TIMEOUT)
        })

        return binding.root
    }


    override fun onStart() {
        super.onStart()
        startDiscovery()
    }

    override fun onStop() {
        super.onStop()
        stopDiscovery()
    }

    private fun startDiscovery() {
        if (viewModel.isDiscoveringServers.value == false) {
            nsdManager.discoverServices(
                    DiscoveryConfig.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
            )
        }
    }

    private fun stopDiscovery() {
        if (viewModel.isDiscoveringServers.value == true)
            nsdManager.stopServiceDiscovery(discoveryListener)
    }

    /**
     * Creates a new profile using given service info.
     */
    private fun createProfile(info: NsdServiceInfo): VncProfile {
        return VncProfile().apply {
            displayName = info.serviceName
            host = info.host.hostAddress
            port = info.port
        }
    }

    /**
     * Adds given profile to list.
     */
    private fun addProfile(profile: VncProfile) {
        if (!viewModel.discoveredServers.contains(profile)) {
            viewModel.discoveredServers.add(profile)
            discoveryAdapter.notifyItemInserted(viewModel.discoveredServers.size - 1)
        }
    }

    /**
     * Remove given profile from list.
     */
    private fun removeProfile(profile: VncProfile) {
        val index = viewModel.discoveredServers.indexOf(profile)
        if (index >= 0) {
            viewModel.discoveredServers.removeAt(index)
            discoveryAdapter.notifyItemRemoved(index)
        }

    }

    /**
     * Listener for discovery process.
     */
    private inner class DiscoveryListener : NsdManager.DiscoveryListener {
        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            Log.d(javaClass.simpleName, "Found new service: ${serviceInfo?.serviceName}")
            nsdManager.resolveService(serviceInfo, ResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            activity?.runOnUiThread { removeProfile(createProfile(serviceInfo!!)) }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.w(javaClass.simpleName, "Service discovery failed to stop [E: $errorCode ]")
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(javaClass.simpleName, "Service discovery failed to start [E: $errorCode ]")
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            viewModel.isDiscoveringServers.postValue(true)
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            viewModel.isDiscoveringServers.postValue(false)
        }
    }

    /**
     * Listener for service resolution result.
     */
    private inner class ResolveListener : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
            Log.d(javaClass.simpleName, "Resolved service: ${serviceInfo?.serviceName}")
            activity?.runOnUiThread { addProfile(createProfile(serviceInfo!!)) }
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.w(javaClass.simpleName, "Service resolution failed for '${serviceInfo}'")
        }
    }
}