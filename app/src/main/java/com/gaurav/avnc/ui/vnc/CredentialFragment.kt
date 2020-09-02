/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentCredentialBinding
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.UserCredential
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Allows user to enter credentials for remote server.
 */
class CredentialFragment : DialogFragment() {
    val viewModel by activityViewModels<VncViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentCredentialBinding.inflate(layoutInflater, null, false)

        binding.usernameRequired = viewModel.credentialRequiredEvent.value

        isCancelable = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_credentials_required)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val cred = UserCredential(binding.username.text.toString(), binding.password.text.toString())
                    viewModel.credentialQueue.offer(cred)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    viewModel.credentialQueue.offer(UserCredential())
                    viewModel.disconnect()
                }
                .create()

        return dialog
    }
}