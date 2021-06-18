/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gaurav.avnc.BuildConfig
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentAboutBinding.inflate(inflater, container, false)

        binding.apply {
            version.text = BuildConfig.VERSION_NAME
            repoBtn.setOnClickListener { openUrl(AboutActivity.GIT_REPO_URL) }
            librariesBtn.setOnClickListener { showFragment(LibrariesFragment()) }
            licenceBtn.setOnClickListener { showFragment(LicenseFragment()) }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.title_about)
    }

    private fun openUrl(url: String) {
        if (url.isNotEmpty()) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    private fun showFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .commit()
    }
}