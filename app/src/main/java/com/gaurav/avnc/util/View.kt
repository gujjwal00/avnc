/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.animation.LayoutTransition
import android.view.ViewGroup


/**
 * Enables animations for layout changes inside [viewGroup].
 *
 * Same as setting android:animateLayoutChanges="true" in XML,
 * but ensures parents of [viewGroup] are not affected by any animation.
 * Parent animations can cause wierd artifacts & possibly ANRs in certain scenarios.
 *
 * This should NOT be used if [viewGroup] size depends on layout of child views.
 */
fun enableChildLayoutTransitions(viewGroup: ViewGroup) {
    debugCheck(viewGroup.layoutParams?.height != ViewGroup.LayoutParams.WRAP_CONTENT)
    debugCheck(viewGroup.layoutParams?.width != ViewGroup.LayoutParams.WRAP_CONTENT)

    viewGroup.layoutTransition = LayoutTransition().apply {
        setAnimateParentHierarchy(false)
    }
}