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

    private val sender by lazy { Executors.newSingleThreadExecutor() }

    private fun execute(action: () -> Unit) {
        if (client.state == VncClient.State.Connected)
            sender.execute(action)
    }

    fun cleanup() {
        sender.shutdownNow()

        try {
            sender.awaitTermination(60, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(javaClass.simpleName, "Interrupted while waiting for Sender thread to shutdown!")
        }

        if (!sender.isShutdown)
            Log.w(javaClass.simpleName, "Unable to shutdown Sender thread!")
    }


    /**************************************************************************
     * Pointer events
     **************************************************************************/

    /**
     * Keeps track of current pointer button state.
     */
    private var pointerButtonMask: Int = 0

    private fun sendPointerEvent(mask: Int, p: PointF) {
        val x = p.x.toInt()
        val y = p.y.toInt()
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

    fun sendClick(button: PointerButton, p: PointF) {
        sendPointerButtonDown(button, p)
        sendPointerButtonUp(button, p)
    }

    /**************************************************************************
     * Key events
     **************************************************************************/

    fun sendKeyDown(keyCode: Int, translate: Boolean) {
        execute { client.sendKeyEvent(keyCode, true, translate) }
    }

    fun sendKeyUp(keyCode: Int, translate: Boolean) {
        execute { client.sendKeyEvent(keyCode, false, translate) }
    }

    /**************************************************************************
     * Misc
     **************************************************************************/

    fun sendClipboardText(text: String) {
        execute { client.sendCutText(text) }
    }
}