/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home.adapter

import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.forEach
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.gaurav.avnc.BR
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * ViewHolder for [ServerProfile].
 */
open class ProfileViewHolder(
        private val homeViewModel: HomeViewModel,
        private val binding: ViewDataBinding,
        private val menuId: Int)
    : RecyclerView.ViewHolder(binding.root) {

    /**
     * Points to the profile being rendered by this view holder.
     * See [bind].
     */
    var profile = ServerProfile()

    init {
        val root = binding.root

        root.setOnClickListener {
            homeViewModel.startConnection(profile)
        }

        root.setOnCreateContextMenuListener { contextMenu, view, _ ->
            MenuInflater(view.context).inflate(menuId, contextMenu)
            contextMenu.forEach { item ->
                item.setOnMenuItemClickListener { onContextOptionClick(it) }
            }
        }
    }

    /**
     * Handle context menu events
     */
    private fun onContextOptionClick(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.edit -> homeViewModel.onEditProfile(profile)
            R.id.duplicate -> homeViewModel.onDuplicateProfile(profile)
            R.id.delete -> homeViewModel.deleteProfile(profile)
            R.id.copy_address -> homeViewModel.setClipboardText(profile.address)
            R.id.copy_name -> homeViewModel.setClipboardText(profile.name)
        }

        return true
    }

    /**
     * Binds given profile to this view holder.
     */
    fun bind(profile: ServerProfile) {
        this.profile = profile
        binding.setVariable(BR.viewModel, profile)
        binding.executePendingBindings()
    }
}

