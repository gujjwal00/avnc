/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityHomeBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * Primary activity of the app.
 *
 * It Provides access to saved and discovered servers.
 */
class HomeActivity : AppCompatActivity() {
    private val viewModel by viewModels<HomeViewModel>()
    private lateinit var binding: ActivityHomeBinding
    private lateinit var tabController: TabController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //View Inflation
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        binding.lifecycleOwner = this

        tabController = TabController(supportFragmentManager, binding.pager, binding.tabs)
        binding.drawerNav.setNavigationItemSelectedListener { onMenuItemSelected(it.itemId) }
        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.open() }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemSelected(it.itemId) }
        binding.toolbar.setOnClickListener { showUrlActivity() }

        //Observers
        viewModel.profileEditEvent.observe(this) { showProfileEditor() }
        viewModel.profileDeletedEvent.observe(this) { showProfileDeletedMsg(it) }
        viewModel.newConnectionEvent.observe(this) { startVncActivity(it) }
        viewModel.discovery.servers.observe(this, Observer { updateDiscoveryBadge(it) })

        viewModel.startDiscovery()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    /**
     * Handle drawer item selection.
     */
    private fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.settings -> showSettings()
            else -> return false
        }

        return true
    }

    /**
     * Launches Settings activity
     */
    private fun showSettings() {
        startActivity(Intent(this, PrefsActivity::class.java))
    }

    /**
     * Launches VNC Url activity
     */
    private fun showUrlActivity() {
    }

    /**
     * Starts profile editor fragment.
     */
    private fun showProfileEditor() {
        ProfileEditorFragment().show(supportFragmentManager, "ProfileEditor")
    }

    /**
     * Shows delete confirmation snackbar.
     */
    private fun showProfileDeletedMsg(profile: ServerProfile) {
        Snackbar.make(binding.root, getString(R.string.msg_server_profile_deleted), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.title_undo)) { viewModel.saveProfile(profile) }
                .show()
    }

    /**
     * Starts VNC Activity with given profile.
     */
    private fun startVncActivity(profile: ServerProfile) {
        val intent = Intent(this, VncActivity::class.java)
        intent.putExtra(VncActivity.KEY.PROFILE, profile)
        startActivity(intent)
    }

    private fun updateDiscoveryBadge(list: List<ServerProfile>) {
        tabController.updateDiscoveryBadge(list.size)
    }
}