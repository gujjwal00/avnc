/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.gaurav.avnc.R
import com.gaurav.avnc.util.DeviceAuthPrompt
import com.google.android.material.appbar.MaterialToolbar

class PrefsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        DeviceAuthPrompt.applyFingerprintDialogFix(supportFragmentManager)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_host, Main())
                    .commit()
        }

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Starts new fragment corresponding to given [pref].
     */
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment!!)

        supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_host, fragment)
                .addToBackStack(null)
                .commit()

        return true
    }


    /**************************************************************************
     * Preference Fragments
     **************************************************************************/

    abstract class PrefFragment(private val prefResource: Int) : PreferenceFragmentCompat() {

        override fun onResume() {
            super.onResume()
            activity?.title = preferenceScreen.title
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(prefResource, rootKey)
        }
    }

    @Keep class Main : PrefFragment(R.xml.pref_main)
    @Keep class Appearance : PrefFragment(R.xml.pref_appearance)

    @Keep class Viewer : PrefFragment(R.xml.pref_viewer) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // If system does not support PiP, disable its preference
            val hasPiPSupport = Build.VERSION.SDK_INT >= 26 &&
                                requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

            if (!hasPiPSupport) {
                findPreference<SwitchPreference>("pip_enabled")!!.apply {
                    isEnabled = false
                    summary = getString(R.string.msg_pip_not_supported)
                }
            }
        }
    }

    @Keep class Input : PrefFragment(R.xml.pref_input) {
        private val visibilityTests = mutableMapOf<Preference, (Map<String, Any?>) -> Boolean>()
        private val enablementTests = mutableMapOf<Preference, (Map<String, Any?>) -> Boolean>()
        private val prefChangeListener = OnSharedPreferenceChangeListener { _, _ -> applyTests() }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(prefChangeListener)

            val canChangePtrIcon = Build.VERSION.SDK_INT >= 24
            if (!canChangePtrIcon) {
                findPreference<SwitchPreference>("hide_local_cursor")!!.apply {
                    isEnabled = false
                    summary = getString(R.string.msg_ptr_hiding_not_supported)
                }
            }

            findPreference<SwitchPreference>("capture_pointer")!!.apply {
                showIf { Build.VERSION.SDK_INT >= 26 }
            }

            findPreference<ListPreferenceEx>("gesture_swipe1")!!.apply {
                enableIf { it["gesture_style"] != "touchpad" }
                disabledStateSummary = getString(R.string.pref_gesture_action_move_pointer)
            }
            findPreference<ListPreferenceEx>("gesture_long_press_swipe")!!.apply {
                enableIf { it["gesture_long_press"] != "left-press" }
                disabledStateSummary = getText(R.string.pref_long_press_swipe_disabled_summary)
            }

            // To reduce clutter & avoid 'UI overload', pref to invert vertical scrolling is
            // only visible when 'Scroll remote content' option is used.
            findPreference<SwitchPreference>("invert_vertical_scrolling")!!.apply {
                showIf { it.values.contains("remote-scroll") }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        }

        private fun Preference.enableIf(test: (Map<String, Any?>) -> Boolean) {
            enablementTests += this to test
            applyTests()
        }

        private fun Preference.showIf(test: (Map<String, Any?>) -> Boolean) {
            visibilityTests += this to test
            applyTests()
        }

        private fun applyTests() {
            val prefs = preferenceManager.sharedPreferences?.all ?: return
            visibilityTests.forEach { it.key.isVisible = it.value(prefs) }
            enablementTests.forEach { it.key.isEnabled = it.value(prefs) }
        }
    }

    @Keep class Server : PrefFragment(R.xml.pref_server) {
        private val authPrompt by lazy { DeviceAuthPrompt(requireActivity()) }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val savedServerLock = findPreference<SwitchPreference>("lock_saved_server")!!

            if (authPrompt.canLaunch()) {
                authPrompt.init({ savedServerLock.isChecked = !savedServerLock.isChecked }, { })
                savedServerLock.setOnPreferenceChangeListener { _, _ ->
                    authPrompt.launch(getString(R.string.title_unlock_dialog))
                    false
                }
            } else {
                savedServerLock.isEnabled = false
            }

            findPreference<Preference>("forget_known_hosts")!!.setOnPreferenceClickListener {
                ForgetKnownHostsDialog().show(childFragmentManager, "ForgetKnownHosts")
                true
            }
        }
    }

    @Keep class Tools : PrefFragment(R.xml.pref_tools)
}
