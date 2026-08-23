/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.TabDiscoveredServersBinding
import com.gaurav.avnc.databinding.TabSavedServersBinding
import com.gaurav.avnc.util.setClipboardTextWithNotification
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * This class creates and manages tabs in [HomeActivity].
 * Tabs:
 *        1. Saved servers
 *        2. Discovered servers
 */
class ServerTabs(val activity: HomeActivity) {

    private lateinit var savedServersTab: TabLayout.Tab
    private lateinit var discoveredServersTab: TabLayout.Tab

    /**
     * Creates and initializes tabs
     *
     * [tabLayout] Hosts tabs
     * [pager] Hosts actual content views
     */
    fun create(tabLayout: TabLayout, pager: ViewPager2) {
        pager.adapter = PagerAdapter()
        pager.offscreenPageLimit = 1  // Tell pager to initialize & keep both tabs in memory

        TabLayoutMediator(tabLayout, pager) { _, _ -> }
                .attach()

        savedServersTab = tabLayout.getTabAt(0)!!
        savedServersTab.setIcon(R.drawable.ic_computer)
        savedServersTab.setContentDescription(R.string.desc_saved_servers_tab)

        discoveredServersTab = tabLayout.getTabAt(1)!!
        discoveredServersTab.setIcon(R.drawable.ic_search)
        discoveredServersTab.setContentDescription(R.string.desc_discovered_servers_tab)

        //ViewPager2 uses a RecyclerView internally, and sets it's DescendantFocusability
        //to 'FOCUS_BEFORE_DESCENDANTS', effectively breaking navigation via D-pad or arrow keys.
        pager.forEach { (it as ViewGroup).descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }
    }

    fun showSavedServers() {
        savedServersTab.select()
    }

    fun updateDiscoveryBadge(count: Int) {
        //Currently, we are not showing the actual count in the badge.
        //But maybe we could implement a preference???
        discoveredServersTab.getOrCreateBadge().isVisible = (count != 0)
    }


    /**
     * Adapter for our pager.
     *
     * We have fixed number of static views so our implementation is really simple.
     * We override [getItemViewType] to use the given position itself as view type.
     * Then, in [onCreateViewHolder], that view type (i.e. position) is used to
     * generate corresponding view.
     *
     * As a result, nothing else needs to be done by [ViewHolder] & [bindViewHolder].
     */
    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)

        override fun getItemCount() = 2
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return when (viewType) {
                0 -> ViewHolder(createSavedServersView(parent))
                1 -> ViewHolder(createDiscoveredServersView(parent))
                else -> throw IllegalStateException("Unexpected view type: [$viewType]")
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {}
    }

    private fun createSavedServersView(parent: ViewGroup): View {
        val viewModel = activity.viewModel
        val binding = TabSavedServersBinding.inflate(activity.layoutInflater, parent, false)
        binding.lifecycleOwner = activity
        binding.viewModel = viewModel

        binding.servers.onServerClick = { viewModel.startConnection(it) }
        binding.servers.onEditServer = { viewModel.onEditProfile(it) }
        binding.servers.onDuplicateServer = { viewModel.onDuplicateProfile(it) }
        binding.servers.onDeleteServer = { viewModel.deleteProfile(it) }
        binding.servers.onCopyServerHost = { activity.setClipboardTextWithNotification(it.host) }

        binding.servers.setSource(activity, viewModel.serverProfiles, viewModel.rediscoveredProfiles)
        return binding.root
    }

    private fun createDiscoveredServersView(parent: ViewGroup): View {
        val viewModel = activity.viewModel
        val binding = TabDiscoveredServersBinding.inflate(activity.layoutInflater, parent, false)
        binding.lifecycleOwner = activity
        binding.viewModel = viewModel

        binding.servers.onServerClick = { viewModel.startConnection(it) }
        binding.servers.onSaveServer = { viewModel.onNewProfile(it) }
        binding.servers.onCopyServerName = { activity.setClipboardTextWithNotification(it.name) }
        binding.servers.onCopyServerHost = { activity.setClipboardTextWithNotification(it.host) }

        binding.servers.setSource(activity, viewModel.discovery.servers)
        return binding.root
    }
}