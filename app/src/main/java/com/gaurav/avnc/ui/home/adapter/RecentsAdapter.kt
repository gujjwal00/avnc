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
import com.gaurav.avnc.databinding.ItemRecentBinding
import com.gaurav.avnc.model.Recent
import com.gaurav.avnc.viewmodel.HomeViewModel

class RecentsAdapter(val viewModel: HomeViewModel)
    : ListAdapter<Recent, RecentsAdapter.ViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemRecentBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(binding: ItemRecentBinding) : BaseViewHolder<Recent, ItemRecentBinding>(binding) {
        override val menuId = R.menu.recent_item_menu

        override fun onClick(view: View) {
            viewModel.startConnection(binding.viewModel!!.profile)
        }

        /**
         * Handle context menu events
         */
        override fun onContextOptionClick(item: MenuItem): Boolean {
            val recent = binding.viewModel!!

            when (item.itemId) {
                R.id.add_bookmark -> viewModel.onNewBookmark(recent.toBookmark())
                R.id.copy_name -> viewModel.toClipboard(recent.profile.displayName)
                R.id.copy_address -> viewModel.toClipboard(recent.profile.host)
                R.id.delete -> viewModel.deleteRecent(recent)
            }

            return true
        }
    }

    object Differ : DiffUtil.ItemCallback<Recent>() {
        override fun areItemsTheSame(old: Recent, new: Recent) = (old.ID == new.ID)
        override fun areContentsTheSame(old: Recent, new: Recent) = (old == new)
    }
}