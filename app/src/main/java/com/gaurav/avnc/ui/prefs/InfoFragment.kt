/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.os.Bundle
import android.view.*
import androidx.annotation.Keep
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentInfoBinding
import com.gaurav.avnc.util.Debugging
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Simple fragment which shows different types of info in scrollable text view.
 * It also allows copying the value to clipboard.
 */
@Keep
abstract class InfoFragment : Fragment(), MenuProvider {

    var text = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        val binding = FragmentInfoBinding.inflate(inflater, container, false)

        lifecycleScope.launchWhenCreated {
            withContext(Dispatchers.IO) {
                text = getInfo()
            }

            binding.text.text = text
            delay(100)
            binding.vScroll.fullScroll(View.FOCUS_DOWN)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getTitle()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menu.add(getString(android.R.string.copy))
                .setOnMenuItemClickListener { copyToClipboard(); true }
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    //Click handlers are directly attached to menu items
    override fun onMenuItemSelected(menuItem: MenuItem) = false

    private fun copyToClipboard() {
        activityViewModels<PrefsViewModel>().value.setClipboardText(text)
        Snackbar.make(requireView(), R.string.msg_copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
    }

    abstract fun getInfo(): String
    abstract fun getTitle(): String
}


@Keep
class LogsFragment : InfoFragment() {
    override fun getInfo() = Debugging.logcat()
    override fun getTitle() = getString(R.string.pref_logs)

    /**
     * In addition to Copy, users can Clear the logs
     */
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateMenu(menu, menuInflater)
        menu.add(getString(R.string.title_clear)).setOnMenuItemClickListener {
            Debugging.clearLogs()
            Snackbar.make(requireView(), "Cleared!", Snackbar.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            true
        }
    }
}

@Keep
class KeyCodeMapFragment : InfoFragment() {
    override fun getInfo() = Debugging.keyCodeMap()
    override fun getTitle() = getString(R.string.pref_keycode_map)
}