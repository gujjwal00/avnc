/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * Released under the terms of GPLv3 (or later).
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home.adapter

import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.forEach
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import com.gaurav.avnc.BR

/**
 * ViewHolder encapsulating common actions.
 */
abstract class BaseViewHolder<E, B : ViewDataBinding>(val binding: B)
    : RecyclerView.ViewHolder(binding.root) {

    abstract val menuId: Int

    init {
        val root = binding.root

        root.setOnClickListener(::onClick)

        root.setOnCreateContextMenuListener { contextMenu, view, _ ->
            MenuInflater(view.context).inflate(menuId, contextMenu)
            contextMenu.forEach { item ->
                item.setOnMenuItemClickListener(::onContextOptionClick)
            }
        }
    }


    open fun onClick(view: View) {}

    open fun onContextOptionClick(item: MenuItem) = false

    fun bind(item: E) {
        binding.setVariable(BR.viewModel, item)
        binding.executePendingBindings()
    }
}

