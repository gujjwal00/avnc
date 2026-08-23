/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ServerListItemBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.debugCheck
import com.gaurav.avnc.util.debugCheckNotNull


typealias ServerListItemAction = (profile: ServerProfile) -> Unit

/**
 *  Server list widget
 */
class ServerList(context: Context, attrs: AttributeSet?, defStyleAttr: Int)
    : RecyclerView(context, attrs, defStyleAttr) {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    /**
     * Action available on individual items
     */
    var onServerClick: ServerListItemAction? = null
    var onSaveServer: ServerListItemAction? = null
    var onEditServer: ServerListItemAction? = null
    var onDuplicateServer: ServerListItemAction? = null
    var onDeleteServer: ServerListItemAction? = null
    var onCopyServerName: ServerListItemAction? = null
    var onCopyServerHost: ServerListItemAction? = null

    var lifecycleOwner: LifecycleOwner? = null; private set
    var indicatorSource: LiveData<List<ServerProfile>>? = null; private set
    private val serverListAdapter = ServerListAdapter(this)

    init {
        setHasFixedSize(true)
        layoutManager = LinearLayoutManager(context)
        adapter = serverListAdapter
    }

    /**
     * Set data source for this server list.
     */
    fun setSource(owner: LifecycleOwner, source: LiveData<List<ServerProfile>>) {
        lifecycleOwner = owner
        source.observe(owner) { serverListAdapter.submitList(it) }
    }

    /**
     * Set data source for this server list.
     * [indicatorSource] is used to show blinking indicator for rediscovered servers.
     */
    fun setSource(owner: LifecycleOwner, source: LiveData<List<ServerProfile>>, indicatorSource: LiveData<List<ServerProfile>>) {
        this.indicatorSource = indicatorSource
        setSource(owner, source)
    }
}

/**
 * List adapter
 */
private class ServerListAdapter(private val serverList: ServerList) : ListAdapter<ServerProfile, ServerListViewHolder>(Differ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerListViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ServerListItemBinding.inflate(inflater, parent, false)

        debugCheckNotNull(serverList.lifecycleOwner)
        binding.lifecycleOwner = serverList.lifecycleOwner
        return ServerListViewHolder(serverList, binding)
    }

    override fun onBindViewHolder(holder: ServerListViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

/**
 * List item
 */
private class ServerListViewHolder(val serverList: ServerList, val binding: ServerListItemBinding)
    : RecyclerView.ViewHolder(binding.root) {

    init {
        binding.root.setOnCreateContextMenuListener { menu, _, _ -> prepareContextMenu(menu) }
        binding.root.setOnClickListener { invokeAction(serverList.onServerClick) }
        binding.saveBtn.setOnClickListener { invokeAction(serverList.onSaveServer) }
    }

    fun bind(profile: ServerProfile) {
        binding.profile = profile
        serverList.indicatorSource?.let { binding.indicator.setup(profile, it) }
    }

    private fun prepareContextMenu(menu: ContextMenu) {
        prepareContextMenuItem(menu, serverList.onEditServer, R.string.title_edit)
        prepareContextMenuItem(menu, serverList.onDuplicateServer, R.string.title_duplicate)
        prepareContextMenuItem(menu, serverList.onCopyServerName, R.string.title_copy_name)
        prepareContextMenuItem(menu, serverList.onCopyServerHost, R.string.title_copy_host)
        prepareContextMenuItem(menu, serverList.onDeleteServer, R.string.title_delete)
    }

    private fun prepareContextMenuItem(menu: ContextMenu, action: ServerListItemAction?, titleRes: Int) {
        if (action != null) // Add menu item only if the action is configured
            menu.add(titleRes).setOnMenuItemClickListener { invokeAction(action) }
    }

    private fun invokeAction(action: ServerListItemAction?): Boolean {
        debugCheckNotNull(binding.profile)
        return binding.profile?.let { action?.invoke(it); true } ?: false
    }
}

private object Differ : DiffUtil.ItemCallback<ServerProfile>() {
    override fun areItemsTheSame(old: ServerProfile, new: ServerProfile): Boolean {
        debugCheck(old.isSaved() == new.isSaved())
        if (old.isSaved())
            return old.ID == new.ID // Use ID match saved servers
        else
            return old == new
    }

    override fun areContentsTheSame(old: ServerProfile, new: ServerProfile): Boolean {
        return old == new
    }
}
