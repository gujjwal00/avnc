/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home.adapter

import android.view.LayoutInflater
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
    : ListAdapter<ServerProfile, ProfileViewHolder>(Differ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemServerBinding.inflate(inflater, parent, false)
        return ProfileViewHolder(viewModel, binding, R.menu.server_profile)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object Differ : DiffUtil.ItemCallback<ServerProfile>() {
        override fun areItemsTheSame(old: ServerProfile, new: ServerProfile) = (old.ID == new.ID)
        override fun areContentsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
    }
}