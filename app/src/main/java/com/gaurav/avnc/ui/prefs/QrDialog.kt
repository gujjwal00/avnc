/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.gaurav.avnc.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

/**
 * [DialogFragment] that displays a QR code.
 */
class QrDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = requireArguments().getCharSequence(ARG_TITLE)
        val data = requireArguments().getCharSequence(ARG_DATA) ?: ""

        val image = try {
            QRCodeWriter()
                    .encode(data.toString(), BarcodeFormat.QR_CODE, 512, 512)
                    .let { matrix ->
                        createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).apply {
                            for (x in 0 until matrix.width)
                                for (y in 0 until matrix.height)
                                    this[x, y] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                        }
                    }.let { bitmap ->
                        ImageView(requireContext()).apply {
                            setImageBitmap(bitmap)
                            adjustViewBounds = true
                        }
                    }
        } catch (e: WriterException) {
            Log.e(QrDialog::class.simpleName, "Failed to generate QR code", e)
            return dialogBuilder(title)
                    .setMessage(R.string.err_qr_export_failed)
                    .create()
        }

        return dialogBuilder(title)
                .setView(image)
                .create()
    }

    private fun dialogBuilder(title: CharSequence?): MaterialAlertDialogBuilder =
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setPositiveButton(android.R.string.ok, null)

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DATA = "data"

        /**
         * Shows a [QrDialog] with the given [title],
         * embedding a QR code with the given [data].
         */
        fun show(manager: FragmentManager, title: CharSequence, data: CharSequence): Unit =
                QrDialog().apply {
                    arguments = Bundle(2).apply {
                        putCharSequence(ARG_TITLE, title)
                        putCharSequence(ARG_DATA, data)
                    }
                }.show(manager, null)
    }

}
