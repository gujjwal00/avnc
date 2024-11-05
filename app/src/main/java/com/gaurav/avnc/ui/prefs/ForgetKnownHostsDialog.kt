/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.gaurav.avnc.R
import com.gaurav.avnc.util.deleteTrustedCertificates
import com.gaurav.avnc.util.forgetKnownHosts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ForgetKnownHostsDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_forget_known_hosts)
                .setMessage(R.string.pref_forget_known_hosts_question)
                .setPositiveButton(R.string.title_forget) { _, _ -> forget() }
                .setNegativeButton(R.string.title_cancel) { _, _ -> }
                .create()
    }

    private fun forget() {
        val success = forgetKnownHosts(requireContext()) && deleteTrustedCertificates(requireContext())
        val view: View? = parentFragment?.view ?: requireActivity().findViewById(android.R.id.content)
        if (view != null) {
            if (success)
                Snackbar.make(view, R.string.msg_done, Snackbar.LENGTH_SHORT).show()
            else
                Snackbar.make(view, "Error forgetting hosts", Snackbar.LENGTH_LONG).show()
        }
    }
}