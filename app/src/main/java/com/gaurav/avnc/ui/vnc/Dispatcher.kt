/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.view.KeyEvent
import com.gaurav.avnc.viewmodel.VncViewModel

/**
 * This class is responsible for executing an action in response to events
 * like keyboard input, gesture input etc.
 *
 * aVNC allows user to configure the action which should take place of
 * different events. This class reads those preferences and invokes proper handlers.
 */
class Dispatcher(private val viewModel: VncViewModel) {

    private val messenger by lazy { viewModel.messenger }

    /**************************************************************************
     * Action configuration
     **************************************************************************/

    val scaleAction = ::doScale
    val scrollAction = ::doPan
    val tapAction = ::doLeftClick
    val doubleTapAction = ::doDoubleClick
    val longPressAction = ::doRightClick

    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = scaleAction(scaleFactor, fx, fy)
    fun onScroll(dx: Float, dy: Float) = scrollAction(dx, dy)
    fun onTap(p: PointF) = tapAction(p)
    fun onDoubleTap(p: PointF) = doubleTapAction(p)
    fun onLongPress(p: PointF) = longPressAction(p)

    fun onKeyEvent(event: KeyEvent) {
        doSendKey(event)
    }


    /**************************************************************************
     * Available actions
     **************************************************************************/

    private fun doScale(scaleFactor: Float, fx: Float, fy: Float) {
        viewModel.updateZoom(scaleFactor, fx, fy)
    }

    private fun doPan(dx: Float, dy: Float) {
        viewModel.panFrame(dx, dy)
    }

    private fun doLeftClick(p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendLeftClick(fbp)
        }
    }

    private fun doDoubleClick(p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendLeftClick(fbp)
            messenger.sendLeftClick(fbp)
        }
    }

    private fun doRightClick(p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendRightClick(fbp)
        }
    }

    private fun doSendKey(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.unicodeChar != 0)
                    messenger.sendKeyDown(event.unicodeChar, false)
                else
                    messenger.sendKeyDown(event.keyCode, true)
            }

            KeyEvent.ACTION_UP -> {
                if (event.unicodeChar != 0)
                    messenger.sendKeyUp(event.unicodeChar, false)
                else
                    messenger.sendKeyUp(event.keyCode, true)
            }

            KeyEvent.ACTION_MULTIPLE -> {
                if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    for (c in event.characters) {
                        messenger.sendKeyDown(c.toInt(), false)
                        messenger.sendKeyUp(c.toInt(), false)
                    }
                } else { //Doesn't really happens anymore.
                    for (i in 1..event.repeatCount) {
                        messenger.sendKeyDown(event.keyCode, false)
                        messenger.sendKeyUp(event.keyCode, false)
                    }
                }
            }
        }
    }
}