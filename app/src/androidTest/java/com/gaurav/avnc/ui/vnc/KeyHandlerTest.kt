/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.os.Build
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.XKeySym
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P) // Mocking final methods is not possible in earlier versions
class KeyHandlerTest {

    private lateinit var keyHandler: KeyHandler
    private lateinit var prefs: AppPreferences
    private lateinit var mockDispatcher: Dispatcher
    private lateinit var dispatchedKeys: ArrayList<Int>

    @Before
    fun before() {
        instrumentation.runOnMainSync { prefs = AppPreferences(targetContext) }

        dispatchedKeys = arrayListOf()
        mockDispatcher = mockk()
        every { mockDispatcher.onXKeySym(any(), true) } answers { dispatchedKeys.add(firstArg()) }
        every { mockDispatcher.onXKeySym(any(), false) } answers {}

        keyHandler = KeyHandler(mockDispatcher, true, prefs)
    }

    /**
     * [KeyEvent] uses character-maps to retrieve the unicode character for each event.
     * We cannot programmatically change the character-map, so we use this custom event.
     */
    private class TestKeyEvent(action: Int, keyCode: Int, private val uChar: Int) : KeyEvent(action, keyCode) {
        override fun getUnicodeChar() = uChar
        override fun getUnicodeChar(metaState: Int) = uChar
    }

    private fun sendDown(keyCode: Int, uChar: Int = 0) {
        keyHandler.onKeyEvent(TestKeyEvent(KeyEvent.ACTION_DOWN, keyCode, uChar))
    }

    private fun sendUp(keyCode: Int, uChar: Int = 0) {
        keyHandler.onKeyEvent(TestKeyEvent(KeyEvent.ACTION_UP, keyCode, uChar))
    }

    private fun sendKey(keyCode: Int, uChar: Int) {
        sendDown(keyCode, uChar)
        sendUp(keyCode, uChar)
    }

    private fun sendKey(keyCode: Int, uChar: Char) {
        sendKey(keyCode, uChar.toInt())
    }

    private fun sendAccent(accent: Int) {
        val uChar = (accent or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(0, uChar)
    }


    /**************************************************************************/
    private val ACCENT_TILDE = 0x02DC
    private val ACCENT_CIRCUMFLEX = 0x02C6

    @Test
    fun diacriticTest_accent() {
        sendAccent(ACCENT_TILDE)
        assertTrue(dispatchedKeys.isEmpty())
    }

    @Test
    fun diacriticTest_twoAccents() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_CIRCUMFLEX)
        assertTrue(dispatchedKeys.isEmpty())
    }

    @Test
    fun diacriticTest_sameAccentTwice() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_TILDE)
        assertEquals(ACCENT_TILDE + 0x1000000, dispatchedKeys.firstOrNull())
    }

    @Test
    fun diacriticTest_charAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ã'.toInt(), dispatchedKeys.firstOrNull())
    }

    @Test
    fun diacriticTest_twiceCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a') // First one should be accented,
        sendKey(KeyEvent.KEYCODE_A, 'a') // next one should be normal
        assertEquals('ã'.toInt(), dispatchedKeys[0])
        assertEquals('a'.toInt(), dispatchedKeys[1])
    }

    @Test
    fun diacriticTest_capitalCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'A')
        assertEquals('Ã'.toInt(), dispatchedKeys.firstOrNull())
    }

    @Test
    fun diacriticTest_charAfterTwoAccents() {
        sendAccent(ACCENT_CIRCUMFLEX)
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ẫ'.toInt() + 0x1000000, dispatchedKeys.firstOrNull())
    }

    @Test
    fun diacriticTest_spaceAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SPACE, ' ')
        assertEquals(ACCENT_TILDE + 0x1000000, dispatchedKeys.firstOrNull())
    }

    @Test
    fun diacriticTest_invalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B, 'b')
        assertTrue(dispatchedKeys.isEmpty())
    }

    @Test
    fun diacriticTest_twiceInvalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B, 'b')  // This will stop composition,
        sendKey(KeyEvent.KEYCODE_B, 'b')  // next char will be passed through
        assertEquals(1, dispatchedKeys.size)
        assertEquals('b'.toInt(), dispatchedKeys.first())
    }

    @Test
    fun diacriticTest_metaKeyAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SHIFT_RIGHT, 0)  // Meta-keys should be passed through
        assertEquals(XKeySym.XK_Shift_R, dispatchedKeys.firstOrNull())
    }
}