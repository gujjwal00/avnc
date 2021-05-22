/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.gaurav.avnc.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Controls tabs and associated pager in home activity.
 */
class TabController(val activity: HomeActivity, private val pager: ViewPager2, tabLayout: TabLayout) {

    private data class PageInfo(
            val fragment: Fragment,
            @DrawableRes val iconId: Int,
            @StringRes val descriptionId: Int
    )

    private val pageList = listOf(
            PageInfo(ServersFragment(), R.drawable.ic_computer, R.string.desc_saved_servers_tab),
            PageInfo(DiscoveryFragment(), R.drawable.ic_search, R.string.desc_discovered_servers_tab),
    )

    private inner class PagerAdapter : FragmentStateAdapter(activity) {
        override fun getItemCount() = pageList.size
        override fun createFragment(position: Int) = pageList[position].fragment
    }


    private val discoveryTab: TabLayout.Tab

    init {
        pager.adapter = PagerAdapter()

        val mediator = TabLayoutMediator(tabLayout, pager) { tab, position ->
            tab.setIcon(pageList[position].iconId)
            tab.setContentDescription(pageList[position].descriptionId)
        }

        mediator.attach()
        discoveryTab = tabLayout.getTabAt(1)!!
    }

    fun showSavedServers() {
        pager.setCurrentItem(0, true)
    }

    fun updateDiscoveryBadge(count: Int) {
        //Currently we are not showing the actual count in the badge.
        //But maybe we could implement a preference???
        discoveryTab.getOrCreateBadge().isVisible = (count != 0)
    }
}