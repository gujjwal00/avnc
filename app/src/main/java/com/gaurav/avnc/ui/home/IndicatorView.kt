/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.gaurav.avnc.model.ServerProfile

/**
 * [IndicatorView] highlights rediscovered servers. Main benefit is to immediately
 * identify reachable servers, without checking Discovery tab.
 *
 * Don't know about other users, but I really like this feature.
 * It complements discovery-autorun very well.
 */
class IndicatorView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private object Controller {
        private val animatedViews = ArrayList<IndicatorView>()
        private val animator = ValueAnimator.ofFloat(0F, 1F).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animatedViews.forEach { view -> view.alpha = animatedValue as Float } }
        }

        fun add(view: IndicatorView) {
            animatedViews.add(view)
            if (!animator.isStarted) animator.start()
        }

        fun remove(view: IndicatorView) {
            animatedViews.remove(view)
            if (animatedViews.isEmpty() && animator.isStarted) animator.end()
        }
    }

    private var profile: ServerProfile? = null
    private var indicatedProfiles: LiveData<List<ServerProfile>>? = null
    private val observer = Observer<List<ServerProfile>> { isVisible = it?.contains(profile) == true }

    fun setup(profile: ServerProfile, indicatedProfiles: LiveData<List<ServerProfile>>) {
        this.profile = profile
        this.indicatedProfiles = indicatedProfiles
        indicatedProfiles.removeObserver(observer)
        indicatedProfiles.observeForever(observer)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this) {
            if (visibility == VISIBLE) Controller.add(this)
            else Controller.remove(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Controller.remove(this)
        indicatedProfiles?.removeObserver(observer)
    }
}