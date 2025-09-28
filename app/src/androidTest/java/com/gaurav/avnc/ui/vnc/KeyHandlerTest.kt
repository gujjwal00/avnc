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
import io.mockk.Called
import io.mockk.MockKVerificationScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P) // Mocking final methods is not possible in earlier versions
class KeyHandlerTest {

    private lateinit var keyHandler: KeyHandler
    private lateinit var prefs: AppPreferences
    private lateinit var mockDispatcher: Dispatcher
    private val kcm by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    @Before
    fun before() {
        instrumentation.runOnMainSync { prefs = AppPreferences(targetContext) }
        mockDispatcher = mockk()
        every { mockDispatcher.onXKey(any(), any(), any()) } returns true
        keyHandler = KeyHandler(mockDispatcher, prefs)
    }

    /**
     * [KeyEvent] uses character-maps to retrieve the unicode character for each event.
     * We cannot programmatically change the character-map, so we use this custom event to
     * override the unicode character.
     */
    private class TestKeyEvent(action: Int, keyCode: Int, private val uChar: Int? = null) : KeyEvent(action, keyCode) {
        override fun getUnicodeChar() = uChar ?: super.getUnicodeChar()
        override fun getUnicodeChar(metaState: Int) = uChar ?: super.getUnicodeChar(metaState)
    }

    private fun sendDown(keyCode: Int, uChar: Int? = null) {
        keyHandler.onKeyEvent(TestKeyEvent(KeyEvent.ACTION_DOWN, keyCode, uChar))
    }

    private fun sendUp(keyCode: Int, uChar: Int? = null) {
        keyHandler.onKeyEvent(TestKeyEvent(KeyEvent.ACTION_UP, keyCode, uChar))
    }

    private fun sendKey(keyCode: Int, uChar: Int? = null) {
        sendDown(keyCode, uChar)
        sendUp(keyCode, uChar)
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

    /**
     * Verification
     */
    private fun verifySentKeys(block: MockKVerificationScope.() -> Unit) = verifySequence { block() }
    private fun verifyNoneSent() = verifySentKeys { mockDispatcher wasNot Called }
    private fun MockKVerificationScope.dn(keySym: Int, xtCode: Int = any()) = mockDispatcher.onXKey(keySym, xtCode, true)
    private fun MockKVerificationScope.dn(char: Char) = dn(char.code)
    private fun MockKVerificationScope.up(keySym: Int, xtCode: Int = any()) = mockDispatcher.onXKey(keySym, xtCode, false)
    private fun MockKVerificationScope.up(char: Char) = up(char.code)


    /**************************************************************************/
    @Test
    fun simpleChar() {
        sendKey(KeyEvent.KEYCODE_A)
        verifySentKeys {
            dn('a')
            up('a')
        }
    }

    @Test
    fun charWithShift() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON)

        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn('A')
            up('A')
        }

    }

    @Test
    fun charWithShiftCtrl() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDown(KeyEvent.KEYCODE_CTRL_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)

        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn(XKeySym.XK_Control_L)
            dn('A')
            up('A')
        }
    }

    @Test
    fun charWithShiftCtrlAlt() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)

        verifySentKeys {
            // "Fake" meta key events should be generated to workaround
            // buggy keyboards which don't send meta key presses properly
            dn(XKeySym.XK_Alt_L)
            dn(XKeySym.XK_Control_L)
            dn(XKeySym.XK_Shift_L)
            dn('A')

            up(XKeySym.XK_Shift_L)
            up(XKeySym.XK_Control_L)
            up(XKeySym.XK_Alt_L)
            up('A')
        }
    }

    @Test
    fun altShift6() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendDown(KeyEvent.KEYCODE_ALT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_6, KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON)

        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn(XKeySym.XK_Alt_L)
            dn('^')
            up('^')
        }
    }

    @Test
    fun charWithCapslock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON)
        verifySentKeys {
            dn('A')
            up('A')
        }
    }


    @Test
    @SdkSuppress(minSdkVersion = 31) // CapsLock + Shift is broken on older Android versions
    fun charWithCapslockShift() {
        sendDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_SHIFT_ON)
        sendUp(KeyEvent.KEYCODE_SHIFT_LEFT)

        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn('a')
            up('a')
            up(XKeySym.XK_Shift_L)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun charWithCapslockShift_withMissingShiftPress() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_SHIFT_ON)
        verifySentKeys {
            dn(XKeySym.XK_Shift_L) // Simulated
            dn('a')
            up(XKeySym.XK_Shift_L) // Simulated
            up('a')
        }
    }

    @Test
    fun charWithCapslockCtrl() {
        sendDown(KeyEvent.KEYCODE_CTRL_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_CTRL_ON)
        verifySentKeys {
            dn(XKeySym.XK_Control_L)
            dn('A')
            up('A')
        }
    }

    @Test
    fun charWithCapslockAlt() {
        sendDown(KeyEvent.KEYCODE_ALT_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CAPS_LOCK_ON or KeyEvent.META_ALT_ON)
        verifySentKeys {
            dn(XKeySym.XK_Alt_L)
            dn('A')
            up('A')
        }
    }

    @Test
    fun charWithSuper() {
        sendDown(KeyEvent.KEYCODE_META_LEFT)
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_META_ON)
        verifySentKeys {
            dn(XKeySym.XK_Super_L)
            dn('a')
            up('a')
        }
    }

    @Test
    fun multipleChars() {
        keyHandler.onKeyEvent(KeyEvent(0, "abCde", 0, 0))

        verifySentKeys {
            dn('a')
            up('a')
            dn('b')
            up('b')

            dn(XKeySym.XK_Shift_L) // Simulated
            dn('C')
            up(XKeySym.XK_Shift_L)
            up('C')

            dn('d')
            up('d')
            dn('e')
            up('e')
        }
    }

    @Test
    fun numpadWithNumlock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.META_NUM_LOCK_ON)
        verifySentKeys {
            dn('1')
            up('1')
        }
    }

    @Test
    fun numpadWithoutNumlock() {
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_1, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_5, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_9, 0)
        sendKeyWithMeta(KeyEvent.KEYCODE_NUMPAD_DOT, 0)

        //Unless NumLock is on, these should not be sent because we
        //want them to fallback to their secondary actions
        verifyNoneSent()
    }

    @Test
    fun rawKeyLeft() {
        val scLeft = 105
        val xtLeft = 203
        sendKeyWithScancode(KeyEvent.KEYCODE_DPAD_LEFT, scLeft)
        verifySentKeys {
            dn(XKeySym.XK_Left, xtLeft)
            up(XKeySym.XK_Left, xtLeft)
        }
    }

    @Test
    fun rawKeySuper() {
        val scSuper = 125
        val xtSuper = 219
        sendKeyWithScancode(KeyEvent.KEYCODE_META_LEFT, scSuper)
        verifySentKeys {
            dn(XKeySym.XK_Super_L, xtSuper)
            up(XKeySym.XK_Super_L, xtSuper)
        }
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
        sendDown(KeyEvent.KEYCODE_SPACE)
        sendDown(KeyEvent.KEYCODE_TAB)
        sendDown(KeyEvent.KEYCODE_ENTER)

        verifySentKeys {
            dn(XKeySym.XK_space)
            dn(XKeySym.XK_Tab)
            dn(XKeySym.XK_Return)
        }
    }

    @Test
    fun fakeShiftBeforeSingleVariantKeys() {
        // If KEYCODE_AT is received directly (and not Shift + 2), fake Shift should be generated
        sendDown(KeyEvent.KEYCODE_AT, '@'.code)
        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn(XKeySym.XK_at)
            up(XKeySym.XK_Shift_L)
        }
    }

    @Test
    fun fakeCtrlPress() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
        verifySentKeys {
            dn(XKeySym.XK_Control_L)
            dn(XKeySym.XK_a)
            up(XKeySym.XK_Control_L)
            up(XKeySym.XK_a)
        }
    }

    @Test
    fun fakeAltPress() {
        sendKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_ALT_ON)
        verifySentKeys {
            dn(XKeySym.XK_Alt_L)
            dn(XKeySym.XK_a)
            up(XKeySym.XK_Alt_L)
            up(XKeySym.XK_a)
        }
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
        verifyNoneSent()
    }

    @Test
    fun diacriticTest_twoAccents() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_CIRCUMFLEX)
        verifyNoneSent()
    }

    @Test
    fun diacriticTest_sameAccentTwice() {
        sendAccent(ACCENT_TILDE)
        sendAccent(ACCENT_TILDE)
        verifySentKeys {
            dn(ACCENT_TILDE + 0x1000000)
            up(ACCENT_TILDE + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_charAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A)
        verifySentKeys {
            dn('ã')
            up('ã')
        }
    }

    @Test
    fun diacriticTest_charWhileAccentIsDown() {
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(KeyEvent.KEYCODE_A)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        verifySentKeys {
            dn('ã')
            up('ã')
        }
    }

    @Test
    fun diacriticTest_interMixedUpDowns1() {
        // Here we test key up/down events for accents and characters mixed in
        // non-sequential order. This case can happen when you type quickly.

        sendDown(KeyEvent.KEYCODE_A)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(KeyEvent.KEYCODE_A)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(KeyEvent.KEYCODE_A)
        sendUp(KeyEvent.KEYCODE_A)

        verifySentKeys {
            dn('a') // First should be normal, as accent was pressed later
            up('a')
            dn('ã') // Second should be accented
            up('ã')
        }
    }

    @Test
    fun diacriticTest_interMixedUpDowns2() {
        // Another variation of intermixed up/downs

        sendDown(KeyEvent.KEYCODE_A)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(KeyEvent.KEYCODE_A)
        sendDown(KeyEvent.KEYCODE_A)
        sendUp(KeyEvent.KEYCODE_A)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)

        verifySentKeys {
            dn('a') // First should be normal, as accent was pressed later
            up('a')
            dn('ã') // Second should be accented
            up('ã')
        }
    }

    @Test
    fun diacriticTest_twiceCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A) // First one should be accented,
        sendKey(KeyEvent.KEYCODE_A) // next one should be normal

        verifySentKeys {
            dn('ã')
            up('ã')
            dn('a')
            up('a')
        }
    }

    @Test
    fun diacriticTest_capitalCharAfterAccent() {
        sendDown(KeyEvent.KEYCODE_SHIFT_RIGHT)
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A, 'A'.code)
        sendUp(KeyEvent.KEYCODE_SHIFT_RIGHT)

        verifySentKeys {
            dn(XKeySym.XK_Shift_R)
            dn('Ã')
            up('Ã')
            up(XKeySym.XK_Shift_R)
        }
    }

    @Test
    fun diacriticTest_charAfterTwoAccents() {
        sendAccent(ACCENT_CIRCUMFLEX)
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_A)
        verifySentKeys {
            dn('ẫ'.code + 0x1000000)
            up('ẫ'.code + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_charAfterTwoAccentsPressedTogether() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)

        sendKey(KeyEvent.KEYCODE_A)
        verifySentKeys {
            dn('ẫ'.code + 0x1000000)
            up('ẫ'.code + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_charAfterTwoAccentsPressedTogetherAndReleasedInReverse() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)

        sendKey(KeyEvent.KEYCODE_A)
        verifySentKeys {
            dn('ẫ'.code + 0x1000000)
            up('ẫ'.code + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_charWhileTwoAccentsAreDown() {
        sendDown(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendDown(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        sendKey(KeyEvent.KEYCODE_A)
        sendUp(0, ACCENT_CIRCUMFLEX or KeyCharacterMap.COMBINING_ACCENT)
        sendUp(0, ACCENT_TILDE or KeyCharacterMap.COMBINING_ACCENT)
        verifySentKeys {
            dn('ẫ'.code + 0x1000000)
            up('ẫ'.code + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_spaceAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SPACE)
        verifySentKeys {
            dn(ACCENT_TILDE + 0x1000000)
            up(ACCENT_TILDE + 0x1000000)
        }
    }

    @Test
    fun diacriticTest_invalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B)
        verifyNoneSent()
    }

    @Test
    fun diacriticTest_twiceInvalidCharAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_B)  // This will stop composition,
        sendKey(KeyEvent.KEYCODE_B)  // next char will be passed through
        verifySentKeys {
            dn('b')
            up('b')
        }
    }

    @Test
    fun diacriticTest_metaKeyAfterAccent() {
        sendAccent(ACCENT_TILDE)
        sendKey(KeyEvent.KEYCODE_SHIFT_RIGHT)  // Meta-keys should be passed through
        verifySentKeys {
            dn(XKeySym.XK_Shift_R)
            up(XKeySym.XK_Shift_R)
        }
    }

    @Test
    fun cCedilla() {
        kcm.getEvents(charArrayOf('ç')).forEach { keyHandler.onKeyEvent(it) }

        verifySentKeys {
            dn(XKeySym.XK_Alt_L)
            up(XKeySym.XK_Alt_L) // expected fake release of Alt
            dn('ç')
            up('ç')
            up(XKeySym.XK_Alt_L) // normal release of Alt

        }
    }

    @Test
    fun cCedillaUppercase() {
        kcm.getEvents(charArrayOf('Ç')).forEach { keyHandler.onKeyEvent(it) }

        verifySentKeys {
            dn(XKeySym.XK_Shift_L)
            dn(XKeySym.XK_Alt_L)
            up(XKeySym.XK_Alt_L) // expected fake release of Alt
            dn('Ç')
            up('Ç')
            up(XKeySym.XK_Alt_L) // normal release of Alt
            up(XKeySym.XK_Shift_L)
        }
    }

    /**************************************************************************/

    @Test
    fun keyMappingPrefs() {
        val mockPrefs = mockk<AppPreferences>()
        every { mockPrefs.input.kmLanguageSwitchToSuper } returns true
        every { mockPrefs.input.kmRightAltToSuper } returns true
        keyHandler = KeyHandler(mockDispatcher, mockPrefs)

        sendKey(KeyEvent.KEYCODE_LANGUAGE_SWITCH)
        sendKey(KeyEvent.KEYCODE_ALT_RIGHT)

        // Alt press with scan code (from hardware keyboard),  which should be cleared when remapping
        val altScanCode = 100
        keyHandler.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_RIGHT, 0, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON, 123, altScanCode))

        verifySentKeys {
            dn(XKeySym.XK_Super_L)
            up(XKeySym.XK_Super_L)

            dn(XKeySym.XK_Super_L)
            up(XKeySym.XK_Super_L)

            dn(XKeySym.XK_Super_L, 0)
        }
    }


    @Test
    fun macOSCompatibility() {
        keyHandler.enableMacOSCompatibility = true
        sendKey(KeyEvent.KEYCODE_ALT_RIGHT)
        sendKey(KeyEvent.KEYCODE_ALT_LEFT)

        // Should generate 'Meta' instead of normal 'Alt'
        verifySentKeys {
            dn(XKeySym.XK_Meta_R)
            up(XKeySym.XK_Meta_R)
            dn(XKeySym.XK_Meta_L)
            up(XKeySym.XK_Meta_L)
        }
    }
}