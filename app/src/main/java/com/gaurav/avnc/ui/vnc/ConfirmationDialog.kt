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
 * This dialog is used to get user-confirmation about something before continuing.
 */
class ConfirmationDialog : DialogFragment() {
    val viewModel by activityViewModels<VncViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null && viewModel.confirmationRequest.value == null) {
            Log.i(javaClass.simpleName, "Activity is being recreated and old ViewModel is gone, removing stale dialog")
            showsDialog = false
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val data = viewModel.confirmationRequest.value
        check(data != null) { "Confirmation dialog started without message!" }

        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(data.first)
                .setMessage(data.second)
                .setPositiveButton(R.string.title_continue) { _, _ -> viewModel.confirmationRequest.offerResponse(true) }
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