/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.abs

/**
 * aVNC allows user to configure the actions for different events.
 * This class reads those preferences and invokes proper handlers.
 *
 * Input handling overview:
 *
 *-     +----------------+     +--------------------+     +--------------+
 *-     |  Touch events  |     |     Key events     |     | Virtual keys |
 *-     +----------------+     +--------------------+     +--------------+
 *-             |                        |                        |
 *-             v                        v                        |
 *-     +----------------+     +--------------------+             |
 *-     | [TouchHandler] |     |    [KeyHandler]    |<------------+
 *-     +----------------+     +--------------------+
 *-             |                        |
 *-             |                        v
 *-             |              +--------------------+
 *-             +------------->+    [Dispatcher]    +
 *-                            +--------------------+
 *-                                |             |
 *-                                v             v
 *-                     +---------------+    +----------------+
 *-                     |  [Messenger]  |    | [VncViewModel] |
 *-                     +---------------+    +----------------+
 *-
 *-
 *
 * 1. First we identify which gesture/key was input by user
 * 2. Then we select an action based on user preferences. This is done here in [Dispatcher].
 * 3. Then that action is executed either in [Messenger] or [VncViewModel] depending on
 *    whether it affects the remote server OR local app state.
 */
class Dispatcher(private val viewModel: VncViewModel) {

    private val messenger = viewModel.messenger
    private val prefs = viewModel.pref

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

    /**
     * Returns a lambda which takes 4 arguments:
     *
     * sp - Start point of the gesture
     * cp - Current point of the gesture
     * dx - Change along x-axis since last event
     * dx - Change along y-axis since last event
     */
    private fun selectSwipeAction(actionName: String): (PointF, PointF, Float, Float) -> Unit {
        return when (actionName) {
            "pan" -> { _, _, dx, dy -> doPan(dx, dy) }
            "remote-scroll" -> { sp, _, dx, dy -> doRemoteScroll(sp, dx, dy) }
            "remote-drag" -> { _, cp, _, _ -> doDrag(cp) }
            else -> { _, _, _, _ -> } //Nothing
        }
    }


    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)

    fun onTap(p: PointF) = tapAction(p)
    fun onDoubleTap(p: PointF) = doubleTapAction(p)
    fun onLongPress(p: PointF) = longPressAction(p)

    fun onSwipe1(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe1Action(sp, cp, dx, dy)
    fun onSwipe2(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe2Action(sp, cp, dx, dy)
    fun onDrag(sp: PointF, cp: PointF, dx: Float, dy: Float) = dragAction(sp, cp, dx, dy)
    fun onDragEnd(p: PointF) {
        if (prefs.input.gesture.drag == "remote-drag")
            endDrag(p)
    }

    fun onKeyDown(keySym: Int) = messenger.sendKey(keySym, true)
    fun onKeyUp(keySym: Int) = messenger.sendKey(keySym, false)

    fun onMouseButtonDown(button: PointerButton, p: PointF) = doPointerButtonDown(button, p)
    fun onMouseButtonUp(button: PointerButton, p: PointF) = doPointerButtonUp(button, p)
    fun onMouseMove(p: PointF) = doMovePointer(p)
    fun onMouseScroll(p: PointF, hs: Float, vs: Float) {
        doRemoteScroll(p, hs * deltaPerScroll, vs * deltaPerScroll)
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


    private fun doPointerButtonUp(button: PointerButton, p: PointF) = inFbCoordinates(p) {
        messenger.sendPointerButtonUp(button, it)
    }

    private fun doPointerButtonDown(button: PointerButton, p: PointF) = inFbCoordinates(p) {
        messenger.sendPointerButtonDown(button, it)
    }

    private fun doClick(button: PointerButton, p: PointF) = inFbCoordinates(p) {
        messenger.sendClick(button, it)
    }

    private fun doDoubleClick(button: PointerButton, p: PointF) {
        doClick(button, p)
        doClick(button, p)
    }

    private fun doMovePointer(p: PointF) = doPointerButtonDown(PointerButton.None, p)
    private fun doDrag(p: PointF) = doPointerButtonDown(PointerButton.Left, p)
    private fun endDrag(p: PointF) = doPointerButtonUp(PointerButton.Left, p)


    /**
     * Positions of input events received from Android are in viewport coordinates.
     * We need to convert them into corresponding position in framebuffer.
     * Also, some positions in viewport might not correspond to a valid framebuffer
     * position (e.g. if zoom is less than 100%).
     *
     * This small utility method converts the position [p] into framebuffer
     * coordinates and invokes [block] with it IF it is valid.
     */
    private inline fun inFbCoordinates(p: PointF, block: (PointF) -> Unit) {
        val fbp = viewModel.frameState.toFb(p)
        if (fbp != null) {
            block(fbp)
        }
    }
}