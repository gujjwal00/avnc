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
import com.gaurav.avnc.databinding.FragmentServersBinding
import com.gaurav.avnc.ui.home.adapter.ServersAdapter
import com.gaurav.avnc.util.Experimental
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Fragment for displaying list of known servers.
 */
class ServersFragment : Fragment() {

    val viewModel by activityViewModels<HomeViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentServersBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val adapter = ServersAdapter(viewModel)
        binding.serversRv.layoutManager = LinearLayoutManager(context)
        binding.serversRv.adapter = adapter
        binding.serversRv.setHasFixedSize(true)

        viewModel.serverProfiles.observe(viewLifecycleOwner) { adapter.submitList(it) }

        if (viewModel.pref.experimental.indicator) {
            Experimental.Indicator().setup(binding, adapter)
        }

        return binding.root
    }
}