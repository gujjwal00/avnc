/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.os.Bundle
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.gaurav.avnc.R

class PrefsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, Main())
                    .commit()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    /**
     * Starts new fragment corresponding to given [pref].
     */
    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment)

        supportFragmentManager.beginTransaction()
                .replace(R.id.settings, fragment)
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

    @Keep class Appearance : PrefFragment(R.xml.pref_appearance) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val theme = findPreference<ListPreference>("theme")!!

            theme.setOnPreferenceChangeListener { _, newValue ->
                val newMode = when (newValue) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                AppCompatDelegate.setDefaultNightMode(newMode)

                true //Allow new value to be persisted
            }
        }
    }

    @Keep class Display : PrefFragment(R.xml.pref_display)
    @Keep class Input : PrefFragment(R.xml.pref_input)
    @Keep class Server : PrefFragment(R.xml.pref_server)
    @Keep class Experimental : PrefFragment(R.xml.pref_experimental)
}
