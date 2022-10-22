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
import android.util.ArrayMap
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentCredentialBinding
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.UserCredential
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

/**
 * Allows user to enter credentials for remote server.
 */
class CredentialFragment : DialogFragment() {
    val viewModel by activityViewModels<VncViewModel>()
    lateinit var binding: FragmentCredentialBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentCredentialBinding.inflate(layoutInflater, null, false)

        binding.usernameRequired = viewModel.credentialRequest.value
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
                .setTitle(R.string.title_login)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val username = binding.username.text.toString()
                    val password = getRealPassword(binding.password.text.toString())

                    val cred = UserCredential(username, password)
                    viewModel.credentialRequest.offerResponse(cred)

                    if (binding.remember.isChecked)
                        scheduleCredentialSave(viewModel, cred)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    viewModel.credentialRequest.cancelRequest()
                    requireActivity().finish()
                }
                .create()
    }

    companion object {

        /**
         * If user has asked to remember credentials, we need to save them
         * to database. But we don't want to save them immediately because
         * user might have mistyped them. So, we wait until successful
         * connection before saving them.
         *
         * This method is 'static' to avoid any accidental leak of fragment instance.
         */
        private fun scheduleCredentialSave(viewModel: VncViewModel, cred: UserCredential) {
            with(viewModel) {
                state.observeForever {
                    if (it == VncViewModel.State.Connected) {
                        profile.username = cred.username
                        profile.password = cred.password
                        saveProfile()
                    }
                }
            }
        }
    }

    /**
     * Hooks completion adapters
     *
     * This feature might not be that useful to end-users, but it saves a lot of time
     * during development because I have to frequently install/uninstall app, test
     * different servers running on different addresses/ports.
     */
    private fun setupAutoComplete() {
        if (viewModel.pref.server.lockSavedServer)
            return

        viewModel.knownCredentials.observe(this) { list ->
            val usernames = list.map { it.username }.filter { it.isNotEmpty() }.distinct()
            val passwords = preparePasswordSuggestions(list)

            if (usernames.isNotEmpty()) {
                val usernameAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usernames)
                binding.username.setAdapter(usernameAdapter)
                binding.usernameLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            }

            if (passwords.isNotEmpty()) {
                val passwordAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, passwords)
                binding.password.setAdapter(passwordAdapter)
                binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
            }
        }
    }

    /**
     * Instead of showing plaintext passwords, we show server name & host in suggestion
     * list. When user taps OK, we convert the suggestion back to real password.
     */
    private val passwordMap = ArrayMap<String, String>()

    private fun preparePasswordSuggestions(list: List<LoginInfo>): List<String> {
        list.filter { it.password.isNotEmpty() }
                .map { Pair("from: ${it.name} [${it.host}]", it.password) }
                .distinct()
                .toMap(passwordMap)
                .removeAll(passwordMap.values) //Guard against (very unlikely) clash with real password

        return passwordMap.keys.toList()
    }

    private fun getRealPassword(typedPassword: String) = passwordMap[typedPassword] ?: typedPassword
}