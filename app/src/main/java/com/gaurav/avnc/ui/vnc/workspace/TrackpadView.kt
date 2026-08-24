/*
 * Copyright (c) 2026 Gaurav Ujjwal.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.ui.vnc.workspace

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gaurav.avnc.ui.vnc.input.Dispatcher
import com.gaurav.avnc.vnc.PointerButton
import kotlin.math.abs

/** A deliberately small trackpad surface that delegates all pointer semantics to Dispatcher. */
class TrackpadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var dispatcher: Dispatcher? = null
    private var last = PointF()
    private var downAt = 0L
    private var moved = false
    private var twoFinger = false
    private var dragging = false
    private var isDown = false
    private var secondTap = false
    private var lastTapAt = 0L
    private var lastTap = PointF()
    private var pendingSingleClick: Runnable? = null
    private val slop = 12f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = 0x66808080
    }

    fun initialize(dispatcher: Dispatcher) {
        this.dispatcher = dispatcher
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 18f * resources.displayMetrics.density
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, 18f, 18f, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = dispatcher ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isDoubleTap = pendingSingleClick != null &&
                    event.eventTime - lastTapAt <= android.view.ViewConfiguration.getDoubleTapTimeout() &&
                    distance(event.x, event.y, lastTap.x, lastTap.y) <= slop * 2
                pendingSingleClick?.let { removeCallbacks(it) }
                pendingSingleClick = null
                secondTap = isDoubleTap
                isDown = true
                downAt = event.eventTime
                last.set(event.x, event.y)
                moved = false
                dragging = false
                twoFinger = false
                d.onWorkspaceGestureStart()
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                twoFinger = true
                secondTap = false
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - last.x
                val dy = event.y - last.y
                if (abs(event.x - last.x) > slop || abs(event.y - last.y) > slop) {
                    moved = true
                }
                if (twoFinger || event.pointerCount >= 2) {
                    twoFinger = true
                    d.onWorkspaceScroll(dx, dy)
                } else if (moved) {
                    if (secondTap && !dragging) {
                        d.onWorkspaceButtonDown(PointerButton.Left)
                        dragging = true
                    }
                    d.onWorkspaceMove(dx, dy)
                }
                last.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                isDown = false
                if (!moved && !dragging && event.eventTime - downAt < 350) {
                    if (secondTap) {
                        d.onWorkspaceDoubleClick(PointerButton.Left)
                        secondTap = false
                    } else if (twoFinger) {
                        d.onWorkspaceClick(PointerButton.Right)
                    } else {
                        lastTapAt = event.eventTime
                        lastTap.set(event.x, event.y)
                        pendingSingleClick = Runnable {
                            d.onWorkspaceClick(PointerButton.Left)
                            pendingSingleClick = null
                        }.also { postDelayed(it, android.view.ViewConfiguration.getDoubleTapTimeout().toLong()) }
                    }
                }
                d.onWorkspaceGestureStop()
            }
            MotionEvent.ACTION_CANCEL -> {
                pendingSingleClick?.let { removeCallbacks(it) }
                pendingSingleClick = null
                isDown = false
                secondTap = false
                d.onWorkspaceGestureStop()
            }
        }
        return true
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return kotlin.math.hypot(x1 - x2, y1 - y2)
    }
}
