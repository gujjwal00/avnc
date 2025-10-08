/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.viewmodel.VncViewModel.State.Companion.isConnected

/**
 * Input handler for remote session.
 * All input events are passed through here.
 *
 * This is a simple hub, used for logging, filtering, notifications etc.
 * Main input processing happens in [TouchHandler] & [KeyHandler].
 */
class InputHandler(private val activity: VncActivity) {
    private val viewModel by lazy { activity.viewModel }
    private val dispatcher by lazy { Dispatcher(activity) }
    private val touchHandler by lazy { TouchHandler(activity.binding.frameView, dispatcher, viewModel.pref) }
    private val keyHandler by lazy { KeyHandler(dispatcher, viewModel.pref) }
    private var isConnected = false


    /**
     * List of listeners to be notified after a [KeyEvent] is handled
     */
    val onAfterKeyEventListeners = mutableListOf<(KeyEvent) -> Unit>()

    fun onStateChanged(state: VncViewModel.State) {
        isConnected = state.isConnected
        if (isConnected)
            keyHandler.enableMacOSCompatibility = viewModel.client.isConnectedToMacOS()
    }

    fun onKeyEvent(keyEvent: KeyEvent) = ifConnected {
        val handled = keyHandler.onKeyEvent(keyEvent) || interceptBackPressFromMouse(keyEvent)
        onAfterKeyEventListeners.forEach { it(keyEvent) }
        handled
    }

    fun onVkKeyEvent(keyEvent: KeyEvent) = ifConnected {
        val handled = keyHandler.onVkKeyEvent(keyEvent)
        onAfterKeyEventListeners.forEach { it(keyEvent) }
        handled
    }

    fun onTouchEvent(event: MotionEvent) = ifConnected {
        touchHandler.onTouchEvent(event)
    }

    fun onGenericMotionEvent(event: MotionEvent) = ifConnected {
        touchHandler.onGenericMotionEvent(event)
    }

    fun onCapturedPointerEvent(event: MotionEvent) = ifConnected {
        touchHandler.onCapturedPointerEvent(event)
    }

    fun onHoverEvent(event: MotionEvent) = ifConnected {
        touchHandler.onHoverEvent(event)
    }

    /*********************************************************************************************/

    private inline fun ifConnected(block: () -> Boolean): Boolean {
        return isConnected && block()
    }

    private fun interceptBackPressFromMouse(keyEvent: KeyEvent): Boolean {
        //It seems that some device manufacturers are hell-bent on making developers'
        //life miserable. In their infinite wisdom, they decided that Android apps don't
        //need Mouse right-click events. It is hardcoded to act as back-press, without
        //giving apps a chance to handle it. For better or worse, they set the 'source'
        //for such key events to Mouse, enabling the following workarounds.
        if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK &&
            keyEvent.scanCode == 0 &&
            keyEvent.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY == 0 &&
            InputDevice.getDevice(keyEvent.deviceId)?.supportsSource(InputDevice.SOURCE_MOUSE) == true &&
            viewModel.pref.input.interceptMouseBack) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN)
                touchHandler.onMouseBack()
            return true
        }
        return false
    }
}