/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentCredentialBinding
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.UserCredential
import com.gaurav.avnc.vnc.VncClient
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
                .setTitle(R.string.title_credentials_required)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val cred = UserCredential(binding.username.text.toString(),
                                              binding.password.text.toString())

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
                clientState.observeForever {
                    if (it == VncClient.State.Connected) {
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
        if (!viewModel.pref.server.credAutocomplete) {
            binding.usernameLayout.endIconMode = TextInputLayout.END_ICON_NONE
            binding.passwordLayout.endIconMode = TextInputLayout.END_ICON_NONE
            return
        }

        viewModel.knownCredentials.observe(this) { list ->
            val usernames = list.map { it.username }.filter { it.isNotBlank() }.distinct()
            val passwords = list.map { it.password }.filter { it.isNotBlank() }.distinct()

            val usernameAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, usernames)
            val passwordAdapter = PasswordAdapter(requireContext(), android.R.layout.simple_list_item_1, passwords)

            binding.username.setAdapter(usernameAdapter)
            binding.password.setAdapter(passwordAdapter)
        }
    }

    /**
     * Specialized adapter for password completion. We don't want to show plaintext password
     * in completion list, so we partially obfuscate it.
     *
     * @param layout Must point to a resource with [TextView] as root
     */
    private class PasswordAdapter(context: Context, layout: Int, passwords: List<String>)
        : ArrayAdapter<String>(context, layout, passwords) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView

            val obfuscated = "**********" + view.text.takeLast(2)
            view.text = obfuscated

            return view
        }
    }
}