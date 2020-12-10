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
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Adapter for known servers
 */
class ServersAdapter(val viewModel: HomeViewModel)
    : ListAdapter<ServerProfile, ServersAdapter.ViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemServerBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Profile View Holder
     */
    inner class ViewHolder(binding: ItemServerBinding) : BaseViewHolder<ServerProfile, ItemServerBinding>(binding) {
        override val menuId = R.menu.server_profile_menu

        override fun onClick(view: View) {
            viewModel.startConnection(binding.viewModel!!)
        }

        /**
         * Handle context menu events
         */
        override fun onContextOptionClick(item: MenuItem): Boolean {
            val profile = binding.viewModel!!

            when (item.itemId) {
                R.id.edit -> viewModel.onEditProfile(profile)
                R.id.duplicate -> viewModel.onDuplicateProfile(profile)
                R.id.delete -> viewModel.deleteProfile(profile)
                R.id.copy_address -> viewModel.toClipboard(profile.address)
            }

            return true
        }
    }

    object Differ : DiffUtil.ItemCallback<ServerProfile>() {
        override fun areItemsTheSame(old: ServerProfile, new: ServerProfile) = (old.ID == new.ID)
        override fun areContentsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
    }
}