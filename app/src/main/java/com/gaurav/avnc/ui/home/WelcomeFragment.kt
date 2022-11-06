/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.gaurav.avnc.databinding.FragmentWelcomeBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Simple fragment to show welcome message after V2 update
 */
class WelcomeFragment : BottomSheetDialogFragment() {

    private val GITHUB_RELEASE_URL = "https://github.com/gujjwal00/avnc/releases"
    private val PLAY_APP_URL = "market://details?id=com.gaurav.avnc"
    private val PLAY_WEB_URL = "https://play.google.com/store/apps/details?id=com.gaurav.avnc"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        binding.changelogBtn.setOnClickListener { openChangelog() }
        binding.rateBtn.setOnClickListener { openAppStore() }
        binding.closeBtn.setOnClickListener { dismiss() }
        with(requireDialog() as BottomSheetDialog) {
            setCanceledOnTouchOutside(false)
            behavior.apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
                isFitToContents = true
            }
        }
        return binding.root
    }

    private fun openChangelog() {
        runCatching { startActivity(Intent.parseUri(GITHUB_RELEASE_URL, 0)) }.onFailure { warnMissingBrowser() }
    }

    private fun openAppStore() {
        runCatching { startActivity(Intent.parseUri(PLAY_APP_URL, 0)) }
                .onFailure {
                    runCatching { startActivity(Intent.parseUri(PLAY_WEB_URL, 0)) }
                            .onFailure { warnMissingBrowser() }
                }
    }

    private fun warnMissingBrowser() {
        Toast.makeText(requireContext(), "Web Browser is missing", Toast.LENGTH_SHORT).show()
    }
}