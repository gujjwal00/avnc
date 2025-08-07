/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.os.Build
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import com.gaurav.avnc.vnc.XKeySymAndroid
import com.gaurav.avnc.vnc.XKeySymUnicode
import com.gaurav.avnc.vnc.XTKeyCode

/**
 * Handler for key events
 *
 * Key handling in RFB protocol works on 'key symbols' instead of key-codes/scan-codes
 * which makes it dependent on keyboard layout. VNC servers implement various heuristics
 * to compensate for this & maximize portability. Our implementation is derived after
 * testing with some popular servers. It might not handle all the edge cases.
 *
 * There is an extension to RFB protocol (ExtendedKeyEvent) implemented by some servers.
 * It includes support for sending XT keycodes along with key symbol. This extension
 * greatly reduces the key handling complexity. Unfortunately, as soft keyboards are
 * more common on Android, most [KeyEvent]s don't provide raw scan codes.
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
 * 3. Legacy X KeySym  - Old KeySyms which are now superseded by their Unicode KeySym equivalents
 *
 *
 * To decide which one to emit, we look at following things:
 *
 * a. Key code of [KeyEvent]               (may not be available, e.g. in case of [KeyEvent.ACTION_MULTIPLE])
 * b. Unicode character of [KeyEvent]      (may not be available, e.g. in case of [KeyEvent.KEYCODE_F1])
 * c. Current [emitLegacyKeysym]
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
class KeyHandler(private val dispatcher: Dispatcher, prefs: AppPreferences) {

    var processedEventObserver: ((KeyEvent) -> Unit)? = null
    var enableMacOSCompatibility = false
    var emitLegacyKeysym = true
    var vkMetaState = 0
    private var hasSentShiftDown = false
    private val inputPref = prefs.input
    private val kcm by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    /**
     * Shortcut to send both up & down events
     */
    fun onKey(keyCode: Int) {
        onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (shouldIgnoreEvent(event))
            return false

        val handled = handleKeyEvent(event)

        postProcessEvent(event)
        return handled
    }

    /************************************************************************************
     * Event Handling
     ***********************************************************************************/
    /**
     * Each incoming [KeyEvent] is broken down into one or more [InEvent]s.
     * This representation simplifies filtering & mapping of events.
     */
    data class InEvent(
            var isDown: Boolean,
            var keyCode: Int = 0,
            var uChar: Int = 0,
            var scanCode: Int = 0
    )

    /**
     * [OutEvent] represents a key event in terms of RFB protocol.
     * Unless filtered, each [InEvent] maps to an [OutEvent].
     */
    data class OutEvent(
            var isDown: Boolean,
            var keySym: Int = 0,
            var xtCode: Int = 0
    )

    /**
     * Event processing model
     */
    data class EventModel(
            val source: KeyEvent,
            val inEvents: ArrayList<InEvent> = arrayListOf(),
            val outEvents: ArrayList<OutEvent> = arrayListOf()
    )

    /**
     * Parses given [event] and sends corresponding key events to server.
     *
     * Returns true if sent successfully.
     *         false if not sent. Can happen if client is disconnected,
     *         or if event could not be mapped to a suitable XKeySym
     */
    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val model = EventModel(event)

        generateInEvents(model)
        remapInEvents(model)
        composeDiacritics(model)
        generateFakeShifts(model)
        releaseSoftAlt(model)
        generateOutEvents(model)
        remapOutEvents(model)

        return emit(model)
    }

    private fun generateInEvents(model: EventModel) {
        val event = model.source

        //Deprecated action types are still received for non-ASCII characters
        @Suppress("DEPRECATION")
        when (event.action) {

            KeyEvent.ACTION_DOWN -> model.inEvents += InEvent(true, event.keyCode, getUnicodeChar(event), event.scanCode)
            KeyEvent.ACTION_UP -> model.inEvents += InEvent(false, event.keyCode, getUnicodeChar(event), event.scanCode)

            KeyEvent.ACTION_MULTIPLE -> {
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {

                    // Here, only Unicode characters are available.
                    forEachCodePointOf(event.characters) {
                        model.inEvents += InEvent(true, uChar = it)
                        model.inEvents += InEvent(false, uChar = it)
                    }

                } else {

                    // Here, only keyCode is available.
                    // According to Android docs, this case doesn't happen anymore.
                    repeat(event.repeatCount) {
                        model.inEvents += InEvent(true, event.keyCode)
                        model.inEvents += InEvent(false, event.keyCode)
                    }
                }
            }
        }
    }

    private fun generateOutEvents(model: EventModel) {
        model.inEvents.forEach { event ->
            var keySym = 0
            val xtCode = if (event.scanCode == 0) 0 else XTKeyCode.fromAndroidScancode(event.scanCode)

            // We prefer to use unicodeChar even when keyCode is available because
            // most servers ignore previously sent SHIFT/CAPS_LOCK keys.
            // As Android takes meta keys into account when calculating unicodeChar,
            // it works well with these servers.
            var useUChar = (event.uChar != 0)

            // Always emit using keyCode for these because Android returns a Unicode char
            // for these but most servers don't handle their Unicode characters.
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_TAB ->
                    useUChar = false
            }

            if (useUChar) {
                if (emitLegacyKeysym)
                    keySym = XKeySymUnicode.getLegacyKeySymForUnicodeChar(event.uChar)

                if (keySym == 0)
                    keySym = XKeySymUnicode.getKeySymForUnicodeChar(event.uChar)
            } else {
                keySym = XKeySymAndroid.getKeySymForAndroidKeyCode(event.keyCode)
            }

            model.outEvents += OutEvent(event.isDown, keySym, xtCode)
        }
    }

    /**
     * Diacritics (Accents) Support
     *
     * Instead of sending diacritics directly to server, we handle their composition here.
     * This is done because:
     *
     * - Android does not report real 'combining' accents to us. Instead we get the
     *   corresponding 'printing' characters, with the `COMBINING_ACCENT` flag set.
     *   See the source code of [KeyCharacterMap] for more details.
     *
     * - Although most servers don't support diacritics directly, some of them can
     *   handle the final composed characters (e.g. TightVNC).
     *
     *
     * Behaviour:
     *
     * - Until an accent is received, all events are ignored by [composeDiacritics].
     * - When first accent is received, we start tracking by adding it to [accentSequence].
     * - When next key is received (can be another accent), we add it to [accentSequence].
     * - Then we try to compose a printable character from [accentSequence], and if successful,
     *   the composed character is sent to the server.
     * - If composition was successful, or we received non-accent key, we stop tracking
     *   by clearing [accentSequence].
     *
     */
    private var accentSequence = ArrayList<Int>()

    private fun composeDiacritics(model: EventModel) {
        var i = 0
        while (i < model.inEvents.size) {

            val (isDown, keyCode, uChar) = model.inEvents[i]
            val isUp = !isDown
            val isAccent = uChar and KeyCharacterMap.COMBINING_ACCENT != 0
            val maskedChar = uChar and KeyCharacterMap.COMBINING_ACCENT_MASK

            if ((!isAccent && accentSequence.size == 0) ||  // No tracking yet (most common case)
                (!isAccent && isUp && !accentSequence.contains(maskedChar)) || // Spurious key-ups
                (KeyEvent.isModifierKey(keyCode))) { // Modifier keys are passed-on to the server
                ++i
                continue
            }

            // Consume this event. When composed character is available, it will be inserted in event stream.
            // This also has the effect of "incrementing" i as later events are moved up one place
            model.inEvents.removeAt(i)

            if (isDown)
                accentSequence.add(maskedChar)

            if (accentSequence.size <= 1) // Nothing to compose yet
                continue

            var composed = accentSequence.last()
            for (j in 0 until accentSequence.lastIndex)
                composed = KeyEvent.getDeadChar(accentSequence[j], composed)

            if (composed != 0)
                model.inEvents.add(i++, InEvent(isDown, uChar = composed))

            if (isUp && (composed != 0 || !isAccent))
                accentSequence.clear()
        }
    }

    /**
     * In some cases we need to simulate Shift key presses to ensure proper handling of
     * uppercase/lowercase letters by VNC servers.
     */
    private fun generateFakeShifts(model: EventModel) {
        // Nothing to do if Shift key event is properly received, or a key is being released
        if (hasSentShiftDown || isShiftKey(model.source.keyCode) || model.source.action == KeyEvent.ACTION_UP)
            return

        // Some keyboard apps don't generate Shift events properly.
        // So we generate fake Shift press if current event says Shift is down but we have
        // not yet received a Shift ACTION_DOWN or already received a Shift ACTION_UP.
        //
        // (Yep, that's what Gboard does. For uppercase letters, it sends a Shift ACTION_DOWN,
        // then *inexplicably* a Shift ACTION_UP, and finally the character event with meta state
        // set to 'Shift pressed'. One would think at least Google won't fuck this up, but here we are)
        var wrapWithShiftKey = model.source.isShiftPressed


        // Keys like '@', '#', '%' etc. can be generated in multiple ways (e.g single KeyEvent.KEYCODE_AT
        // vs KeyEvent.KEYCODE_SHIFT + KeyEvent.KEYCODE_2). If single-keycode variant of such keys is
        // received, we need to fake the Shift press.
        //
        // Gboard sends single-keycode variant for some of these, but sends Shift+Number for others.
        // I can't find a pattern to this madness, so just check if Android would normally generate
        // a Shift press for this character.
        if (!wrapWithShiftKey && model.inEvents.size == 1) model.inEvents[0].let {
            if (it.scanCode == 0 && it.keyCode != 0 && it.uChar != 0 && !Character.isLetterOrDigit(it.uChar)) {
                kcm.getEvents(charArrayOf(it.uChar.toChar()))?.let { keyEvents ->
                    if (keyEvents.size == 4 &&  // Shift Down + Char Down + Char Up + Shift Up
                        isShiftKey(keyEvents[0].keyCode))
                        wrapWithShiftKey = true
                }
            }
        }

        if (wrapWithShiftKey) {
            model.inEvents.add(0, InEvent(true, KeyEvent.KEYCODE_SHIFT_LEFT))
            model.inEvents.add(model.inEvents.size, InEvent(false, KeyEvent.KEYCODE_SHIFT_LEFT))
            return
        }

        // Primary target here is non-ASCII uppercase characters delivered as KeyEvent.ACTION_MULTIPLE.
        // We don't usually receive Shift presses for such events in Android, but most VNC servers expect
        // a Shift key press before an uppercase letter is pressed.
        val isCapsLockOn = model.source.isCapsLockOn
        var i = 0
        while (i < model.inEvents.size) {
            val event = model.inEvents[i]
            if (event.isDown && event.uChar > 0) {
                if ((Character.isUpperCase(event.uChar) && !isCapsLockOn) ||
                    (Character.isLowerCase(event.uChar) && isCapsLockOn)) {

                    model.inEvents.add(i, InEvent(true, KeyEvent.KEYCODE_SHIFT_LEFT))
                    i += 2 // Inserted Shift event + Current event
                    model.inEvents.add(i, InEvent(false, KeyEvent.KEYCODE_SHIFT_LEFT))
                }
            }
            ++i
        }
    }

    /**
     * Some characters in Android can generate events with Alt-key combination:
     * Ç  => Alt + C  ; ß => Alt + S
     *
     * But Sending Alt press in most cases breaks typing these characters on server
     * because that's not their usual key combination. To fix this, we artificially
     * release the Alt key before sending these characters.
     */
    private fun releaseSoftAlt(model: EventModel) {
        model.source.let {
            // Only apply the workaround to events coming from software keyboards to avoid
            // interfering with shortcuts on external keyboards
            if (it.action == KeyEvent.ACTION_DOWN && it.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD &&
                it.scanCode == 0 && it.isAltPressed && it.unicodeChar.toChar() in "Ççß") {
                var keyCode = KeyEvent.KEYCODE_ALT_LEFT
                if (it.metaState and KeyEvent.META_ALT_RIGHT_ON != 0)
                    keyCode = KeyEvent.KEYCODE_ALT_RIGHT

                if (model.inEvents.isNotEmpty()) // Can be empty if InEvent was used for diacritics
                    model.inEvents.add(0, InEvent(false, keyCode))
            }
        }
    }

    private fun remapInEvents(model: EventModel) {
        // Apply user's preferences
        model.inEvents.forEach {
            if ((it.keyCode == KeyEvent.KEYCODE_LANGUAGE_SWITCH && inputPref.kmLanguageSwitchToSuper) ||
                (it.keyCode == KeyEvent.KEYCODE_ALT_RIGHT && inputPref.kmRightAltToSuper)) {
                it.keyCode = KeyEvent.KEYCODE_META_LEFT
                it.scanCode = 0
            }

            // Back key mapping doesn't affect the events generated by Back button in Navigation bar
            if (it.keyCode == KeyEvent.KEYCODE_BACK && (model.source.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY == 0)
                && inputPref.kmBackToEscape) {
                it.keyCode = KeyEvent.KEYCODE_ESCAPE
                it.scanCode = 0
            }
        }
    }

    private fun remapOutEvents(model: EventModel) {
        if (enableMacOSCompatibility) {
            model.outEvents.forEach {
                if (it.keySym == XKeySym.XK_Alt_L) it.keySym = XKeySym.XK_Meta_L
                if (it.keySym == XKeySym.XK_Alt_R) it.keySym = XKeySym.XK_Meta_R
            }
        }
    }

    private fun emit(model: EventModel): Boolean {
        var result = true
        model.outEvents.forEach {
            if (!emit(it.keySym, it.isDown, it.xtCode))
                result = false
        }
        return result
    }

    /**
     * Sends given X key to [dispatcher].
     */
    private fun emit(keySym: Int, isDown: Boolean, xtCode: Int = 0): Boolean {
        if (keySym == 0)
            return false

        return dispatcher.onXKey(keySym, xtCode, isDown)
    }

    /************************************************************************************
     * Utilities
     ***********************************************************************************/

    /**
     * Returns unicode character for given event.
     * Normally [KeyEvent.getUnicodeChar] is sufficient for our need, but sometimes
     * we have to fiddle with meta state to extract a suitable character.
     *
     * Consider Ctrl+Shift+A: [KeyEvent.getUnicodeChar] returns 0 for this case,
     * because there is no character mapping for A when Ctrl & Shift both are pressed.
     * But we want to obtain capital 'A' here, so that we can send it to server.
     * This ensures proper working of keyboard shortcuts.
     */
    private fun getUnicodeChar(event: KeyEvent): Int {
        var metaState = event.metaState or vkMetaState
        var uChar = event.getUnicodeChar(metaState)

        // Fix for Alt+Shift+6 (Android generates ACCENT_CIRCUMFLEX, but we want '^')
        if (event.keyCode == KeyEvent.KEYCODE_6 && metaState != 0 && (uChar and KeyCharacterMap.COMBINING_ACCENT) != 0)
            uChar = '^'.code

        if (uChar != 0 || metaState == 0)
            return uChar

        // Try without Alt/Ctrl
        metaState = metaState and (KeyEvent.META_ALT_MASK or KeyEvent.META_CTRL_MASK).inv()
        return event.getUnicodeChar(metaState)
    }

    private fun isShiftKey(keyCode: Int): Boolean {
        return (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
    }

    /**
     * Some cases where we want to ignore events.
     */
    private fun shouldIgnoreEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // As if our key-handling wasn't already complex enough, Android
        // decided to mess-up NumLock handling. When any numpad number-key
        // is pressed (e.g. 7) and NumLock is off, it will _still_ send
        // the number keycode (e.g. KEYCODE_NUMPAD_7) first. And if apps don't
        // handle that, it will fallback to secondary action (e.g. KEYCODE_MOVE_HOME).
        // So we have to ignore the first events when NumLock is off.
        return (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9
                || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) && !event.isNumLockOn
    }

    private fun postProcessEvent(event: KeyEvent) {
        if (isShiftKey(event.keyCode))
            hasSentShiftDown = event.action == KeyEvent.ACTION_DOWN

        processedEventObserver?.invoke(event)
    }

    /**
     * Converts [string] to Unicode code points, and calls [block] for each one.
     */
    private inline fun forEachCodePointOf(string: String, block: (Int) -> Unit) {
        if (string.length == 1) {
            // Simple & most frequent case
            block(string[0].code)
        } else if (Build.VERSION.SDK_INT >= 24) {
            for (cp in string.codePoints())
                block(cp)
        } else {
            // Fallback to simple conversion (will be incorrect for non-MBP code points)
            for (c in string)
                block(c.code)
        }
    }
}