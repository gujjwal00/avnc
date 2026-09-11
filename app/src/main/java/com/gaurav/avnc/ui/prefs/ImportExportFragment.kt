/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentImportExportBinding
import com.gaurav.avnc.util.ConfirmDialog
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.util.OpenableDocument
import com.gaurav.avnc.util.QrCode
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.DateFormat
import java.util.Date

@Keep
class ImportExportFragment : Fragment() {

    private enum class Tag { Import, ImportQr, Export, ExportQr }

    companion object {
        private const val REQUEST_IMPORT_URI = "request_import_uri"
        private const val KEY_IMPORT_URI = "import_uri"
    }

    private val importFilePicker = registerForActivityResult(OpenableDocument()) { import(it) }
    private val exportFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { export(it) }
    private val scanCode = registerForActivityResult(ScanContract()) { scanResult ->
        val content = scanResult?.contents ?: return@registerForActivityResult
        val decoded = try {
            QrCode.decode(content)
        } catch (_: QrCode.InvalidQrCodeException) {
            showMsg(getString(R.string.err_invalid_qr_code))
            return@registerForActivityResult
        }

        when (decoded) {
            is QrCode.Content.Json -> viewModel.import(decoded.json)
            is QrCode.Content.Uri -> handleImportedUri(decoded.uri)
        }
    }

    private lateinit var binding: FragmentImportExportBinding
    private val viewModel by activityViewModels<PrefsViewModel>()
    private val authPrompt by lazy { DeviceAuthPrompt(requireActivity()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentImportExportBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.importBtn.setOnClickListener { checkAuthAndStart(Tag.Import) }
        binding.importQrBtn.setOnClickListener { checkAuthAndStart(Tag.ImportQr) }
        binding.exportBtn.setOnClickListener { checkAuthAndStart(Tag.Export) }
        binding.exportQrBtn.setOnClickListener { checkAuthAndStart(Tag.ExportQr) }

        viewModel.importExportFinishedEvent.observe(viewLifecycleOwner) { handleImportExportResult(it) }

        childFragmentManager.setFragmentResultListener(REQUEST_IMPORT_URI, viewLifecycleOwner) { _, bundle ->
            val uri = BundleCompat.getParcelable(bundle, KEY_IMPORT_URI, Uri::class.java)
                    ?: return@setFragmentResultListener
            viewModel.import(uri)
        }

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
            Tag.ImportQr -> launchScan()
            Tag.Export -> launchFilePicker(exportFilePicker, generateFilename())
            Tag.ExportQr -> {
                val json = MutableLiveData<String>()
                json.observe(viewLifecycleOwner) { showQrDialog(it) }
                viewModel.export(json)
            }
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

    private fun launchScan() {
        val options = ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.title_import_qr))
        scanCode.launch(options)
    }

    /**
     * Handles an `AVNC:URI` decoded from a QR code.
     * Displays the URI and asks the user for confirmation before dereferencing it.
     */
    private fun handleImportedUri(uri: Uri) {
        when (uri.scheme) {
            "file", "http", "https" -> {
                val title = getString(R.string.title_import)
                val msg = "${getString(R.string.msg_confirm_import_uri)}\n\n$uri"
                val result = Bundle().apply { putParcelable(KEY_IMPORT_URI, uri) }
                ConfirmDialog.show(childFragmentManager, REQUEST_IMPORT_URI, title, msg, result)
            }
            else -> showMsg(getString(R.string.err_unsupported_qr_uri))
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
            if (it.isNotEmpty())
                showMsg(it)
        }.onFailure {
            MsgDialog.show(childFragmentManager, "Error", it.message ?: "An error occurred")
            Log.e(javaClass.simpleName, "Import/Export error", it)
        }
    }

    /**
     * Shows the exported [json] as a QR code in a dialog.
     */
    private fun showQrDialog(json: String) {
        val title = getString(R.string.title_export_qr)
        val data = QrCode.encode(json)
        QrDialog.show(childFragmentManager, title, data)
    }
}