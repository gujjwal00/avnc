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
import com.gaurav.avnc.databinding.ItemServerBinding
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.viewmodel.HomeViewModel

class DiscoveryAdapter(val viewModel: HomeViewModel) :
        ListAdapter<VncProfile, DiscoveryAdapter.ViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemServerBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(binding: ItemServerBinding) :
            BaseViewHolder<VncProfile, ItemServerBinding>(binding) {
        override val menuId = R.menu.discovered_server_menu

        override fun onClick(view: View) {
            viewModel.startConnection(binding.viewModel!!)
        }

        /**
         * Handle context menu events
         */
        override fun onContextOptionClick(item: MenuItem): Boolean {
            val server = binding.viewModel!!

            when (item.itemId) {
                R.id.add_bookmark -> viewModel.onNewBookmark(Bookmark(profile = server.copy()))
                R.id.copy_name -> viewModel.toClipboard(server.displayName)
                R.id.copy_address -> viewModel.toClipboard(server.host)
            }

            return true
        }
    }

    object Differ : DiffUtil.ItemCallback<VncProfile>() {
        override fun areItemsTheSame(old: VncProfile, new: VncProfile) = (old.host == new.host)
        override fun areContentsTheSame(old: VncProfile, new: VncProfile) = (old == new)
    }
}