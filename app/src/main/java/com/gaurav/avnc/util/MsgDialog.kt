package com.gaurav.avnc.util

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder


object MsgDialog {

    /**
     * Shows a dialog with given title & message,
     */
    fun show(manager: FragmentManager, @StringRes titleRes: Int, @StringRes msgRes: Int) {
        val fragment = MsgDialogFragment()
        val args = Bundle(2)

        args.putInt("titleRes", titleRes)
        args.putInt("msgRes", msgRes)
        fragment.arguments = args

        fragment.show(manager, null)
    }

    class MsgDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return MaterialAlertDialogBuilder(requireContext())
                    .setTitle(requireArguments().getInt("titleRes"))
                    .setMessage(requireArguments().getInt("msgRes"))
                    .setPositiveButton(android.R.string.ok) { _, _ -> /* Let it dismiss */ }
                    .create()
        }
    }
}