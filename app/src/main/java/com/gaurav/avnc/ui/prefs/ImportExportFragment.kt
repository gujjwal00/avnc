/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentImportExportBinding
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.util.OpenableDocument
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.util.Date

@Keep
class ImportExportFragment : Fragment() {

    private enum class Tag { Import, Export }

    private val importFilePicker = registerForActivityResult(OpenableDocument()) { import(it) }
    private val exportFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { export(it) }

    private lateinit var binding: FragmentImportExportBinding
    private val viewModel by activityViewModels<PrefsViewModel>()
    private val authPrompt by lazy { DeviceAuthPrompt(requireActivity()) }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentImportExportBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.importBtn.setOnClickListener { checkAuthAndStart(Tag.Import) }
        binding.exportBtn.setOnClickListener { checkAuthAndStart(Tag.Export) }

        viewModel.importExportFinishedEvent.observe(viewLifecycleOwner) { handleImportExportResult(it) }

        authPrompt.init(
                onSuccess = { checkNotNull(it as? Tag); start(it) },
                onFail = { showMsg("Authentication error: $it") }
        )

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.pref_import_export)
    }

    private fun showMsg(msg: CharSequence) {
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Generates a name for export file.
     */
    private fun generateFilename(): String {
        val date = Date()
        val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)
        return "${getString(R.string.app_name)}-Export-${date.time} $dateStr.json"
    }

    /**
     * If user has enabled any authentication method, we verify the user before exporting data.
     * This is to protect sensitive info that might be present in exported data.
     */
    private fun checkAuthAndStart(tag: Tag) {
        if (authPrompt.canLaunch())
            authPrompt.launch(getString(R.string.msg_export_auth_required), tag)
        else
            start(tag)
    }

    private fun start(tag: Tag) {
        when (tag) {
            Tag.Import -> launchFilePicker(importFilePicker, arrayOf("*/*"))
            Tag.Export -> launchFilePicker(exportFilePicker, generateFilename())
        }
    }

    private fun <I> launchFilePicker(picker: ActivityResultLauncher<I>, args: I) {
        try {
            picker.launch(args)
        } catch (e: ActivityNotFoundException) {
            showMsg("Error: No app found to choose backup file.")
            Log.e("ImportExport", "Error: No app found to choose backup file.", e)
        }
    }

    private fun import(uri: Uri?) {
        if (uri != null)
            viewModel.import(uri)
    }

    private fun export(uri: Uri?) {
        if (uri != null)
            viewModel.export(uri)
    }

    private fun handleImportExportResult(result: Result<String>) {
        result.onSuccess {
            showMsg(it)
        }.onFailure {
            MsgDialog.show(childFragmentManager, "Error", it.message ?: "An error occurred")
            Log.e(javaClass.simpleName, "Import/Export error", it)
        }
    }
}