/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout

/**
 * All the experimental stuff is dumped here.
 */
object Experimental {


    /**
     * Normally, drawers in [DrawerLayout] are closed by two gestures:
     * 1. Swipe 'on' the drawer
     * 2. Tap inside Scrim (dimmed region outside of drawer)
     *
     * Notably, swiping inside scrim area does NOT hide the drawer. This can be jarring
     * to users if drawer is relatively small & most of the layout area acts as scrim.
     *
     * The toolbar drawer used in [com.gaurav.avnc.ui.vnc.VncActivity] is affected by
     * this issue.
     *
     * This function attempts to detect these swipe gestures and close the drawer
     * when they happen.
     *
     * [drawerGravity] can be [Gravity.START] or [Gravity.END]
     *
     * Note: It will set a custom TouchListener on [drawerLayout].
     *
     * Why Experimental: Because I don't know if it handles all corner cases.
     */
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    fun setupDrawerCloseOnScrimSwipe(drawerLayout: DrawerLayout, drawerGravity: Int) {

        drawerLayout.setOnTouchListener(object : View.OnTouchListener {
            var drawerOpen = false

            val detector = GestureDetector(drawerLayout.context, object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(e1: MotionEvent, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                    val absGravity = Gravity.getAbsoluteGravity(drawerGravity, drawerLayout.layoutDirection)
                    if ((absGravity == Gravity.LEFT && vX < 0) || (absGravity == Gravity.RIGHT && vX > 0)) {
                        drawerLayout.closeDrawer(drawerGravity)
                        drawerOpen = false
                    }
                    return true
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN)
                    drawerOpen = drawerLayout.isDrawerOpen(drawerGravity)

                if (drawerOpen)
                    detector.onTouchEvent(event)

                return false
            }
        })
    }
}