/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.widget.Toast // Import Toast
import com.gaurav.avnc.ui.vnc.TouchPanningInputDevice // Added import
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

    // Sensitivity factor for converting swipe dx/dy to camera yaw/pitch.
    // This could also be sourced from AppPreferences if it needs to be user-configurable.
    private val cameraPanSensitivity = 0.1f

    private var touchPanningInputDevice: TouchPanningInputDevice? = null

    /**************************************************************************
     * Action configuration
     **************************************************************************/
    private val directMode = DirectMode()
    private val relativeMode = RelativeMode()
    private var config = Config()

    private inner class Config {
        val gestureStyle = viewModel.activeGestureStyle.value ?: gesturePref.style
        val defaultMode = if (gestureStyle == "touchscreen") directMode else relativeMode

        val tap1Action = selectPointAction(gesturePref.tap1)
        val tap2Action = selectPointAction(gesturePref.tap2)
        val tap3Action = selectPointAction(gesturePref.tap3)
        val doubleTapAction = selectPointAction(gesturePref.doubleTap)
        val longPressAction = selectPointAction(gesturePref.longPress)

        val swipe1Pref = if (gestureStyle == "touchpad") "move-pointer" else gesturePref.swipe1
        val swipe1Action = selectSwipeAction(swipe1Pref)
        val swipe2Action = selectSwipeAction(gesturePref.swipe2)
        val swipe3Action = selectSwipeAction(gesturePref.swipe3)
        val doubleTapSwipeAction = selectSwipeAction(gesturePref.doubleTapSwipe)
        val longPressSwipeAction = selectSwipeAction(gesturePref.longPressSwipe)
        val flingAction = selectFlingAction()

        val mouseBackAction = selectPointAction(viewModel.pref.input.mouseBack)

        private fun selectPointAction(actionName: String): (PointF) -> Unit {
            if (actionName == "center_xr_view") {
                Toast.makeText(activity, "Dispatcher: 'center_xr_view' resolved by selectPointAction", Toast.LENGTH_SHORT).show() // DEBUG
            }
            return when (actionName) {
                "left-press" -> { p -> defaultMode.doButtonDown(PointerButton.Left, p) }
                "left-click" -> { p -> defaultMode.doClick(PointerButton.Left, p) }
                "double-click" -> { p -> defaultMode.doDoubleClick(PointerButton.Left, p) }
                "middle-click" -> { p -> defaultMode.doClick(PointerButton.Middle, p) }
                "right-click" -> { p -> defaultMode.doClick(PointerButton.Right, p) }
                "open-keyboard" -> { _ -> doOpenKeyboard() }
                "center_xr_view" -> { _ ->
                    Toast.makeText(activity, "Dispatcher: Executing 'center_xr_view' action", Toast.LENGTH_SHORT).show() // DEBUG
                    viewModel.requestViewReset()
                } // New action
                else -> { _ -> } //Nothing
            }
        }

        /**
         * Returns a lambda which accepts four arguments:
         *
         * sp: Start point of the gesture
         * cp: Current point of the gesture
         * dx: Change along x-axis since last event
         * dy: Change along y-axis since last event
         */
        private fun selectSwipeAction(actionName: String): (PointF, PointF, Float, Float) -> Unit {
            return when (actionName) {
                "pan" -> { _, _, dx, dy ->
                    // Apply the same sensitivity and inversion as the original doCameraPan
                    val deltaYaw = dx * cameraPanSensitivity
                    val deltaPitch = dy * cameraPanSensitivity * -1f // Ensure consistent Y inversion for panning up
                    this@Dispatcher.touchPanningInputDevice?.processPan(deltaYaw, deltaPitch)
                }
                "move-pointer" -> { _, cp, dx, dy -> defaultMode.doMovePointer(cp, dx, dy) }
                "remote-scroll" -> { sp, _, dx, dy -> defaultMode.doRemoteScroll(sp, dx, dy) }
                "remote-drag" -> { _, cp, dx, dy -> defaultMode.doRemoteDrag(PointerButton.Left, cp, dx, dy) }
                "remote-drag-middle" -> { _, cp, dx, dy -> defaultMode.doRemoteDrag(PointerButton.Middle, cp, dx, dy) }
                else -> { _, _, _, _ -> } //Nothing
            }
        }

        /**
         * Fling is only used for smooth-scrolling the frame.
         * So it only makes sense when 1-finger-swipe is set to "pan".
         */
        private fun selectFlingAction(): (Float, Float) -> Unit {
            return if (swipe1Pref == "pan") { vx, vy -> startFrameFling(vx, vy) }
            else { _, _ -> }
        }
    }

    /**************************************************************************
     * Event receivers
     **************************************************************************/

    fun onGestureStart() = config.defaultMode.onGestureStart()
    fun onGestureStop(p: PointF) {
        config.defaultMode.onGestureStop(p)
        viewModel.frameState.onGestureStop()
    }

    fun onTap1(p: PointF) = config.tap1Action(p)
    fun onTap2(p: PointF) = config.tap2Action(p)
    fun onTap3(p: PointF) {
        Toast.makeText(activity, "Dispatcher.onTap3 called", Toast.LENGTH_SHORT).show() // DEBUG
        config.tap3Action(p)
    }
    fun onDoubleTap(p: PointF) = config.doubleTapAction(p)
    fun onLongPress(p: PointF) = config.longPressAction(p)

    fun onSwipe1(sp: PointF, cp: PointF, dx: Float, dy: Float) = config.swipe1Action(sp, cp, dx, dy)
    fun onSwipe2(sp: PointF, cp: PointF, dx: Float, dy: Float) = config.swipe2Action(sp, cp, dx, dy)
    fun onSwipe3(sp: PointF, cp: PointF, dx: Float, dy: Float) = config.swipe3Action(sp, cp, dx, dy)
    fun onDoubleTapSwipe(sp: PointF, cp: PointF, dx: Float, dy: Float) = config.doubleTapSwipeAction(sp, cp, dx, dy)
    fun onLongPressSwipe(sp: PointF, cp: PointF, dx: Float, dy: Float) = config.longPressSwipeAction(sp, cp, dx, dy)

    fun onScale(scaleFactor: Float, fx: Float, fy: Float) = doScale(scaleFactor, fx, fy)
    fun onFling(vx: Float, vy: Float) = config.flingAction(vx, vy)

    fun onMouseButtonDown(button: PointerButton, p: PointF) = directMode.doButtonDown(button, p)
    fun onMouseButtonUp(button: PointerButton, p: PointF) = directMode.doButtonUp(button, p)
    fun onMouseMove(p: PointF) = directMode.doMovePointer(p, 0f, 0f)
    fun onMouseScroll(p: PointF, hs: Float, vs: Float) = directMode.doRemoteScrollFromMouse(p, hs, vs)
    fun onMouseBack(p: PointF) = config.mouseBackAction(p)

    fun onStylusTap(p: PointF) = directMode.doClick(PointerButton.Left, p)
    fun onStylusDoubleTap(p: PointF) = directMode.doDoubleClick(PointerButton.Left, p)
    fun onStylusLongPress(p: PointF) = directMode.doClick(PointerButton.Right, p)
    fun onStylusScroll(p: PointF) = directMode.doButtonDown(PointerButton.Left, p)

    fun onXKey(keySym: Int, xtCode: Int, isDown: Boolean) = messenger.sendKey(keySym, xtCode, isDown)

    fun onGestureStyleChanged() {
        config = Config()
    }

    /**
     * Re-initializes the action configuration.
     * This should be called if underlying preferences that affect action mapping have changed.
     */
    fun reinitializeConfig() {
        config = Config()
    }

    fun setTouchPanningInputDevice(device: TouchPanningInputDevice) {
        this.touchPanningInputDevice = device
    }

    /**************************************************************************
     * Available actions
     **************************************************************************/

    private fun doOpenKeyboard() = activity.showKeyboard()
    private fun doScale(scaleFactor: Float, fx: Float, fy: Float) = viewModel.updateZoom(scaleFactor, fx, fy)
    // This is the original 2D pan for the VNC frame. Keep it for other input types if needed,
    // or if "pan" action from non-swipe sources should still do 2D pan.
    private fun doPan(dx: Float, dy: Float) = viewModel.panFrame(dx, dy)

    /**
     * New method to handle 3D camera panning based on swipe deltas.
     */
    private fun doCameraPan(dx: Float, dy: Float) {
        val deltaYaw = dx * cameraPanSensitivity
        // Y-axis on screen is often inverted compared to typical camera pitch controls.
        val deltaPitch = dy * cameraPanSensitivity * -1f
        viewModel.panCamera(deltaYaw, deltaPitch)
    }

    private fun startFrameFling(vx: Float, vy: Float) = viewModel.frameScroller.fling(vx, vy)
    private fun stopFrameFling() = viewModel.frameScroller.stop()

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
        private val yScrollDirection = (if (gesturePref.invertVerticalScrolling) -1 else 1)

        abstract fun transformPoint(p: PointF): PointF?
        abstract fun doMovePointer(p: PointF, dx: Float, dy: Float)
        abstract fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float)

        open fun onGestureStart() = stopFrameFling()
        open fun onGestureStop(p: PointF) = doButtonRelease(p)

        fun doButtonDown(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonDown(button, it) }
        }

        fun doButtonUp(button: PointerButton, p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonUp(button, it) }
        }

        fun doButtonRelease(p: PointF) {
            transformPoint(p)?.let { messenger.sendPointerButtonRelease(it) }
        }

        open fun doClick(button: PointerButton, p: PointF) {
            doButtonDown(button, p)
            // Some apps (mostly games) seems to ignore click event if button-up is received too early
            if ((button == PointerButton.Left || button == PointerButton.Middle || button == PointerButton.Right)
                && viewModel.profile.fButtonUpDelay)
                messenger.insertButtonUpDelay()
            doButtonUp(button, p)
        }

        fun doDoubleClick(button: PointerButton, p: PointF) {
            doClick(button, p)
            doClick(button, p)
        }

        fun doRemoteScroll(focus: PointF, dx: Float, dy: Float) {
            accumulatedDx += dx
            accumulatedDy += dy * yScrollDirection

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
        override fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float) = doButtonDown(button, p)
        override fun doClick(button: PointerButton, p: PointF) {
            if (transformPoint(p) != null)
                super.doClick(button, p)
            else if (button == PointerButton.Left)
                coerceToFbEdge(p)?.let { doMovePointer(it, 0f, 0f) }
        }

        // When user taps outside the frame, move the pointer to edge of the frame
        // It allows opening of taskbar/panels when they are set to auto-hide.
        // It can also be used for previewing taskbar items.
        private fun coerceToFbEdge(p: PointF): PointF? {
            val fs = viewModel.frameState
            if (fs.fbWidth < 1 || fs.fbHeight < 1)
                return null

            return fs.toVP(
                    fs.toFbUnchecked(p).apply {
                        x = x.coerceIn(0f, fs.fbWidth - 1)
                        y = y.coerceIn(0f, fs.fbHeight - 1)
                    }
            )
        }
    }

    /**
     * Actions happen at [pointerPosition], which is updated by [doMovePointer].
     */
    private inner class RelativeMode : AbstractMode() {
        private val pointerPosition = PointF(0f, 0f)

        override fun onGestureStart() {
            super.onGestureStart()
            //Initialize with the latest pointer position
            pointerPosition.apply {
                x = viewModel.client.pointerX.toFloat()
                y = viewModel.client.pointerY.toFloat()
            }
            viewModel.client.ignorePointerMovesByServer = true
        }

        override fun onGestureStop(p: PointF) {
            super.onGestureStop(p)
            viewModel.client.ignorePointerMovesByServer = false
        }

        override fun transformPoint(p: PointF) = pointerPosition

        override fun doMovePointer(p: PointF, dx: Float, dy: Float) {
            val xLimit = viewModel.frameState.fbWidth - 1
            val yLimit = viewModel.frameState.fbHeight - 1
            if (xLimit < 0 || yLimit < 0)
                return

            pointerPosition.apply {
                offset(dx, dy)
                x = x.coerceIn(0f, xLimit)
                y = y.coerceIn(0f, yLimit)
            }
            doButtonDown(PointerButton.None, pointerPosition)

            //Try to keep the pointer centered on screen
            val vp = viewModel.frameState.toVP(pointerPosition)
            val centerDiffX = viewModel.frameState.safeArea.centerX() - vp.x
            val centerDiffY = viewModel.frameState.safeArea.centerY() - vp.y
            viewModel.panFrame(centerDiffX, centerDiffY)
        }

        override fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float) {
            doButtonDown(button, p)
            doMovePointer(p, dx, dy)
        }
    }
}