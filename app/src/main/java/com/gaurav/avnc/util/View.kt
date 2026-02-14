/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.animation.LayoutTransition
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat


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

/**
 * [AnimatedVectorDrawable] doesn't natively support looping.
 * This function implements it manually.
 *
 * [hostView]'s [ImageView.drawable] must be a [AnimatedVectorDrawable].
 */
fun loopAnimatedDrawable(hostView: ImageView) {
    // Need to use compat library fot API 21 support
    val animatedDrawable = hostView.drawable as? AnimatedVectorDrawableCompat
                           ?: hostView.drawable as AnimatedVectorDrawable
    AnimatedVectorDrawableCompat.registerAnimationCallback(animatedDrawable, object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            hostView.postDelayed({
                                     if (hostView.isAttachedToWindow)
                                         animatedDrawable.start()
                                 }, 200)
        }
    })
    animatedDrawable.start()
}