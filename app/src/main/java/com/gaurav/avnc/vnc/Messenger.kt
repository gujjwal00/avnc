/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.graphics.PointF
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Allows sending different types of messages to remote server.
 */
class Messenger(private val client: VncClient) {

    /**************************************************************************
     * Sender thread
     **************************************************************************/
    private val sender = Executors.newSingleThreadExecutor()

    private fun execute(action: Runnable): Boolean {
        try {
            if (client.connected && !sender.isShutdown) {
                sender.execute(action)
                return true
            }
        } catch (e: Exception) {
            Log.w("Messenger", "Failed to enqueue action [isShutdown: ${sender.isShutdown}]: ${e.message}")
        }
        return false
    }

    fun shutdown() {
        runCatching {
            sender.shutdown()
            sender.awaitTermination(60, TimeUnit.SECONDS)
        }
        if (!sender.isTerminated)
            Log.w("Messenger", "Unable to shutdown messenger thread")
    }


    /**************************************************************************
     * Input events
     **************************************************************************/

    /**
     * Keeps track of current pointer button state.
     */
    private var pointerButtonMask: Int = 0

    private fun sendPointerEvent(mask: Int, p: PointF) {
        val x = p.x.toInt()
        val y = p.y.toInt()
        client.moveClientPointer(x, y)
        execute { client.sendPointerEvent(x, y, mask) }
    }

    fun sendPointerButtonDown(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask or button.bitMask
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonUp(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask and button.bitMask.inv()
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonRelease(p: PointF) {
        if (pointerButtonMask != 0) {
            pointerButtonMask = 0
            sendPointerEvent(pointerButtonMask, p)
        }
    }

    fun sendKey(keySym: Int, xtCode: Int, isDown: Boolean): Boolean {
        return execute { client.sendKeyEvent(keySym, xtCode, isDown) }
    }

    fun insertButtonUpDelay() {
        execute { runCatching { Thread.sleep(200) } }
    }

    /**************************************************************************
     * Misc
     **************************************************************************/

    fun sendClipboardText(text: String) {
        execute { client.sendCutText(text) }
    }

    fun setDesktopSize(width: Int, height: Int) {
        execute { client.setDesktopSize(width, height) }
    }

    fun refreshFrameBuffer() {
        execute { client.refreshFrameBuffer() }
    }

    fun setFrameBufferUpdatesPaused(pause: Boolean) {
        execute { client.setFrameBufferUpdatesPaused(pause) }
    }
}