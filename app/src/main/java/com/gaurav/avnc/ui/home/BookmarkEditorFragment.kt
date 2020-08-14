/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * Released under the terms of GPLv3 (or later).
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentBookmarkEditorBinding
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Editor fragment used for creating and editing bookmarks.
 *
 * It uses {@see HomeViewModel.editorBookmark} as backing storage.
 * If its ID is 0, a new bookmark will be created.
 */
class BookmarkEditorFragment : DialogFragment() {

    private val viewModel by activityViewModels<HomeViewModel>()
    private val bookmark by lazy { viewModel.bookmarkEditEvent.value!! }

    /**
     * Creates editor dialog.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val binding = FragmentBookmarkEditorBinding.inflate(layoutInflater, null, false)
        binding.viewModel = viewModel

        val dialog = AlertDialog.Builder(requireContext())
                .setView(binding.root)
                .setTitle(getTitle())
                .setPositiveButton(R.string.title_save) { _, _ -> onSave() }
                .setNegativeButton(R.string.title_cancel) { _, _ -> dismiss() }
                .setNeutralButton(R.string.title_more) { _, _ -> }
                .create()

        //Customize neutral button directly to avoid dialog dismissal on click
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener {
                binding.showAll = true
                it.visibility = View.GONE
            }
        }

        return dialog
    }

    /**
     * Returns title string resource
     */
    private fun getTitle() =
            if (isNew(bookmark)) R.string.title_new_bookmark
            else R.string.title_edit_bookmark


    /**
     * Saves current bookmark.
     */
    private fun onSave() =
            if (isNew(bookmark)) viewModel.insertBookmark(bookmark)
            else viewModel.updateBookmark(bookmark)


    /**
     * Checks if given bookmark is new (i.e. not stored in database)
     */
    private fun isNew(bookmark: Bookmark) = (bookmark.ID == 0L)
}
