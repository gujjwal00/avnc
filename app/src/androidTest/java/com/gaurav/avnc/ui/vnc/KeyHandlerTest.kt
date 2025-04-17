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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var dispatchedKeyDowns: ArrayList<Int>
    private lateinit var dispatchedKeyUps: ArrayList<Int>
    private lateinit var dispatchedXTDowns: ArrayList<Int>
    private lateinit var dispatchedXTUps: ArrayList<Int>

    @Before
    fun before() {
        instrumentation.runOnMainSync { prefs = AppPreferences(targetContext) }

        dispatchedKeyDowns = arrayListOf()
        dispatchedKeyUps = arrayListOf()
        dispatchedXTDowns = arrayListOf()
        dispatchedXTUps = arrayListOf()
        mockDispatcher = mockk()
        every { mockDispatcher.onXKey(any(), any(), true) } answers {
            dispatchedKeyDowns.add(firstArg())
            dispatchedXTDowns.add(secondArg())
            true
        }
        every { mockDispatcher.onXKey(any(), any(), false) } answers {
            dispatchedKeyUps.add(firstArg())
            dispatchedXTUps.add(secondArg())
            true
        }
        keyHandler = KeyHandler(mockDispatcher, prefs)
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
        sendKey(keyCode, uChar.code)
    }

    private fun sendAccent(accent: Int) {
        val uChar = (accent or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(0, uChar)
    }


    private fun sendKeyWithMeta(keyCode: Int, metaState: Int) {
        keyHandler.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        keyHandler.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun sendKeyWithScancode(keyCode: Int, scancode: Int) {
        keyHandler.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, scancode))
        keyHandler.onKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, scancode))
    }


    /**************************************************************************/
    @Test
    fun simpleChar() {
        keyHandler.onKey(KeyEvent.KEYCODE_A)
        assertEquals('a'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun charWithShift() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON)
        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0])
        assertEquals('A'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun charWithShiftCtrl() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDown(KeyEvent.KEYCODE_CTRL_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)

        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0])
        assertEquals(XKeySym.XK_Control_L, dispatchedKeyDowns[1])
        assertEquals('A'.code, dispatchedKeyDowns[2])
    }

    @Test
    fun charWithShiftCtrlAlt() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        assertEquals('A'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun charWithCapslock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON)
        assertEquals('A'.code, dispatchedKeyDowns.firstOrNull())
    }


    @Test
    @SdkSuppress(minSdkVersion = 31) // CapsLock + Shift is broken on older Android versions
    fun charWithCapslockShift() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_SHIFT_ON)
        sendUp(KeyEvent.KEYCODE_SHIFT_LEFT)

        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0])
        assertEquals('a'.code, dispatchedKeyDowns[1])
        dispatchedKeyUps.reverse()
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun charWithCapslockShift_withMissingShiftPress() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_SHIFT_ON)
        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0]) // This should be fake-pressed by KeyHandler
        assertEquals('a'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun charWithCapslockCtrl() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_CTRL_ON)
        assertEquals('A'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun charWithCapslockAlt() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_ALT_ON)
        assertEquals('A'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun charWithSuper() {
        sendKeyWithMeta(KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_ON)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_META_ON)
        assertEquals(XKeySym.XK_Super_L, dispatchedKeyDowns[0])
        assertEquals('a'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun multipleChars() {
        keyHandler.onKeyEvent(KeyEvent(0, "abCde", 0, 0))

        assertEquals('a'.code, dispatchedKeyDowns[0])
        assertEquals('b'.code, dispatchedKeyDowns[1])
        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[2]) // Fake Shift press is expected
        assertEquals('C'.code, dispatchedKeyDowns[3])
        assertEquals('d'.code, dispatchedKeyDowns[4])
        assertEquals('e'.code, dispatchedKeyDowns[5])
    }

    @Test
    fun numpadWithNumlock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.META_NUM_LOCK_ON)
        assertEquals('1'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun numpadWithoutNumlock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_1, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_5, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_9, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_DOT, 0)

        //Unless NumLock is on, these should not be sent because we
        //want them to fallback to their secondary actions
        assertTrue(dispatchedKeyDowns.isEmpty())
    }

    @Test
    fun rawKeyLeft() {
        val scLeft = 105
        val xtLeft = 203
        sendKeyWithScancode(KeyEvent.KEYCODE_DPAD_LEFT, scLeft)
        assertEquals(xtLeft, dispatchedXTDowns.first())
        assertEquals(xtLeft, dispatchedXTUps.first())
    }

    @Test
    fun rawKeySuper() {
        val scLeft = 125
        val xtLeft = 219
        sendKeyWithScancode(KeyEvent.KEYCODE_META_LEFT, scLeft)
        assertEquals(xtLeft, dispatchedXTDowns.first())
        assertEquals(xtLeft, dispatchedXTUps.first())
    }

    @Test
    fun unhandledKeys() {
        // These keys should not be consumed by KeyHandler
        // because these are intended to be handled by Android
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CALL)))
    }

    @Test
    fun unmappedKeys() {
        // Keys without X KeySym should not be consumed by KeyHandler
        // Few examples of unmapped keys:
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TV)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FUNCTION)))
        assertFalse(keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_1)))
    }

    @Test
    fun specialKeysWithPreferredKeyCode() {
        // Some keys for which Android keyCode should be used instead of their Unicode character
        sendDown(KeyEvent.KEYCODE_SPACE, ' '.code)
        sendDown(KeyEvent.KEYCODE_TAB, '\t'.code)
        sendDown(KeyEvent.KEYCODE_ENTER, '\n'.code)

        assertEquals(XKeySym.XK_space, dispatchedKeyDowns[0])
        assertEquals(XKeySym.XK_Tab, dispatchedKeyDowns[1])
        assertEquals(XKeySym.XK_Return, dispatchedKeyDowns[2])
    }

    @Test
    fun fakeShiftBeforeSingleVariantKeys() {
        // If KEYCODE_AT is received directly (and not Shift + 2), fake Shift should be generated
        sendDown(KeyEvent.KEYCODE_AT, '@'.code)

        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0])
        assertEquals(XKeySym.XK_at, dispatchedKeyDowns[1])
    }

    @Test
    fun observerTest() {
        var observedEvent: KeyEvent? = null
        keyHandler.processedEventObserver = { observedEvent = it }
        keyHandler.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1))

        assertNotNull(observedEvent)
        assertEquals(KeyEvent.KEYCODE_F1, observedEvent?.keyCode)
    }

    /**************************************************************************/
    private val ACCENT_TILDE = 0x02DC
    private val ACCENT_CIRCUMFLEX = 0x02C6

    @Test
    fun diacriticTest_accent() {
        sendAccent(ACCENT_TILDE)
        assertTrue(dispatchedKeyDowns.isEmpty())
    }

    @Test
    fun diacriticTest_twoAccents() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_CIRCUMFLEX)
        assertTrue(dispatchedKeyDowns.isEmpty())
    }

    @Test
    fun diacriticTest_sameAccentTwice() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_TILDE)
        assertEquals(ACCENT_TILDE + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_charAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ã'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_charWhileAccentIsDown() {
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        assertEquals('ã'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_interMixedUpDowns1() {
        // Here we test key up/down events for accents and characters mixed in
        // non-sequential order. This case can happen when you type quickly.

        sendDown(KeyEvent.KEYCODE_A, 'a'.code)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(KeyEvent.KEYCODE_A, 'a'.code)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(KeyEvent.KEYCODE_A, 'a'.code)
        sendUp(KeyEvent.KEYCODE_A, 'a'.code)

        assertEquals('a'.code, dispatchedKeyDowns[0]) // First should be normal, as accent was pressed later
        assertEquals('ã'.code, dispatchedKeyDowns[1]) // Second should be accented
    }

    @Test
    fun diacriticTest_interMixedUpDowns2() {
        // Another variation of intermixed up/downs

        sendDown(KeyEvent.KEYCODE_A, 'a'.code)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(KeyEvent.KEYCODE_A, 'a'.code)
        sendDown(KeyEvent.KEYCODE_A, 'a'.code)
        sendUp(KeyEvent.KEYCODE_A, 'a'.code)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)

        assertEquals('a'.code, dispatchedKeyDowns[0])
        assertEquals('ã'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun diacriticTest_twiceCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a') // First one should be accented,
        sendKey(KeyEvent.KEYCODE_A, 'a') // next one should be normal
        assertEquals('ã'.code, dispatchedKeyDowns[0])
        assertEquals('a'.code, dispatchedKeyDowns[1])
    }

    @Test
    fun diacriticTest_capitalCharAfterAccent() {
        sendDown(KeyEvent.KEYCODE_SHIFT_RIGHT)
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'A')
        sendUp(KeyEvent.KEYCODE_SHIFT_RIGHT)

        assertEquals(XKeySym.XK_Shift_R, dispatchedKeyDowns[0])
        assertEquals('Ã'.code, dispatchedKeyDowns[1])
        dispatchedKeyUps.reverse()
    }

    @Test
    fun diacriticTest_charAfterTwoAccents() {
        sendAccent(ACCENT_CIRCUMFLEX)
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ẫ'.code + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_charAfterTwoAccentsPressedTogether() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)

        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ẫ'.code + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_charAfterTwoAccentsPressedTogetherAndReleasedInReverse() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)

        sendKey(KeyEvent.KEYCODE_A, 'a')
        assertEquals('ẫ'.code + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_charWhileTwoAccentsAreDown() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(KeyEvent.KEYCODE_A, 'a')
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        assertEquals('ẫ'.code + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_spaceAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SPACE, ' ')
        assertEquals(ACCENT_TILDE + 0x1000000, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun diacriticTest_invalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B, 'b')
        assertTrue(dispatchedKeyDowns.isEmpty())
    }

    @Test
    fun diacriticTest_twiceInvalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B, 'b')  // This will stop composition,
        sendKey(KeyEvent.KEYCODE_B, 'b')  // next char will be passed through
        assertEquals(1, dispatchedKeyDowns.size)
        assertEquals('b'.code, dispatchedKeyDowns.first())
    }

    @Test
    fun diacriticTest_metaKeyAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SHIFT_RIGHT, 0)  // Meta-keys should be passed through
        assertEquals(XKeySym.XK_Shift_R, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun cCedilla() {
        keyHandler.onKeyEvent(KeyEvent(0, "ç", 0, 0))
        assertEquals('ç'.code, dispatchedKeyDowns.firstOrNull())
    }

    @Test
    fun cCedillaUppercase() {
        keyHandler.onKeyEvent(KeyEvent(0, "Ç", 0, 0))
        assertEquals(XKeySym.XK_Shift_L, dispatchedKeyDowns[0])
        assertEquals('Ç'.code, dispatchedKeyDowns[1])
    }

    /**************************************************************************/

    @Test
    fun keyMappingPrefs() {
        val mockPrefs = mockk<AppPreferences>()
        every { mockPrefs.input.kmLanguageSwitchToSuper } returns true
        every { mockPrefs.input.kmRightAltToSuper } returns true
        val keyHandler = KeyHandler(mockDispatcher, mockPrefs)

        keyHandler.onKey(KeyEvent.KEYCODE_LANGUAGE_SWITCH)
        keyHandler.onKey(KeyEvent.KEYCODE_ALT_RIGHT)

        assertEquals(XKeySym.XK_Super_L, dispatchedKeyDowns[0])
        assertEquals(XKeySym.XK_Super_L, dispatchedKeyDowns[1])
    }


    @Test
    fun macOSCompatibility() {
        keyHandler.enableMacOSCompatibility = true
        keyHandler.onKey(KeyEvent.KEYCODE_ALT_RIGHT)
        keyHandler.onKey(KeyEvent.KEYCODE_ALT_LEFT)

        // Should generate 'Meta' instead of normal 'Alt'
        assertEquals(XKeySym.XK_Meta_R, dispatchedKeyDowns[0])
        assertEquals(XKeySym.XK_Meta_L, dispatchedKeyDowns[1])
    }
}