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

/**
 * Input handler for remote session.
 * All input events are passed through here.
 *
 * This is a simple hub, used for logging, filtering, notifications etc.
 * Main input processing happens in [TouchHandler] & [KeyHandler].
 */
class InputHandler(private val activity: VncActivity) {
    private var dispatcher: Dispatcher? = null
    private var touchHandler: TouchHandler? = null
    private var keyHandler: KeyHandler? = null

    /**
     * List of listeners to be notified after a [KeyEvent] is handled
     */
    val onAfterKeyEventListeners = mutableListOf<(KeyEvent) -> Unit>()

    fun onStateChanged(isConnected: Boolean) {
        if (isConnected) {
            val viewModel = activity.viewModel
            dispatcher = Dispatcher(activity)
            touchHandler = TouchHandler(activity.binding.frameView, dispatcher!!, viewModel.pref)
            keyHandler = KeyHandler(dispatcher!!, viewModel.pref)
            keyHandler!!.enableMacOSCompatibility = viewModel.client.isConnectedToMacOS()
        } else {
            dispatcher = null
            touchHandler = null
            keyHandler = null
        }
    }

    fun onKeyEvent(keyEvent: KeyEvent): Boolean {
        val handled = keyHandler?.onKeyEvent(keyEvent) == true || interceptBackPressFromMouse(keyEvent)
        onAfterKeyEventListeners.forEach { it(keyEvent) }
        return handled
    }

    fun onVkKeyEvent(keyEvent: KeyEvent): Boolean {
        val handled = keyHandler?.onVkKeyEvent(keyEvent) == true
        onAfterKeyEventListeners.forEach { it(keyEvent) }
        return handled
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return touchHandler?.onTouchEvent(event) == true
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return touchHandler?.onGenericMotionEvent(event) == true
    }

    fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        return touchHandler?.onCapturedPointerEvent(event) == true
    }

    fun onHoverEvent(event: MotionEvent): Boolean {
        return touchHandler?.onHoverEvent(event) == true
    }

    /*********************************************************************************************/

    private fun interceptBackPressFromMouse(keyEvent: KeyEvent): Boolean {
        //It seems that some device manufacturers are hell-bent on making developers'
        //life miserable. In their infinite wisdom, they decided that Android apps don't
        //need Mouse right-click events. It is hardcoded to act as back-press, without
        //giving apps a chance to handle it. For better or worse, they set the 'source'
        //for such key events to Mouse, enabling the following workarounds.
        if (touchHandler != null &&
            keyEvent.keyCode == KeyEvent.KEYCODE_BACK &&
            keyEvent.scanCode == 0 &&
            keyEvent.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY == 0 &&
            InputDevice.getDevice(keyEvent.deviceId)?.supportsSource(InputDevice.SOURCE_MOUSE) == true &&
            activity.viewModel.pref.input.interceptMouseBack) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN)
                touchHandler!!.onMouseBack()
            return true
        }
        return false
    }
}