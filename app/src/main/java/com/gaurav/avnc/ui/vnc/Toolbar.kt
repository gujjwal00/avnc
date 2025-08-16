/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.drawerlayout.widget.DrawerLayout
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.VncViewModel.State
import com.gaurav.avnc.viewmodel.VncViewModel.State.Companion.isConnected

/**
 *
 * Overview of toolbar layout:
 *
 *                DrawerLayout
 *   +-------------------+--------------+
 *   |                   |              |
 *   |   Toolbar Drawer  |              |
 *   |    [drawerView]   |              |
 *   |                   |              |
 *   |+---+              |              |
 *   || B |              |              |
 *   || t |              |              |
 *   || n |+------------+|    Scrim     |
 *   || s ||  Flyouts   ||              |
 *   |+---++------------+|              |
 *   |                   |              |
 *   |                   |              |
 *   |                   |              |
 *   |                   |              |
 *   |                   |              |
 *   +-------------------+--------------+
 *
 * User can align the toolbar to left or right edge.
 *
 */
class Toolbar(private val activity: VncActivity) {
    private val viewModel = activity.viewModel
    private val binding = activity.binding.toolbar
    private val drawerLayout = activity.binding.drawerLayout
    private val drawerView = binding.root
    private val openWithSwipe = viewModel.pref.viewer.toolbarOpenWithSwipe
    private val openWithButton = viewModel.pref.viewer.toolbarOpenWithButton
    private val openerButton = activity.binding.openToolbarBtn
    private val flyouts = mutableMapOf<ToggleButton, View>()

    fun initialize() {
        binding.keyboardBtn.setOnClickListener { activity.showKeyboard(); close() }
        binding.zoomOptionsToggle.setOnLongClickListener { resetZoomToDefault(); close(); true }
        binding.zoomResetBtn.setOnClickListener { resetZoomToDefault(); close() }
        binding.zoomResetBtn.setOnLongClickListener { resetZoom(); close(); true }
        binding.zoomLockBtn.setOnCheckedChangeListener { _, checked -> toggleZoomLock(checked); close() }
        binding.zoomSaveBtn.setOnClickListener { saveZoom(); close() }
        binding.virtualKeysBtn.setOnClickListener { activity.virtualKeys.show(true); close() }

        // Root view is transparent. Click on it should work just like a click in scrim area
        drawerView.setOnClickListener { close() }

        viewModel.state.observe(activity) { onStateChange(it) }

        setupAlignment()
        setupFlyouts()
        setupFlyoutClose()
        setupOpenerButton()
        setupViewModeSelection()
        setupGestureStyleSelection()
        setupGestureExclusionRect()
        setupDrawerCloseOnScrimSwipe()
    }

    fun open() {
        drawerLayout.openDrawer(drawerView)
    }

    fun close() {
        drawerLayout.closeDrawer(drawerView)
    }

    /**
     * The main thing problematic for toolbar is the display cutout.
     * If any cutout is found to overlap the toolbar, system window
     * fitting is enabled, which automatically adds required paddings.
     */
    fun handleInsets(insets: WindowInsetsCompat) {
        val cutout = insets.displayCutout
        if (cutout == null || !viewModel.pref.viewer.drawBehindCutout)
            return

        val v = binding.primaryButtons
        val r = getActionableToolbarRect()
        val shouldFit = cutout.boundingRects.find { it.intersect(r) } != null

        if (v.fitsSystemWindows != shouldFit) {
            v.fitsSystemWindows = shouldFit
            ViewCompat.onApplyWindowInsets(v, WindowInsetsCompat(insets))
        }
    }

    private fun toast(@StringRes msgRes: Int) = Toast.makeText(activity, msgRes, Toast.LENGTH_SHORT).show()

    private fun resetZoom() {
        viewModel.resetZoom()
        toast(R.string.msg_zoom_reset)
    }

    private fun resetZoomToDefault() {
        viewModel.resetZoomToDefault()
        toast(R.string.msg_zoom_reset_default)
    }

    private fun toggleZoomLock(enabled: Boolean) {
        viewModel.toggleZoomLock(enabled)
        toast(if (enabled) R.string.msg_zoom_locked else R.string.msg_zoom_unlocked)
    }

    private fun saveZoom() {
        viewModel.saveZoom()
        toast(R.string.msg_zoom_saved)
    }

    private fun setupViewModeSelection() {
        val viewModeButtonMap = mapOf(
                ServerProfile.VIEW_MODE_NORMAL to R.id.view_mode_normal,
                ServerProfile.VIEW_MODE_NO_INPUT to R.id.view_mode_no_input,
                ServerProfile.VIEW_MODE_NO_VIDEO to R.id.view_mode_no_video
        )

        binding.viewModeGroup.let { group ->
            check(group.childCount == viewModeButtonMap.size)

            viewModel.activeViewMode.observe(activity) {
                group.check(viewModeButtonMap[it] ?: R.id.view_mode_normal)
            }

            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    if (viewModel.state.value.isConnected) { // Make sure profile is available
                        val newMode = viewModeButtonMap.entries.first { it.value == checkedId }.key
                        if (viewModel.activeViewMode.value != newMode) {
                            viewModel.setViewMode(newMode)
                            showViewModeChangeToast(newMode)
                        }
                    }
                    close()
                }
            }
        }
    }

    private fun showViewModeChangeToast(newMode: Int) {
        when (newMode) {
            ServerProfile.VIEW_MODE_NORMAL -> toast(R.string.msg_normal_view_mode)
            ServerProfile.VIEW_MODE_NO_INPUT -> toast(R.string.msg_input_disabled)
        }
    }

    private fun setupGestureStyleSelection() {
        val styleButtonMap = mapOf(
                "auto" to R.id.gesture_style_auto,
                "touchscreen" to R.id.gesture_style_touchscreen,
                "touchpad" to R.id.gesture_style_touchpad
        )

        binding.gestureStyleGroup.let { group ->
            viewModel.activeGestureStyle.observe(activity) {
                // Retrieve gesture style from profile,
                // because activeGestureStyle doesn't contain the 'auto' option
                group.check(styleButtonMap[viewModel.profile.gestureStyle] ?: -1)
            }

            group.setOnCheckedChangeListener { _, id ->
                if (viewModel.state.value.isConnected) { // Make sure profile is available
                    val newStyle = styleButtonMap.entries.first { it.value == id }.key
                    viewModel.setGestureStyle(newStyle)
                }
                close()
            }
        }
    }

    private fun onStateChange(state: State) {
        if (Build.VERSION.SDK_INT >= 29)
            updateGestureExclusionRect()

        updateLockMode(state.isConnected)
        openerButton.isVisible = openWithButton && state.isConnected
    }

    private fun updateLockMode(isConnected: Boolean) {
        if (isConnected && openWithSwipe)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED)
        else
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    /**
     * Returns a rectangle covering the area occupied by primary buttons in opened state.
     * Returned rectangle is in [drawerLayout]'s coordinate space.
     */
    private fun getActionableToolbarRect(): Rect {
        val v = drawerView
        val r = Rect(v.left, binding.primaryButtons.top, v.right, binding.primaryButtons.bottom)

        if (r.left < 0) r.offset(v.width, 0)                          // closed along left edge
        else if (r.right > drawerLayout.right) r.offset(-v.width, 0)  // closed along right edge

        return r
    }


    /**
     * Setup gravity & layout direction
     */
    @SuppressLint("RtlHardcoded")
    private fun setupAlignment() {
        val gravity = if (viewModel.pref.viewer.toolbarAlignment == "start") GravityCompat.START else GravityCompat.END

        drawerView.layoutParams = (drawerView.layoutParams as DrawerLayout.LayoutParams).apply {
            this.gravity = gravity
        }

        openerButton.layoutParams = (openerButton.layoutParams as FrameLayout.LayoutParams).apply {
            this.gravity = gravity
        }

        // Before layout pass, layoutDirection should be retrieved from Activity config
        val layoutDirection = activity.resources.configuration.layoutDirection
        val isLeftAligned = Gravity.getAbsoluteGravity(gravity, layoutDirection) == Gravity.LEFT

        // We need the layout direction based on alignment rather than language/locale
        // so that flyouts and button icons are properly ordered.
        drawerView.layoutDirection = if (isLeftAligned) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        openerButton.layoutDirection = drawerView.layoutDirection

        // Let the gesture group have natural layout as it contains text elements
        binding.gestureStyleGroup.layoutDirection = layoutDirection
    }

    /**
     * Setup gesture exclusion updates
     */
    private fun setupGestureExclusionRect() {
        if (Build.VERSION.SDK_INT >= 29) {
            binding.primaryButtons.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateGestureExclusionRect()
            }
        }
    }

    /**
     * Add System Gesture exclusion rects to allow toolbar opening when gesture navigation is active.
     * Note: Some ROMs, e.g. MIUI, completely ignore whatever is set here.
     */
    @RequiresApi(29)
    private fun updateGestureExclusionRect() {
        if (!openWithSwipe || !viewModel.state.value.isConnected) {
            drawerLayout.systemGestureExclusionRects = listOf()
        } else {
            // Area covered by primaryButtons, in drawerLayout's coordinate space
            val rect = getActionableToolbarRect()

            if (viewModel.pref.viewer.fullscreen) {
                // For fullscreen activities, Android does not enforce the height limit of exclusion area.
                // We could use the entire height for opening toolbar, but that will completely disable gestures.
                // So we pad by one-third of available space in each direction
                val padding = (drawerLayout.height - rect.height()) / 6
                if (padding > 0) {
                    rect.top -= padding
                    rect.bottom += padding
                }
            }

            drawerLayout.systemGestureExclusionRects = listOf(rect)
        }
    }

    private fun setupFlyouts() {
        flyouts += binding.viewModesToggle to binding.viewModeGroup
        flyouts += binding.gestureStyleToggle to binding.gestureStyleGroup
        flyouts += binding.zoomOptionsToggle to binding.zoomOptionsGroup

        flyouts.values.forEach { it.isVisible = false }

        flyouts.keys.forEach { toggle ->
            toggle.setOnCheckedChangeListener { _, isChecked ->
                flyouts[toggle]?.isVisible = isChecked

                if (isChecked) // Close others
                    flyouts.keys.forEach { if (it != toggle) it.isChecked = false }
            }
        }
    }

    /**
     * Close flyouts after drawer is closed.
     *
     * We can't do this in [close] because that will change toolbar width _while_ drawer
     * is closing. This can conflict with close animation, and mess with internal calculations
     * of DrawerLayout, resulting in failure of close operation.
     */
    private fun setupFlyoutClose() {
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(closedView: View) {
                if (closedView == drawerView) {
                    flyouts.keys.forEach { it.isChecked = false }
                }
            }
        })
    }

    /**
     * Normally, drawers in [DrawerLayout] are closed by two gestures:
     * 1. Swipe 'on' the drawer
     * 2. Tap inside Scrim (dimmed region outside of drawer)
     *
     * Notably, swiping inside scrim area does NOT hide the drawer. This can be jarring
     * to users if drawer is relatively small & most of the layout area acts as scrim.
     * The toolbar drawer is affected by this issue.
     *
     * This function attempts to detect these swipe gestures and close the drawer
     * when they happen.
     *
     * Note: It will set a custom TouchListener on [drawerLayout].
     */
    @SuppressLint("ClickableViewAccessibility", "RtlHardcoded")
    private fun setupDrawerCloseOnScrimSwipe() {
        drawerLayout.setOnTouchListener(object : View.OnTouchListener {
            var drawerOpen = false
            var drawerGravity = Gravity.LEFT

            val detector = GestureDetector(drawerLayout.context, object : GestureDetector.SimpleOnGestureListener() {

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                    if ((drawerGravity == Gravity.LEFT && vX < 0) || (drawerGravity == Gravity.RIGHT && vX > 0)) {
                        close()
                        drawerOpen = false
                    }
                    return true
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    drawerOpen = drawerLayout.isDrawerOpen(drawerView)
                    drawerGravity = Gravity.getAbsoluteGravity(
                            (drawerView.layoutParams as DrawerLayout.LayoutParams).gravity,
                            drawerLayout.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK
                }

                if (drawerOpen)
                    detector.onTouchEvent(event)

                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOpenerButton() {
        if (!openWithButton)
            return

        val runInfo = viewModel.pref.runInfo
        var verticalBias = runInfo.toolbarOpenerBtnVerticalBias
        val openerButtonParent = (openerButton.parent as ViewGroup)

        val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val parentHeight = openerButtonParent.height
                val parentTouchY = openerButton.y + e2.y
                verticalBias = parentTouchY / parentHeight
                updateOpenerBtnPosition(openerButton, verticalBias)
                return true
            }
        }
        val gestureDetector = GestureDetector(activity, gestureListener)
        gestureDetector.setIsLongpressEnabled(false)

        openerButton.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP && verticalBias != runInfo.toolbarOpenerBtnVerticalBias)
                runInfo.toolbarOpenerBtnVerticalBias = verticalBias // Save position

            gestureDetector.onTouchEvent(e)
            v.onTouchEvent(e)
        }
        openerButton.setOnClickListener { open() }

        openerButtonParent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateOpenerBtnPosition(openerButton, verticalBias)
        }
    }

    private fun updateOpenerBtnPosition(btn: View, verticalBias: Float) {
        val parentHeight = (btn.parent as ViewGroup).height
        val minY = btn.marginTop
        val maxY = parentHeight - btn.height - btn.marginBottom
        val y = (parentHeight * verticalBias).toInt().coerceAtMost(maxY).coerceAtLeast(minY)
        btn.y = y.toFloat()
    }
}