package com.gaurav.avnc.util

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Shows a dialog that asks the user to confirm an action.
 *
 * When confirmed, a fragment result is delivered on the specified key
 * (via [FragmentManager.setFragmentResult]).
 * The host must register a listener with [FragmentManager.setFragmentResultListener].
 *
 * All state (title, message, result) is stored in arguments, so the dialog is
 * safe to be destroyed and recreated by the OS.
 */
class ConfirmDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val title = args.getCharSequence(ARG_TITLE)
        val msg = args.getCharSequence(ARG_MSG)
        val requestKey = args.getString(ARG_REQUEST_KEY) ?: throw Exception("Missing request key")
        val result = args.getBundle(ARG_RESULT) ?: Bundle()

        return MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parentFragmentManager.setFragmentResult(requestKey, result)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> /* Let it dismiss */ }
                .create()
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MSG = "msg"
        private const val ARG_REQUEST_KEY = "request_key"
        private const val ARG_RESULT = "result"

        /**
         * Shows a [ConfirmDialog] with the given [title] and [msg].
         * When confirmed, a fragment result containing [result] is delivered on
         * [requestKey] to the host's [FragmentManager].
         */
        fun show(
                manager: FragmentManager,
                requestKey: String,
                title: CharSequence,
                msg: CharSequence,
                result: Bundle = Bundle(),
        ): Unit = ConfirmDialog().apply {
            arguments = Bundle(4).apply {
                putCharSequence(ARG_TITLE, title)
                putCharSequence(ARG_MSG, msg)
                putString(ARG_REQUEST_KEY, requestKey)
                putBundle(ARG_RESULT, result)
            }
        }.show(manager, null)
    }

}
