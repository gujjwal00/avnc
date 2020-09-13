/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection

/**
 * Customized input connection used by [FrameView].
 *
 * It allows us to do two things:
 *
 *  1. [FrameView] can pretend to be an editor which allows us to customize IMEs.
 *     (ex: disabling fullscreen edit mode)
 *
 *  2. We can avoid receiving key events which are not intended for [FrameView].
 */
class FrameInputConnection(private val dispatcher: Dispatcher, target: View) : BaseInputConnection(target, false) {

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (isIgnoredKey(event.keyCode))
            return super.sendKeyEvent(event)

        dispatcher.onKeyEvent(event)
        return true
    }

    //Keys ignored by FrameView
    private fun isIgnoredKey(key: Int): Boolean {
        return key == KeyEvent.KEYCODE_BACK ||
                key == KeyEvent.KEYCODE_MENU ||
                key == KeyEvent.KEYCODE_HOME
    }
}