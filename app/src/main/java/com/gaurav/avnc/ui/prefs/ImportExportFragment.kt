/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.biometric.auth.startClass2BiometricOrCredentialAuthentication
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentImportExportBinding
import com.gaurav.avnc.util.OpenableDocument
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.*

@Keep
class ImportExportFragment : Fragment() {

    private val importFilePicker = registerForActivityResult(OpenableDocument()) { import(it) }
    private val exportFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument()) { export(it) }

    private lateinit var binding: FragmentImportExportBinding
    private val viewModel by activityViewModels<PrefsViewModel>()


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (savedInstanceState == null)
            viewModel.importExportError.value = null

        binding = FragmentImportExportBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.importBtn.setOnClickListener { startImport() }
        binding.exportBtn.setOnClickListener { startExport() }

        viewModel.importFinishedEvent.observe(viewLifecycleOwner) { if (it == true) showMsg(R.string.msg_imported) }
        viewModel.exportFinishedEvent.observe(viewLifecycleOwner) { if (it == true) showMsg(R.string.msg_exported) }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.pref_import_export)
    }

    private fun showMsg(@StringRes msgRes: Int) {
        Snackbar.make(requireView(), msgRes, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Generates a name for export file.
     */
    private fun generateFilename(): String {
        val date = Date()
        val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
        return "${getString(R.string.app_name)}-Export-${date.time} $dateStr.json"
    }

    private fun startImport() {
        importFilePicker.launch(arrayOf("*/*"))
    }

    /**
     * If user has enabled any authentication method, we verify the user before exporting data.
     * This is to protect sensitive info that might be present in exported data.
     */
    private fun startExport() {
        if (viewModel.canAuthenticateUser)
            startClass2BiometricOrCredentialAuthentication(
                    title = getString(R.string.msg_export_auth_required),
                    confirmationRequired = false,
                    callback = ExportAuthCallback()
            )
        else
            exportFilePicker.launch(generateFilename())
    }

    private fun import(uri: Uri?) {
        if (uri != null)
            viewModel.import(uri, binding.deleteCurrentServers.isChecked)
    }

    private fun export(uri: Uri?) {
        if (uri != null)
            viewModel.export(uri)
    }

    /**
     * Used for authentication before Export
     */
    private inner class ExportAuthCallback : AuthPromptCallback() {

        override fun onAuthenticationSucceeded(a: FragmentActivity?, r: BiometricPrompt.AuthenticationResult) {
            exportFilePicker.launch(generateFilename())
        }

        override fun onAuthenticationError(a: FragmentActivity?, errorCode: Int, errString: CharSequence) {
            viewModel.importExportError.value = errString.toString()
        }

        override fun onAuthenticationFailed(activity: FragmentActivity?) {
            viewModel.importExportError.value = "Authentication failed!"
        }
    }
}