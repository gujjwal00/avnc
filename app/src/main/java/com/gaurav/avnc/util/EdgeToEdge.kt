/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Insets
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowInsets.Type
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

/**
 * Android forces apps to be edge to edge on API 35+.
 * But this app requires a through redesign to properly handle edge-to-edge.
 *
 * To avoid breakage, this layout is used as a temporary measure to emulate
 * old behavior on new versions. It simply adds necessary padding for system bars
 * and draws behind them with appropriate colors.
 *
 * Hopefully, this will not be needed after proper UI migration to Compose.
 */
@Suppress("DEPRECATION")
class EdgeToEdgeWrapperLayout(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
    : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    @RequiresApi(Build.VERSION_CODES.Q)
    private var statusBarInsets = Insets.NONE
    private val statusBarPaint = Paint()
    private var statusBarThemeColor = 0

    @RequiresApi(Build.VERSION_CODES.Q)
    private var navBarInsets = Insets.NONE
    private val navBarPaint = Paint()
    private var navBarThemeColor = 0
    private var navBarCustomColor: Int? = null

    init {
        if (Build.VERSION.SDK_INT >= 35) {
            setWillNotDraw(false)
            statusBarThemeColor = resolveColorAttrib(android.R.attr.statusBarColor)
            navBarThemeColor = resolveColorAttrib(android.R.attr.navigationBarColor)
        }
    }


    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT < 35)
            return super.onApplyWindowInsets(insets)

        statusBarInsets = insets.getInsets(Type.statusBars())
        navBarInsets = insets.getInsets(Type.navigationBars())

        insets.getInsets(Type.systemBars() or Type.ime()).let {
            setPadding(it.left, it.top, it.right, it.bottom)
        }

        return WindowInsets.CONSUMED
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT < 35)
            return

        statusBarPaint.color = statusBarThemeColor
        navBarPaint.color = navBarCustomColor ?: navBarThemeColor

        drawInsets(canvas, statusBarInsets, statusBarPaint)
        drawInsets(canvas, navBarInsets, navBarPaint)
    }

    fun setCustomNavBarColor(color: Int?) {
        navBarCustomColor = color
    }

    private fun resolveColorAttrib(attrib: Int): Int {
        return TypedValue().let {
            context.theme.resolveAttribute(attrib, it, true)
            it.data
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun drawInsets(canvas: Canvas, i: Insets, paint: Paint) {
        if (i.top != 0) canvas.drawRect(0f, 0f, width.toFloat(), i.top.toFloat(), paint)
        if (i.bottom != 0) canvas.drawRect(0f, (height - i.bottom).toFloat(), width.toFloat(), height.toFloat(), paint)
        if (i.left != 0) canvas.drawRect(0f, 0f, i.left.toFloat(), height.toFloat(), paint)
        if (i.right != 0) canvas.drawRect((width - i.right).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)
    }
}


/**
 * Helpers to install [EdgeToEdgeWrapperLayout] as root view for an activity
 */
object EdgeToEdgeHelper {
    private val WRAPPER_ENABLED = Build.VERSION.SDK_INT >= 35

    fun setContentView(activity: Activity, @LayoutRes res: Int) {
        if (!WRAPPER_ENABLED) {
            activity.setContentView(res)
            return
        }

        val wrapper = EdgeToEdgeWrapperLayout(activity)
        configureActivity(activity)
        activity.layoutInflater.inflate(res, wrapper, true)
        activity.setContentView(wrapper)
    }

    fun <T : ViewDataBinding> setDataBindingContentView(activity: Activity, @LayoutRes res: Int): T {
        if (!WRAPPER_ENABLED) {
            return DataBindingUtil.setContentView(activity, res)
        }

        val wrapper = EdgeToEdgeWrapperLayout(activity)
        configureActivity(activity)
        activity.setContentView(wrapper)
        return DataBindingUtil.inflate(activity.layoutInflater, res, wrapper, true)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun configureActivity(activity: Activity) {
        activity.window.isNavigationBarContrastEnforced = false
    }
}
