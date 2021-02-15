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
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.abs

/**
 * This class is responsible for executing an action in response to events
 * like keyboard input, gesture input etc.
 *
 * aVNC allows user to configure the action which should take place of
 * different events. This class reads those preferences and invokes proper handlers.
 */
class Dispatcher(private val viewModel: VncViewModel) {

    private val messenger get() = viewModel.messenger
    private val prefs get() = viewModel.pref

    //Used for remote scrolling
    private var accumulatedDx = 0F
    private var accumulatedDy = 0F
    private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent

    /**************************************************************************
     * Action configuration
     **************************************************************************/

    val swipe1Action by lazy { selectSwipeAction(prefs.input.gesture.swipe1) }
    val swipe2Action by lazy { selectSwipeAction(prefs.input.gesture.swipe2) }
    val dragAction by lazy { selectSwipeAction(prefs.input.gesture.drag) }

    val tapAction by lazy { selectPointerAction(prefs.input.gesture.singleTap) }
    val doubleTapAction by lazy { selectPointerAction(prefs.input.gesture.doubleTap) }
    val longPressAction by lazy { selectPointerAction(prefs.input.gesture.longPress) }

    private fun selectPointerAction(actionName: String): (PointF) -> Unit {
        return when (actionName) {
            "left-click" -> { p -> doClick(PointerButton.Left, p) }
            "double-click" -> { p -> doDoubleClick(PointerButton.Left, p) }
            "middle-click" -> { p -> doClick(PointerButton.Middle, p) }
            "right-click" -> { p -> doClick(PointerButton.Right, p) }
            "move-pointer" -> { p -> doMovePointer(p) }
            else -> { _ -> } //Nothing
        }
    }

    private fun selectSwipeAction(actionName: String): (PointF, Float, Float) -> Unit {
        return when (actionName) {
            "remote-scroll" -> { sp, dx, dy -> doRemoteScroll(sp, dx, dy) }
            "pan" -> { _, dx, dy -> doPan(dx, dy) }
            "drag" -> { p, _, _ -> doDrag(p) }
            else -> { _, _, _ -> } //Nothing
        }
    }


    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)
    fun onSwipe1(startPoint: PointF, dx: Float, dy: Float) = swipe1Action(startPoint, dx, dy)
    fun onSwipe2(startPoint: PointF, dx: Float, dy: Float) = swipe2Action(startPoint, dx, dy)
    fun onTap(p: PointF) = tapAction(p)
    fun onDoubleTap(p: PointF) = doubleTapAction(p)
    fun onLongPress(p: PointF) = longPressAction(p)
    fun onDrag(p: PointF, dx: Float, dy: Float) = dragAction(p, dx, dy)

    fun onDragEnd(p: PointF) {
        if (prefs.input.gesture.drag == "drag")
            endDrag(p)
    }

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

    private fun doRemoteScroll(focus: PointF, dx: Float, dy: Float) {
        accumulatedDx += dx
        accumulatedDy += dy

        //Drain horizontal shift
        while (abs(accumulatedDx) > deltaPerScroll) {
            if (accumulatedDx > 0) {
                doClick(PointerButton.WheelLeft, focus)
                accumulatedDx -= deltaPerScroll
            } else {
                doClick(PointerButton.WheelRight, focus)
                accumulatedDx += deltaPerScroll
            }
        }

        //Drain vertical shift
        while (abs(accumulatedDy) > deltaPerScroll) {
            if (accumulatedDy > 0) {
                doClick(PointerButton.WheelUp, focus)
                accumulatedDy -= deltaPerScroll
            } else {
                doClick(PointerButton.WheelDown, focus)
                accumulatedDy += deltaPerScroll
            }
        }
    }

    private fun doClick(button: PointerButton, p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendClick(button, fbp)
        }
    }

    private fun doDoubleClick(button: PointerButton, p: PointF) {
        doClick(button, p)
        doClick(button, p)
    }

    private fun doMovePointer(p: PointF) {
        doClick(PointerButton.None, p)
    }

    private fun doDrag(p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendPointerButtonDown(PointerButton.Left, fbp)
        }
    }

    private fun endDrag(p: PointF) {
        val fbp = viewModel.frameState.toFb(p)

        if (fbp != null) {
            messenger.sendPointerButtonUp(PointerButton.Left, fbp)
        }
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
                    messenger.sendKeyDown(event.unicodeChar, false)
                else
                    messenger.sendKeyDown(event.keyCode, true)
            }

            KeyEvent.ACTION_UP -> {
                if (event.unicodeChar != 0 && event.keyCode != KeyEvent.KEYCODE_ENTER)
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