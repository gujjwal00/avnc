/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home.adapter

import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ItemBookmarkBinding
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Adapter for Bookmarks RV
 */
class BookmarksAdapter(val viewModel: HomeViewModel)
    : ListAdapter<Bookmark, BookmarksAdapter.ViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBookmarkBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Bookmark View Holder
     */
    inner class ViewHolder(binding: ItemBookmarkBinding) : BaseViewHolder<Bookmark, ItemBookmarkBinding>(binding) {
        override val menuId = R.menu.bookmark_item_menu

        override fun onClick(view: View) {
            viewModel.startConnection(binding.viewModel!!)
        }

        /**
         * Handle context menu events
         */
        override fun onContextOptionClick(item: MenuItem): Boolean {
            val bookmark = binding.viewModel!!

            when (item.itemId) {
                R.id.edit -> viewModel.onEditBookmark(bookmark)
                R.id.duplicate -> viewModel.onDuplicateBookmark(bookmark)
                R.id.delete -> viewModel.deleteBookmark(bookmark)
                R.id.copy_address -> viewModel.toClipboard(bookmark.profile.host)
            }

            return true
        }
    }

    object Differ : DiffUtil.ItemCallback<Bookmark>() {
        override fun areItemsTheSame(old: Bookmark, new: Bookmark) = (old.ID == new.ID)
        override fun areContentsTheSame(old: Bookmark, new: Bookmark) = (old == new)
    }
}