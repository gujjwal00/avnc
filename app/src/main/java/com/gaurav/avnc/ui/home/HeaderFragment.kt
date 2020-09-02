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
import com.gaurav.avnc.databinding.FragmentHeaderBinding
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.gaurav.avnc.vnc.VncUri

/**
 *
 */
class HeaderFragment : Fragment() {
    private lateinit var binding: FragmentHeaderBinding
    private val viewModel by activityViewModels<HomeViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHeaderBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        //Setup listener for Enter key
        binding.urlBox.setOnEditorActionListener { _, _, _ ->
            startVncActivity(viewModel.serverUrl.value!!)
            true
        }
        return binding.root
    }

    /**
     * Starts VNC activity for given uri
     */
    private fun startVncActivity(uri: String) {
        if (uri.isBlank())
            return

        val profile = VncProfile(VncUri(uri))
        viewModel.startConnection(profile)
    }
}