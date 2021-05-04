/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gaurav.avnc.R
import com.google.android.material.tabs.TabLayout

/**
 * Controls tabs and associated pager in home activity.
 */
class TabController(val fragMgr: FragmentManager, private val pager: ViewPager, tabLayout: TabLayout) {

    private data class PageInfo(
            val fragment: Fragment,
            val tabIconId: Int,
    )

    private val pageList = listOf(
            PageInfo(ServersFragment(), R.drawable.ic_computer),
            PageInfo(DiscoveryFragment(), R.drawable.ic_search),
    )

    private inner class PagerAdapter : FragmentPagerAdapter(fragMgr, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount() = pageList.size
        override fun getItem(position: Int) = pageList[position].fragment
    }


    private val discoveryTab: TabLayout.Tab

    init {
        pager.adapter = PagerAdapter()

        tabLayout.setupWithViewPager(pager)
        discoveryTab = tabLayout.getTabAt(1)!!

        pageList.forEachIndexed { i, p ->
            tabLayout.getTabAt(i)?.icon = ContextCompat.getDrawable(tabLayout.context, p.tabIconId)
        }
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