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
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gaurav.avnc.databinding.FragmentRecentsBinding
import com.gaurav.avnc.ui.home.adapter.RecentsAdapter
import com.gaurav.avnc.viewmodel.HomeViewModel

class RecentsFragment : Fragment() {
    val viewModel by activityViewModels<HomeViewModel>()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentRecentsBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel


        val adapter = RecentsAdapter(viewModel)
        binding.recentsRv.layoutManager = LinearLayoutManager(context)
        binding.recentsRv.adapter = adapter
        binding.recentsRv.setHasFixedSize(true)


        viewModel.recents.observe(viewLifecycleOwner, Observer { adapter.submitList(it) })

        return binding.root
    }
}