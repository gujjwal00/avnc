/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.input

import android.graphics.PointF
import com.gaurav.avnc.ui.vnc.PointerAcceleration
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.abs

/**
 * Most actions have the same implementation in both modes, only difference being
 * the point where event is sent. [transformPoint] is used for this mode-specific
 * point selection.
 */
abstract class BasePointerMode(val viewModel: VncViewModel) {
    private val messenger = viewModel.messenger

    //Used for remote scrolling
    private var accumulatedDx = 0F
    private var accumulatedDy = 0F
    private val deltaPerScroll = 20F //For how much dx/dy, one scroll event will be sent
    private val yScrollDirection = (if (viewModel.pref.input.gesture.invertVerticalScrolling) -1 else 1)

    abstract fun transformPoint(p: PointF): PointF?
    abstract fun doMovePointer(p: PointF, dx: Float, dy: Float)
    abstract fun doRemoteDrag(button: PointerButton, p: PointF, dx: Float, dy: Float)

    open fun onGestureStart() {}
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
        // hs is -ve for for left and +ve for right. But doRemoteScroll() works
        // in terms of finger movement where -ve is right and +ve is left
        // So we have to invert the sign of hs for doRemoteScroll()
        doRemoteScroll(p, -1 * hs * deltaPerScroll, vs * deltaPerScroll)
    }
}

/**
 * Actions happen at touch-point, which is simply transformed from
 * viewport coordinates into corresponding position in framebuffer.
 */
class DirectPointerMode(viewModel: VncViewModel) : BasePointerMode(viewModel) {
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
class RelativePointerMode(viewModel: VncViewModel, private val accelerator: PointerAcceleration) : BasePointerMode(viewModel) {
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

    override fun doMovePointer(p: PointF, dx: Float, dy: Float) = doMovePointer(dx, dy, true)

    fun doMovePointer(dx: Float, dy: Float, accelerate: Boolean) {
        val xLimit = viewModel.frameState.fbWidth - 1
        val yLimit = viewModel.frameState.fbHeight - 1
        if (xLimit < 0 || yLimit < 0)
            return

        var adx = dx
        var ady = dy
        if (accelerate) {
            accelerator.compute()
            adx = accelerator.updateDx(dx)
            ady = accelerator.updateDy(dy)
        }

        pointerPosition.apply {
            offset(adx, ady)
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