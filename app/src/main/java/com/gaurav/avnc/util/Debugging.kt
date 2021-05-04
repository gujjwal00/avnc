/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.view.KeyEvent

/**
 * Utilities to aid in debugging
 */
object Debugging {

    /**
     * Returns logcat output.
     * Should not be called from main thread.
     */
    fun logcat(): String {
        try {
            return ProcessBuilder("logcat", "-d")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .readText()
        } catch (t: Throwable) {
            return "Error getting logs: ${t.message}"
        }
    }

    fun clearLogs() {
        try {
            ProcessBuilder("logcat", "-c").start()
        } catch (t: Throwable) {
            //Ignore
        }
    }

    /**
     * This returns a map of various properties of key-codes.
     * Can be useful if some keys are not working as intended.
     */
    fun keyCodeMap(): String {
        val builder = StringBuilder()

        builder.append("#   Keycode   Unicode   Printing?   Label\n")
        for (i in 0..KeyEvent.getMaxKeyCode()) {
            val e = KeyEvent(KeyEvent.ACTION_DOWN, i)

            val s = String.format("%4d  %-40s  %5d %5b  %c      \n",
                                  i, KeyEvent.keyCodeToString(i), e.unicodeChar, e.isPrintingKey, e.displayLabel)

            builder.append(s)
        }

        return builder.toString()
    }
}