/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.gaurav.avnc.databinding.FragmentDiscoveryBinding
import com.gaurav.avnc.ui.home.adapter.DiscoveryAdapter
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Fragment for detecting and displaying VNC servers advertising themselves on current network.
 */
class DiscoveryFragment : Fragment() {

    val viewModel by activityViewModels<HomeViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentDiscoveryBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val adapter = DiscoveryAdapter(viewModel)
        binding.discoveredRv.layoutManager = LinearLayoutManager(requireContext())
        binding.discoveredRv.adapter = adapter
        binding.discoveredRv.setHasFixedSize(true)

        viewModel.discovery.servers.observe(viewLifecycleOwner) { adapter.submitList(it) }
        return binding.root
    }
}