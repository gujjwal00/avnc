/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.viewmodel.VncViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * This dialog is used to get user-confirmation before connecting to unknown SSH servers.
 */
class HostKeyFragment : DialogFragment() {
    val viewModel by activityViewModels<VncViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && viewModel.sshHostKeyVerifyRequest.value == null) {
            Log.i(javaClass.simpleName, "Activity is being recreated and old ViewModel is gone, removing stale dialog")
            showsDialog = false
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val request = viewModel.sshHostKeyVerifyRequest
        val hostKey = request.value!!
        val titleRes = if (hostKey.isKnownHost) R.string.title_ssh_host_key_changed else R.string.title_unknown_ssh_host

        val message = """
                 |
                 |Host:   ${hostKey.host}
                 |Key type:   ${hostKey.algo.uppercase()}
                 |Key fingerprint: 
                 |  
                 |${hostKey.getFingerprint()}
                 |
                 |Please make sure your are connecting to the valid host.
                 |
                 |If you continue, this host & key will be marked as known.
                 """.trimMargin()

        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(R.string.title_continue) { _, _ -> request.offerResponse(true) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> abortSession() }
                .create().apply {
                    setCanceledOnTouchOutside(false)
                }
    }

    override fun onCancel(dialog: DialogInterface) {
        abortSession()
    }

    private fun abortSession() {
        requireActivity().finish()
    }
}