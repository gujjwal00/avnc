/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.animation.ValueAnimator
import android.util.Log
import android.view.View
import androidx.annotation.Keep
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.RecyclerView
import com.gaurav.avnc.databinding.FragmentServersBinding
import com.gaurav.avnc.databinding.ItemServerBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.home.adapter.ServersAdapter

/**
 * All of the experimental stuff is dumped here.
 */
class Experimental {

    /**
     * This feature allows us to show indicator (small pulsating circle) for already
     * saved profiles if we find the same server in Discovery. Main benefit is to
     * immediately know about reachable servers.
     *
     * Why Experimental:
     * - For this to be most useful the user needs to run Discovery frequently. Otherwise
     *   indicators will become stale.
     * - Because of Discovery and and animation we will consume slightly more battery.
     *
     * One other approach for this implementation is to have a field in [ServerProfile],
     * update that field in [com.gaurav.avnc.viewmodel.HomeViewModel] and let Data Binding
     * automatically render the indicator. But that approach has some disadvantages:
     *
     * - It complicates code in other classes too much.
     * - Because each Indicator view has independent animator, they pulse out of sync.
     * - I simply don't like that approach.
     */
    class Indicator {

        /**
         * Local reference to discovered servers.
         */
        private var discoveredServers: List<ServerProfile>? = null

        /**
         * Maps each [ItemServerBinding.indicator] view to the server profile being
         * rendered by its container.
         */
        private val profileMap = HashMap<View, ServerProfile>(5)

        /**
         * Maps each [ItemServerBinding.indicator] view to its availability state.
         * If state is true, the view will be animated otherwise it will be hidden.
         */
        private val animatorMap = HashMap<View, Boolean>(5)

        /**
         * Animator for 'alpha' property of indicator views.
         */
        private val animator = ValueAnimator.ofFloat(0F, 1F).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { onAnimationUpdate() }
        }

        /**
         * Initialize Indicator support.
         *
         * Note: Viewmodel & lifecycle must have been assigned to [binding].
         */
        fun setup(binding: FragmentServersBinding, adapter: ServersAdapter) {
            Log.i(javaClass.simpleName, "Setting up Experimental Indicator")

            // There are 4 event sources which we are interested in:

            // 1 - Discovery: Need to know which items should show indicator.
            binding.viewModel!!.discovery.servers.observe(binding.lifecycleOwner!!) {
                discoveredServers = it

                for ((v, p) in profileMap)
                    animatorMap[v] = isServerDiscovered(p)

                updateAnimator()
            }

            // 2 - RV Binding: We need to know which RV Item View is rendering which profile.
            adapter.bindListener = { holder, profile ->
                if (holder.binding is ItemServerBinding) {
                    val view = holder.binding.indicator
                    profileMap[view] = profile
                    animatorMap[view] = isServerDiscovered(profile)
                }
            }

            // 3 - RV Views: We need to clear Views which are no longer used by RecyclerView.
            binding.serversRv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {}
                override fun onChildViewDetachedFromWindow(view: View) {
                    profileMap.remove(view)
                    animatorMap.remove(view)
                }
            })

            // 4 - Activity Lifecycle: We don't want to run animation if our activity is not in front.
            binding.lifecycleOwner!!.lifecycle.addObserver(object : LifecycleObserver {
                @Keep
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onStart() = updateAnimator()

                @Keep
                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onStop() = animator.end()
            })
        }

        private fun isServerDiscovered(p: ServerProfile): Boolean {
            return discoveredServers?.find { p.address == it.address && p.port == it.port } != null
        }

        private fun updateAnimator() {
            if (discoveredServers?.isNotEmpty() == true && !animator.isStarted)
                animator.start()
            else
                animator.end()
        }

        private fun onAnimationUpdate() {
            for ((v, animate) in animatorMap) {
                if (animate) {
                    v.alpha = animator.animatedValue as Float
                    v.visibility = View.VISIBLE
                } else {
                    v.visibility = View.GONE
                }
            }
        }
    }
}