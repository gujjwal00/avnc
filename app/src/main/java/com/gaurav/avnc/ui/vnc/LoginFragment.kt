/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.ArrayMap
import android.util.Log
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentCredentialBinding
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.VncViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

/**
 * Allows user to enter login information.
 *
 * There are different types of login information ([LoginInfo.Type]),
 * but all of them basically boils down to a username/password combo.
 *
 * User can choose to "remember" the information, in which case it will be
 * saved in the profile.
 *
 */
class LoginFragment : DialogFragment() {
    private lateinit var binding: FragmentCredentialBinding
    private val viewModel by activityViewModels<VncViewModel>()
    private val loginType by lazy { viewModel.loginInfoRequest.value!! }
    private val loginInfo by lazy { getLoginInfoFromProfile(viewModel.profile) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && viewModel.loginInfoRequest.value == null) {
            Log.i(javaClass.simpleName, "Activity is being recreated and old ViewModel is gone, removing stale login dialog")
            showsDialog = false
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        check(viewModel.loginInfoRequest.value != null) { "Login fragment invoked without a login type" }
        binding = FragmentCredentialBinding.inflate(layoutInflater, null, false)

        binding.loginInfo = loginInfo
        binding.usernameLayout.isVisible = loginInfo.username.isBlank() && loginType == LoginInfo.Type.VNC_CREDENTIAL
        binding.passwordLayout.isVisible = loginInfo.password.isBlank()
        binding.remember.isVisible = viewModel.profile.ID != 0L && loginType != LoginInfo.Type.SSH_KEY_PASSWORD

        binding.password.setOnEditorActionListener { _, _, _ ->
            onOk()
            dismiss()
            true
        }

        if (loginType == LoginInfo.Type.SSH_KEY_PASSWORD) {
            binding.passwordLayout.setHint(R.string.hint_key_password)
        }

        setupAutoComplete()

        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(getTitle())
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                .create().apply {
                    setCanceledOnTouchOutside(false)
                }
    }

    override fun onCancel(dialog: DialogInterface) {
        onCancel()
    }

    private fun getTitle() = when (loginType) {
        LoginInfo.Type.VNC_PASSWORD,
        LoginInfo.Type.VNC_CREDENTIAL -> R.string.title_vnc_login
        LoginInfo.Type.SSH_PASSWORD -> R.string.title_ssh_login
        LoginInfo.Type.SSH_KEY_PASSWORD -> R.string.title_unlock_private_key
    }

    private fun getLoginInfoFromProfile(p: ServerProfile): LoginInfo {
        return LoginInfo.fromProfile(p, loginType)
    }

    private fun onOk() {
        loginInfo.password = getRealPassword(loginInfo.password)
        viewModel.loginInfoRequest.offerResponse(loginInfo)
        if (binding.remember.isChecked)
            viewModel.loginInfoToBeRemembered += loginInfo
    }

    private fun onCancel() {
        requireActivity().finish()
    }


    /**********************************************************************************************
     * Autocompletion
     *
     * This feature might not be that useful to end-users, but it saves a lot of time
     * during development because I have to frequently install/uninstall app, test
     * different servers running on different addresses/ports.
     *********************************************************************************************/
    private fun setupAutoComplete() {
        if (viewModel.pref.server.lockSavedServer || loginType == LoginInfo.Type.SSH_KEY_PASSWORD)
            return

        viewModel.savedProfiles.observe(this) { profiles ->
            val usernames = prepareUsernameSuggestions(profiles)
            val passwords = preparePasswordSuggestions(profiles)

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

    private fun prepareUsernameSuggestions(profiles: List<ServerProfile>): List<String> {
        if (loginType != LoginInfo.Type.VNC_CREDENTIAL)
            return listOf()

        return profiles.map { it.username }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Instead of showing plaintext passwords, we show server name & host in suggestion
     * list. When user taps OK, we convert the suggestion back to real password.
     */
    private val passwordMap = ArrayMap<String, String>()

    private fun preparePasswordSuggestions(profiles: List<ServerProfile>): List<String> {
        profiles.map { Pair(getPasswordLabel(it), getLoginInfoFromProfile(it).password) }
                .distinct()
                .filter { it.second.isNotEmpty() }
                .toMap(passwordMap)
                .removeAll(passwordMap.values) //Guard against unlikely clash with real password

        return passwordMap.keys.toList()
    }

    private fun getPasswordLabel(profile: ServerProfile): String {
        val host = if (loginType == LoginInfo.Type.SSH_PASSWORD) profile.sshHost else profile.host
        return "from: ${profile.name} [${host}]"

    }

    private fun getRealPassword(typedPassword: String) = passwordMap[typedPassword] ?: typedPassword
}