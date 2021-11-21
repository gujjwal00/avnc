/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.view.View
import com.gaurav.avnc.databinding.VirtualKeysBinding

/**
 * Virtual keys allow the user to input keys which are not normally found on
 * keyboards but can be useful for controlling remote server.
 *
 * This class manages the inflation & visibility of virtual keys.
 */
class VirtualKeys(activity: VncActivity) {

    private val pref = activity.viewModel.pref.input
    private val keyHandler = activity.keyHandler
    private val stub = activity.binding.virtualKeysStub
    private var openedWithKb = false

    fun show() {
        init()
        stub.root?.visibility = View.VISIBLE
    }

    fun hide() {
        stub.root?.visibility = View.GONE
        openedWithKb = false //Reset flag
    }

    fun onKeyboardOpen() {
        if (pref.vkOpenWithKeyboard && stub.root?.visibility != View.VISIBLE) {
            show()
            openedWithKb = true
        }
    }

    fun onKeyboardClose() {
        if (openedWithKb) {
            hide()
            openedWithKb = false
        }
    }

    fun releaseMetaKeys() {
        val binding = stub.binding as? VirtualKeysBinding
        binding?.apply {
            shiftBtn.isChecked = false
            ctrlBtn.isChecked = false
            altBtn.isChecked = false
        }
    }

    private fun init() {
        if (stub.isInflated)
            return

        stub.viewStub?.inflate()
        val binding = stub.binding as VirtualKeysBinding
        binding.h = keyHandler
        binding.showAll = pref.vkShowAll
        binding.hideBtn.setOnClickListener { hide() }
    }
}