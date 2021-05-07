/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityHomeBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.ui.vnc.startVncActivity
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
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        //View Inflation
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        binding.lifecycleOwner = this

        tabController = TabController(this, binding.pager, binding.tabs)
        binding.drawerNav.setNavigationItemSelectedListener { onMenuItemSelected(it.itemId) }
        binding.navigationBtn.setOnClickListener { binding.drawerLayout.open() }
        binding.settingsBtn.setOnClickListener { showSettings() }
        binding.urlbar.setOnClickListener { showUrlActivity() }

        //Observers
        viewModel.editProfileEvent.observe(this) { showProfileEditor() }
        viewModel.profileInsertedEvent.observe(this) { onProfileInserted(it) }
        viewModel.profileDeletedEvent.observe(this) { showProfileDeletedMsg(it) }
        viewModel.newConnectionEvent.observe(this) { startVncActivity(this, it) }
        viewModel.discovery.servers.observe(this) { updateDiscoveryBadge(it) }
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
        val anim = ActivityOptions.makeSceneTransitionAnimation(this, binding.urlbar, "urlbar")
        startActivity(Intent(this, UrlBarActivity::class.java), anim.toBundle())
    }

    /**
     * Starts profile editor fragment.
     */
    private fun showProfileEditor() {
        if (viewModel.pref.appearance.preferAdvancedEditor)
            ProfileEditorFragment().showAdvanced(supportFragmentManager)
        else
            ProfileEditorFragment().show(supportFragmentManager)
    }

    private fun onProfileInserted(profile: ServerProfile) {
        tabController.showSavedServers()

        // Only show snackbar for new servers
        if (profile.ID == 0L)
            Snackbar.make(binding.root, R.string.msg_server_profile_inserted, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Shows delete confirmation snackbar.
     */
    private fun showProfileDeletedMsg(profile: ServerProfile) {
        Snackbar.make(binding.root, R.string.msg_server_profile_deleted, Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.title_undo)) { viewModel.insertProfile(profile) }
                .show()
    }

    private fun updateDiscoveryBadge(list: List<ServerProfile>) {
        tabController.updateDiscoveryBadge(list.size)
    }
}