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

        doSendKey(event)
        return true
    }

    //Keys ignored by FrameView
    private fun isIgnoredKey(key: Int): Boolean {
        return key == KeyEvent.KEYCODE_BACK ||
                key == KeyEvent.KEYCODE_MENU ||
                key == KeyEvent.KEYCODE_HOME
    }


    /**
     * Key handling in RFB protocol is messed up. It works on 'key symbols' instead of
     * key-codes/scan-codes which makes it dependent on keyboard layout. VNC servers
     * implement various heuristics to compensate for this & maximize portability.
     *
     * Then there is the issue of Unicode support.
     *
     * Our implementation is derived after testing with some popular servers. It is not
     * perfect and does not handle all of the edge cases but is a good enough start.
     *
     * We separate key events in two categories:
     *
     *   With unicode char: When android tells us that there is Unicode character available
     *                      for the event, we send that directly. This works well with servers
     *                      which ignore the state of Shift key.
     *
     *   Without unicode char: In this case we use key code. But before sending, they
     *                      are translated to X Key-Symbols in native code.
     *
     * Note: [KeyEvent.KEYCODE_ENTER] is treated differently because Android returns a
     *       Unicode symbol for it.
     */
    @Suppress("DEPRECATION") //Even though some events are deprecated, we still receive them in corner cases
    private fun doSendKey(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.unicodeChar != 0 && event.keyCode != KeyEvent.KEYCODE_ENTER)
                    dispatcher.onKeyDown(event.unicodeChar, false)
                else
                    dispatcher.onKeyDown(event.keyCode, true)
            }

            KeyEvent.ACTION_UP -> {
                if (event.unicodeChar != 0 && event.keyCode != KeyEvent.KEYCODE_ENTER)
                    dispatcher.onKeyUp(event.unicodeChar, false)
                else
                    dispatcher.onKeyUp(event.keyCode, true)
            }

            KeyEvent.ACTION_MULTIPLE -> {
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    for (c in event.characters) {
                        dispatcher.onKeyDown(c.toInt(), false)
                        dispatcher.onKeyUp(c.toInt(), false)
                    }
                } else { //Doesn't really happens anymore.
                    for (i in 1..event.repeatCount) {
                        dispatcher.onKeyDown(event.keyCode, false)
                        dispatcher.onKeyUp(event.keyCode, false)
                    }
                }
            }
        }
    }
}