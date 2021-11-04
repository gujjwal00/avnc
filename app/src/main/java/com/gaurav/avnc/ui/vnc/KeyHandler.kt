/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.os.Build
import android.view.KeyEvent
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import com.gaurav.avnc.vnc.XKeySymAndroid
import com.gaurav.avnc.vnc.XKeySymAndroid.updateKeyMap
import com.gaurav.avnc.vnc.XKeySymUnicode

/**
 * Handler for key events
 *
 * Key handling in RFB protocol works on 'key symbols' instead of key-codes/scan-codes
 * which makes it dependent on keyboard layout. VNC servers implement various heuristics
 * to compensate for this & maximize portability. Our implementation is derived after
 * testing with some popular servers. It might not handle all the edge cases.
 *
 *
 * Basically, job of this class is to convert the received [KeyEvent] into a 'KeySym'.
 * That KeySym will be sent to the server.
 *
 *-      [KeyEvent]     +----------------+    KeySym     +----------------+
 *-   ----------------> |  [KeyHandler]  | ------------> |  [Dispatcher]  |
 *-                     +----------------+               +----------------+
 *
 * This class emits (conceptually) three types of key symbols:
 *
 * 1. X KeySym         - Individual symbols defined by X Windows System
 * 2. Unicode KeySym   - Unicode code points encoded as X KeySym
 * 2. Legacy X KeySym  - Old KeySyms which are now superseded by their Unicode KeySym equivalents
 *
 *
 * To decide which one to emit, we look at following things:
 *
 * a. Key code of [KeyEvent]               (may not be available, e.g. in case of [KeyEvent.ACTION_MULTIPLE])
 * b. Unicode character of [KeyEvent]      (may not be available, e.g. in case of [KeyEvent.KEYCODE_F1])
 * c. Current [compatMode]
 *
 *
 *-                                 [KeyEvent]
 *-                                     |
 *-                                     v
 *-                       +----------------------------+
 *-                       | Is Unicode Char Available? |
 *-                       +-------------+--------------+
 *-                                     |
 *-                           Yes       |      No
 *-                       +-------------+--------------+
 *-                       |                            |
 *-             +---------v----------+         +-------v-------+
 *-             |  Use Unicode Char  |         |  Use Key Code |
 *-             +---------+----------+         +-------+-------+
 *-                       |                            |
 *-             +---------v----------+                 |
 *-             |   In compat mode?  |                 |
 *-             +---------+----------+                 |
 *-                       |                            |
 *-                Yes    |    No                      |
 *-             +---------+----------+                 |
 *-             |                    |                 |
 *-             v                    v                 v
 *-     (Legacy X KeySym)    (Unicode KeySym)      (X KeySym)
 *
 * See [handleKeyEvent] as a starting point.
 *
 *
 * Reference:
 * [X Windows System Protocol](https://www.x.org/releases/X11R7.7/doc/xproto/x11protocol.html#keysym_encoding)
 *
 */
class KeyHandler(private val dispatcher: Dispatcher, private val compatMode: Boolean, prefs: AppPreferences) {

    init {
        if (prefs.input.kmLanguageSwitchToSuper) updateKeyMap(KeyEvent.KEYCODE_LANGUAGE_SWITCH, XKeySym.XK_Super_L)
        if (prefs.input.kmRightAltToSuper) updateKeyMap(KeyEvent.KEYCODE_ALT_RIGHT, XKeySym.XK_Super_L)
    }

    /**
     * Shortcut to send both up & down events. Useful for Virtual Keys.
     */
    fun onKey(keyCode: Int) {
        onKeyEvent(keyCode, true)
        onKeyEvent(keyCode, false)
    }

    fun onKeyEvent(keyCode: Int, isDown: Boolean) {
        val action = if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        onKeyEvent(KeyEvent(action, keyCode))
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        return handleKeyEvent(event)
    }


    /**
     * This will parse the [event] and call [emitForKeyEvent] appropriately.
     */
    private fun handleKeyEvent(event: KeyEvent): Boolean {

        //Deprecated action types are still received for non-ASCII characters
        @Suppress("DEPRECATION")
        when (event.action) {

            KeyEvent.ACTION_DOWN -> return emitForKeyEvent(event.keyCode, event.unicodeChar, true)
            KeyEvent.ACTION_UP -> return emitForKeyEvent(event.keyCode, event.unicodeChar, false)

            KeyEvent.ACTION_MULTIPLE -> {
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {

                    // Here, only Unicode characters are available.
                    for (uChar in toCodePoints(event.characters)) {
                        emitForKeyEvent(0, uChar, true)
                        emitForKeyEvent(0, uChar, false)
                    }

                } else {

                    // Here, only keyCode is available.
                    // According to Android docs, this case doesn't happen anymore.
                    for (i in 1..event.repeatCount) {
                        emitForKeyEvent(event.keyCode, 0, true)
                        emitForKeyEvent(event.keyCode, 0, false)
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * Emits a KeySym for given event details.
     * It will call [emitForAndroidKeyCode] or [emitForUnicodeChar] depending on arguments.
     */
    private fun emitForKeyEvent(keyCode: Int, unicodeChar: Int, isDown: Boolean): Boolean {

        // Always emit using keyCode for these because Android returns a unicodeChar
        // for these but most servers don't handle their Unicode characters.
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_TAB ->
                return emitForAndroidKeyCode(keyCode, isDown)
        }

        // We prefer to use unicodeChar even when keyCode is available because
        // most servers ignore previously sent SHIFT/CAPS_LOCK keys.
        // As Android takes meta keys into account when calculating unicodeChar,
        // it works well with these servers.

        if (unicodeChar != 0)
            return emitForUnicodeChar(unicodeChar, isDown)
        else
            return emitForAndroidKeyCode(keyCode, isDown)
    }

    /**
     * Emits X KeySym corresponding to [keyCode]
     */
    private fun emitForAndroidKeyCode(keyCode: Int, isDown: Boolean): Boolean {
        val keySym = XKeySymAndroid.getKeySymForAndroidKeyCode(keyCode)
        return emit(keySym, isDown)
    }

    /**
     * Emits either Unicode KeySym or legacy KeySym for [uChar], depending on [compatMode].
     */
    private fun emitForUnicodeChar(uChar: Int, isDown: Boolean): Boolean {
        var uKeySym = 0

        if (compatMode)
            uKeySym = XKeySymUnicode.getLegacyKeySymForUnicodeChar(uChar)

        if (uKeySym == 0)
            uKeySym = XKeySymUnicode.getKeySymForUnicodeChar(uChar)


        // If we are generating legacy KeySym and the character is uppercase,
        // we need to fake press the Shift key. Otherwise, most servers can't
        // handle them. This is just a compat shim and ideally server should
        // support Unicode KeySym.
        val shouldFakeShift = uKeySym in 0x100..0xfffe && uChar.toChar().isUpperCase()
        if (shouldFakeShift)
            emitForAndroidKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, true)

        emit(uKeySym, isDown)

        if (shouldFakeShift)
            emitForAndroidKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT, false)

        return true
    }

    /**
     * Sends given [keySym] to [dispatcher].
     */
    private fun emit(keySym: Int, isDown: Boolean): Boolean {
        if (keySym == 0)
            return false

        dispatcher.onXKeySym(keySym, isDown)
        return true
    }

    /************************************************************************************
     * Convert String to Array of Unicode code-points
     ***********************************************************************************/

    private val cpCache = intArrayOf(0)

    private fun toCodePoints(string: String): IntArray {
        //Handle simple & most probable case
        if (string.length == 1)
            return cpCache.apply { this[0] = string[0].toInt() }

        if (Build.VERSION.SDK_INT >= 24)
            return string.codePoints().toArray()

        //Otherwise, do simple conversion (will be incorrect non-MBP code points)
        return string.map { it.toInt() }.toIntArray()
    }
}