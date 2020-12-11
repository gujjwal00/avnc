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
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
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
    lateinit var binding: FragmentCredentialBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentCredentialBinding.inflate(layoutInflater, null, false)

        binding.usernameRequired = viewModel.credentialRequiredEvent.value
        binding.canRemember = viewModel.profile.ID != 0L

        setupAutoComplete()

        isCancelable = false
        return prepareDialog()
    }


    /**
     * Prepare dialog instance
     */
    private fun prepareDialog(): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_credentials_required)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val cred = UserCredential(binding.username.text.toString(), binding.password.text.toString())
                    viewModel.credentialQueue.offer(cred)

                    if (binding.remember.isChecked) {
                        //Put credentials in current profile, which will be saved by the
                        //Viewmodel after connection is successful.
                        viewModel.profile.username = cred.username
                        viewModel.profile.password = cred.password
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    viewModel.credentialQueue.offer(UserCredential())
                    viewModel.disconnect()
                }
                .create()
    }

    /**
     * Hooks completion adapters
     */
    private fun setupAutoComplete() {
        if (!viewModel.pref.cred.autocomplete)
            return

        viewModel.knownCredentials.observe(this, Observer { list ->
            val usernames = list.map { it.username }.filter { it.isNotBlank() }
            val passwords = list.map { it.password }.filter { it.isNotBlank() }

            val usernameAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usernames)
            val passwordAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, passwords)

            binding.username.setAdapter(usernameAdapter)
            binding.password.setAdapter(passwordAdapter)
        })
    }
}