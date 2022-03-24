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


    /**************************************************************************
     * Action configuration
     **************************************************************************/
    private val directMode = DirectMode()
    private val relativeMode by lazy { RelativeMode() } //Lazy to avoid pointer sync behaviour
    private val defaultMode = if (gesturePref.directTouch) directMode else relativeMode

    private val tap1Action = selectPointAction(gesturePref.tap1)
    private val tap2Action = selectPointAction(gesturePref.tap2)
    private val doubleTapAction = selectPointAction(gesturePref.doubleTap)
    private val longPressAction = selectPointAction(gesturePref.longPress)

    private val swipe1Action = selectSwipeAction(if (gesturePref.directTouch) gesturePref.swipe1 else "move-pointer")
    private val swipe2Action = selectSwipeAction(gesturePref.swipe2)
    private val dragAction = selectSwipeAction(gesturePref.drag)

    private fun selectPointAction(actionName: String): (PointF) -> Unit {
        return when (actionName) {
            "left-click" -> { p -> defaultMode.doClick(PointerButton.Left, p) }
            "double-click" -> { p -> defaultMode.doDoubleClick(PointerButton.Left, p) }
            "middle-click" -> { p -> defaultMode.doClick(PointerButton.Middle, p) }
            "right-click" -> { p -> defaultMode.doClick(PointerButton.Right, p) }
            "open-keyboard" -> { _ -> doOpenKeyboard() }
            else -> { _ -> } //Nothing
        }
    }

    private fun selectSwipeAction(actionName: String): SwipeAction {
        return when (actionName) {
            "pan" -> SwipeAction { _, _, dx, dy -> doPan(dx, dy) }
            "move-pointer" -> SwipeAction { _, cp, dx, dy -> defaultMode.doMovePointer(cp, dx, dy) }
            "remote-scroll" -> SwipeAction { sp, _, dx, dy -> defaultMode.doRemoteScroll(sp, dx, dy) }
            "remote-drag" -> SwipeAction { _, cp, dx, dy -> defaultMode.doDrag(cp, dx, dy) }
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

    fun onGestureStart() {
        viewModel.client.ignorePointerMovesByServer = true
        stopFling()
    }

    fun onGestureStop(p: PointF) {
        defaultMode.doButtonRelease(p)
        viewModel.client.ignorePointerMovesByServer = false
    }

    fun onTap1(p: PointF) = tap1Action(p)
    fun onTap2(p: PointF) = tap2Action(p)
    fun onDoubleTap(p: PointF) = doubleTapAction(p)
    fun onLongPress(p: PointF) = longPressAction(p)

    fun onSwipe1(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe1Action(sp, cp, dx, dy)
    fun onSwipe2(sp: PointF, cp: PointF, dx: Float, dy: Float) = swipe2Action(sp, cp, dx, dy)
    fun onDrag(sp: PointF, cp: PointF, dx: Float, dy: Float) = dragAction(sp, cp, dx, dy)

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)
    fun onFling(vx: Float, vy: Float) = doFling(vx, vy)

    fun onMouseButtonDown(button: PointerButton, p: PointF) = directMode.doButtonDown(button, p)
    fun onMouseButtonUp(button: PointerButton, p: PointF) = directMode.doButtonUp(button, p)
    fun onMouseMove(p: PointF) = directMode.doMovePointer(p, 0f, 0f)
    fun onMouseScroll(p: PointF, hs: Float, vs: Float) = directMode.doRemoteScrollFromMouse(p, hs, vs)

    fun onStylusTap(p: PointF) = directMode.doClick(PointerButton.Left, p)
    fun onStylusDoubleTap(p: PointF) = directMode.doDoubleClick(PointerButton.Left, p)
    fun onStylusLongPress(p: PointF) = directMode.doClick(PointerButton.Right, p)
    fun onStylusScroll(p: PointF) = directMode.doButtonDown(PointerButton.Left, p)

    fun onXKeySym(keySym: Int, isDown: Boolean) = messenger.sendKey(keySym, isDown)


    /**************************************************************************
     * Available actions
     **************************************************************************/

    private fun doOpenKeyboard() = activity.showKeyboard()
    private fun doScale(scaleFactor: Float, fx: Float, fy: Float) = viewModel.updateZoom(scaleFactor, fx, fy)
    private fun doPan(dx: Float, dy: Float) = viewModel.panFrame(dx, dy)

    private fun doFling(vx: Float, vy: Float) = viewModel.frameScroller.fling(vx, vy)
    private fun stopFling() = viewModel.frameScroller.stop()

    /**
     * Most actions have the same implementation in both modes, only difference being
     * the point where event is sent. [transformPoint] is used for this mode-specific
     * point selection.
     */
    private abstract inner class AbstractMode {
        //Used for remote scrolling
        private var accumulatedDx = 0F
        private var accumulatedDy = 0F
        private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent

        abstract fun transformPoint(p: PointF): PointF?
        abstract fun doMovePointer(p: PointF, dx: Float, dy: Float)
        abstract fun doDrag(p: PointF, dx: Float, dy: Float)

        fun doButtonDown(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonDown(button, it) }
        }

        fun doButtonUp(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonUp(button, it) }
        }

        fun doButtonRelease(p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonRelease(it) }
        }

        fun doClick(button: PointerButton, p: PointF) {
            doButtonDown(button, p)
            doButtonUp(button, p)
        }

        fun doDoubleClick(button: PointerButton, p: PointF) {
            doClick(button, p)
            doClick(button, p)
        }

        fun doRemoteScroll(focus: PointF, dx: Float, dy: Float) {
            accumulatedDx += dx
            accumulatedDy += dy

            //Drain horizontal change
            while (abs(accumulatedDx) >= deltaPerScroll) {
                if (accumulatedDx > 0) {
                    doClick(PointerButton.WheelLeft, focus)
                    accumulatedDx -= deltaPerScroll
                } else {
                    doClick(PointerButton.WheelRight, focus)
                    accumulatedDx += deltaPerScroll
                }
            }

            //Drain vertical change
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

        /**
         * [hs] Movement of horizontal scroll wheel
         * [vs] Movement of vertical scroll wheel
         */
        fun doRemoteScrollFromMouse(p: PointF, hs: Float, vs: Float) {
            doRemoteScroll(p, hs * deltaPerScroll, vs * deltaPerScroll)
        }
    }

    /**
     * Actions happen at touch-point, which is simply transformed from
     * viewport coordinates into corresponding position in framebuffer.
     */
    private inner class DirectMode : AbstractMode() {
        override fun transformPoint(p: PointF) = viewModel.frameState.toFb(p)
        override fun doMovePointer(p: PointF, dx: Float, dy: Float) = doButtonDown(PointerButton.None, p)
        override fun doDrag(p: PointF, dx: Float, dy: Float) = doButtonDown(PointerButton.Left, p)
    }

    /**
     * Actions happen at [pointerPosition], which is updated by [doMovePointer].
     */
    private inner class RelativeMode : AbstractMode() {
        private val pointerPosition = PointF(0f, 0f)

        override fun transformPoint(p: PointF) = pointerPosition

        override fun doMovePointer(p: PointF, dx: Float, dy: Float) {
            pointerPosition.apply {
                offset(dx, dy)
                x = x.coerceIn(0f, viewModel.frameState.fbWidth)
                y = y.coerceIn(0f, viewModel.frameState.fbHeight)
            }
            doButtonDown(PointerButton.None, pointerPosition)
        }

        override fun doDrag(p: PointF, dx: Float, dy: Float) {
            doButtonDown(PointerButton.Left, p)
            doMovePointer(p, dx, dy)
        }

        /**
         * As we have no way know the real pointer position (at-least for now), we move
         * the remote pointer to center of the screen. This is a crude way of syncing
         * [pointerPosition] with remote pointer position.
         *
         * We have to start somewhere, and center of the screen seems most appropriate.
         * This is done immediately after connecting (and whenever framebuffer size changes),
         * If we waited for a gesture before doing this, there would be a sudden jump,
         * right before the gesture. Another "benefit" is, if connection is fast enough,
         * user might not even notice our trickery.
         *
         * One caveat is, we can sync only after client initialization, but size change event
         * is likely to fire during initialization. Another problem is activity restart,
         * which will re-trigger our client state observer. We use [syncPending] to solve
         * both of these problems.
         *
         * We can (hopefully) implement a better solution in the future.
         */

        private var syncPending = false

        init {
            viewModel.fbSizeChangedEvent.observe(activity) {
                syncPending = true
                syncPosition()
            }
            viewModel.state.observe(activity) {
                syncPosition()
            }
        }

        private fun syncPosition() {
            if (syncPending && viewModel.client.connected) {
                pointerPosition.apply {
                    x = viewModel.frameState.fbWidth / 2
                    y = viewModel.frameState.fbHeight / 2
                }
                doButtonDown(PointerButton.None, pointerPosition)
                syncPending = false
            }
        }
    }
}