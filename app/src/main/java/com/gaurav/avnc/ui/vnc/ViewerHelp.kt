/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.gaurav.avnc.databinding.ViewerHelpBinding

/**
 * Two of the most common question asked by new users are:
 * - Where is the toolbar, or how to open it
 * - How to cleanly exit a session
 *
 * This class aims to answer these questions. When user starts a session for
 * the first time, this guide is shown. It consists of two pages: one shows
 * how to open the toolbar drawer, other tells about the Back navigation button.
 */
class ViewerHelp {

    fun onConnected(activity: VncActivity) {
        if (!activity.viewModel.pref.runInfo.hasShownViewerHelp) {
            initHelpView(activity)
        }
    }

    private fun initHelpView(activity: VncActivity) {
        val binding = ViewerHelpBinding.inflate(activity.layoutInflater, activity.binding.drawerLayout, false)
        activity.binding.drawerLayout.addView(binding.root, 1)

        // Open help view with animation
        binding.root.alpha = 0f
        binding.root.animate().alpha(1f).setStartDelay(500)
        binding.root.setOnClickListener { /* Consume clicks to stop them from passing through to FrameView */ }


        initAnimatedDrawable(binding.toolbarAnimation)
        binding.nextBtn.setOnClickListener {
            binding.page1.isVisible = false
            binding.page2.isVisible = true
            initAnimatedDrawable(binding.navbarAnimation)
        }
        binding.endBtn.setOnClickListener {
            activity.viewModel.pref.runInfo.hasShownViewerHelp = true
            binding.root.animate().alpha(0f).withEndAction {
                activity.binding.drawerLayout.removeView(binding.root)
            }
        }
    }

    /**
     * AnimatedVectorDrawable doesn't support looping, so we have to implement it manually.
     */
    private fun initAnimatedDrawable(hostView: ImageView) {
        // Need to use compat library fot API 21 support
        val animatedDrawable = hostView.drawable as? AnimatedVectorDrawableCompat
                               ?: hostView.drawable as AnimatedVectorDrawable
        AnimatedVectorDrawableCompat.registerAnimationCallback(animatedDrawable, object : Animatable2Compat.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) {
                hostView.postDelayed({ if (hostView.isAttachedToWindow) animatedDrawable.start() }, 200)
            }
        })
        animatedDrawable.start()
    }
}