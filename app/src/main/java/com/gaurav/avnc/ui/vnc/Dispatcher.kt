/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import com.gaurav.avnc.ui.vnc.Dispatcher.SwipeAction
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.abs

/**
 * We allow users to customize the actions for different events.
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
 *-                                      |
 *-                                      |
 *-                 +--------------------+---------------------+
 *-                 |                    |                     |
 *-                 v                    v                     v
 *-         +---------------+    +----------------+    +---------------+
 *-         |  [Messenger]  |    | [VncViewModel] |    | [VncActivity] |
 *-         +---------------+    +----------------+    +---------------+
 *-
 *-
 *
 * 1. First we identify which gesture/key was input by the user.
 * 2. Then we select an action based on user preferences. This is done here in [Dispatcher].
 * 3. Then that action is executed. Some actions change local app state (e.g. zoom in/out),
 *    while others send events to remote server (e.g. mouse click).
 */
class Dispatcher(private val activity: VncActivity) {

    private val viewModel = activity.viewModel
    private val messenger = viewModel.messenger
    private val gesturePref = viewModel.pref.input.gesture

    //Used for remote scrolling
    private var accumulatedDx = 0F
    private var accumulatedDy = 0F
    private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent

    /**************************************************************************
     * Action configuration
     **************************************************************************/

    private val tap1Action = selectPointAction(gesturePref.tap1)
    private val tap2Action = selectPointAction(gesturePref.tap2)
    private val doubleTapAction = selectPointAction(gesturePref.doubleTap)
    private val longPressAction = selectPointAction(gesturePref.longPress)

    private val swipe1Action = selectSwipeAction(gesturePref.swipe1)
    private val swipe2Action = selectSwipeAction(gesturePref.swipe2)
    private val dragAction = selectSwipeAction(gesturePref.drag)

    private fun selectPointAction(actionName: String): (PointF) -> Unit {
        return when (actionName) {
            "left-click" -> { p -> doClick(PointerButton.Left, p) }
            "double-click" -> { p -> doDoubleClick(PointerButton.Left, p) }
            "middle-click" -> { p -> doClick(PointerButton.Middle, p) }
            "right-click" -> { p -> doClick(PointerButton.Right, p) }
            "move-pointer" -> { p -> doMovePointer(p) }
            "open-keyboard" -> { _ -> doOpenKeyboard() }
            else -> { _ -> } //Nothing
        }
    }

    private fun selectSwipeAction(actionName: String): SwipeAction {
        return when (actionName) {
            "pan" -> SwipeAction { _, _, dx, dy -> doPan(dx, dy) }
            "remote-scroll" -> SwipeAction { sp, _, dx, dy -> doRemoteScroll(sp, dx, dy) }
            "remote-drag" -> SwipeAction { _, cp, _, _ -> doDrag(cp) }
            else -> SwipeAction { _, _, _, _ -> } //Nothing
        }
    }

    //Instead of using generic lambda, like point actions, we are using a functional
    //interface with SAM conversion to avoid boxing/unboxing overhead for dx & dy.
    private fun interface SwipeAction {
        /**
         * [sp] Start point of the gesture
         * [cp] Current point of the gesture
         * [dx] Change along x-axis since last event
         * [dy] Change along y-axis since last event
         */
        operator fun invoke(sp: PointF, cp: PointF, dx: Float, dy: Float)
    }


    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onGestureStart(p: PointF) = stopFling()
    fun onGestureStop(p: PointF) = doPointerButtonRelease(p)

    fun onTap1(p: PointF) = tap1Action(p)
    fun onTap2(p: PointF) = tap2Action(p)
    fun onDoubleTap(p: PointF) = doubleTapAction(p)
    fun onLongPress(p: PointF) = longPressAction(p)

    fun onSwipe1(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe1Action(sp, cp, dx, dy)
    fun onSwipe2(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe2Action(sp, cp, dx, dy)
    fun onDrag(sp: PointF, cp: PointF, dx: Float, dy: Float) = dragAction(sp, cp, dx, dy)

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)
    fun onFling(vx: Float, vy: Float) = doFling(vx, vy)

    fun onMouseButtonDown(button: PointerButton, p: PointF) = doPointerButtonDown(button, p)
    fun onMouseButtonUp(button: PointerButton, p: PointF) = doPointerButtonUp(button, p)
    fun onMouseMove(p: PointF) = doMovePointer(p)
    fun onMouseScroll(p: PointF, hs: Float, vs: Float) = doRemoteScroll(p, hs * deltaPerScroll, vs * deltaPerScroll)

    fun onStylusTap(p: PointF) = doClick(PointerButton.Left, p)
    fun onStylusDoubleTap(p: PointF) = doDoubleClick(PointerButton.Left, p)
    fun onStylusLongPress(p: PointF) = doClick(PointerButton.Right, p)
    fun onStylusScroll(p: PointF) = doPointerButtonDown(PointerButton.Left, p)

    fun onXKeySym(keySym: Int, isDown: Boolean) = messenger.sendKey(keySym, isDown)

    /**************************************************************************
     * Available actions
     **************************************************************************/

    private fun doScale(scaleFactor: Float, fx: Float, fy: Float) {
        viewModel.updateZoom(scaleFactor, fx, fy)
    }

    private fun doPan(dx: Float, dy: Float) {
        viewModel.panFrame(dx, dy)
    }

    private fun doFling(vx: Float, vy: Float) = viewModel.frameScroller.fling(vx, vy)
    private fun stopFling() = viewModel.frameScroller.stop()

    private fun doRemoteScroll(focus: PointF, dx: Float, dy: Float) {
        accumulatedDx += dx
        accumulatedDy += dy

        //Drain horizontal shift
        while (abs(accumulatedDx) >= deltaPerScroll) {
            if (accumulatedDx > 0) {
                doClick(PointerButton.WheelLeft, focus)
                accumulatedDx -= deltaPerScroll
            } else {
                doClick(PointerButton.WheelRight, focus)
                accumulatedDx += deltaPerScroll
            }
        }

        //Drain vertical shift
        while (abs(accumulatedDy) >= deltaPerScroll) {
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

    private fun doPointerButtonRelease(p: PointF) = inFbCoordinates(p) {
        messenger.sendPointerButtonRelease(it)
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

    private fun doOpenKeyboard() = activity.showKeyboard()

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