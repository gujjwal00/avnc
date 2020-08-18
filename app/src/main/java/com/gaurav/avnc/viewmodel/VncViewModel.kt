/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.ui.vnc.FrameView
import com.gaurav.avnc.vnc.UserCredential
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * ViewModel for VncActivity
 */
class VncViewModel(app: Application) : BaseViewModel(app), VncClient.Observer {

    companion object {
        const val TAG = "VncViewModel"
    }

    /**
     * Current client instance
     */
    val client by lazy { VncClient(this) }

    /**
     * Profile used for current connection.
     * Only valid after [init] has been called.
     */
    lateinit var profile: VncProfile

    /**
     * Fired when [VncClient] has asked for credential. It is used to
     * show Credentials dialog to user.
     *
     * Boolean value of this event is true if `username` is also required.
     * It is false if only password is required.
     */
    val credentialRequiredEvent = LiveEvent<Boolean>()

    /**
     * Used for credential transfer between credential dialog (producer)
     * and the thread doing VNC initialization (consumer).
     *
     * After firing 'credential required' event, consumer will block
     * waiting for credentials. After producer has received credentials from
     * user, it will put them in this queue allowing consumer to continue.
     */
    val credentialQueue = LinkedBlockingQueue<UserCredential>()

    /**
     * Holds a weak reference to [FrameView] instance.
     *
     * This is used to tell [FrameView] to re-render its content when VncClient's
     * framebuffer is updated. Instead of of using LiveData/LiveEvent, we keep a
     * weak reference because:
     *
     *      1. It avoids a context-switch to UI thread. Rendering request to
     *         a GlSurfaceView can be sent from any thread.
     *
     *      2. We don't have to invoke the whole ViewModel machinery just for
     *         a single call to FrameView.
     */
    var frameViewRef = WeakReference<FrameView>(null)

    /**
     * Used for sending events to remote server.
     */
    private val sender = Executors.newSingleThreadExecutor()

    /**
     * Whether connection has been initialized.
     */
    private var initialized = false


    /**
     * Initialize VNC connection using given profile.
     */
    fun init(profile: VncProfile) {
        if (initialized)
            return

        initialized = true
        this.profile = profile

        viewModelScope.launch(Dispatchers.IO) {
            client.init(profile.host, profile.port)

            while (isActive) {
                client.processServerMessage(1000 * 1000)
            }

            cleanup()
        }
    }

    /**
     * Releases all resources and terminates the connection.
     *
     * It is called from receiver coroutine.
     */
    private fun cleanup() {
        sender.shutdownNow()

        if (!sender.isTerminated) try {
            sender.awaitTermination(5, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to wait for sender termination.", t)
        }

        if (!sender.isTerminated)
            Log.e(TAG, "Could not shutdown sender thread.")

        client.cleanup()
    }


    fun sendPointerEvent(x: Int, y: Int, mask: Int) = sender.execute {
        client.sendPointerEvent(x, y, mask)
    }

    fun sendKeyEvent(key: Long, isDown: Boolean) = sender.execute {
        client.sendKeyEvent(key, isDown)
    }

    fun sendCutText(text: String) = sender.execute {
        client.sendCutText(text)
    }

    /**
     * Called when remote server has asked for password.
     */
    override fun rfbGetPassword(): String {
        if (!profile.password.isBlank())
            return profile.password

        return obtainCredential(false).password
    }

    /**
     * Called when remote server has asked for both username & password.
     */
    override fun rfbGetCredential(): UserCredential {
        if (!profile.username.isBlank() && !profile.password.isBlank())
            return UserCredential(profile.username, profile.password)

        return obtainCredential(true)
    }

    /**
     * Used for obtaining credentials from user.
     * This is a blocking operation and will wait until credential dialog
     * is finished.
     */
    private fun obtainCredential(usernameRequired: Boolean): UserCredential {
        credentialQueue.clear()
        credentialRequiredEvent.post(usernameRequired)
        return credentialQueue.take()   //Blocking call
    }

    override fun rfbFinishedFrameBufferUpdate() {
        frameViewRef.get()?.requestRender()
    }

    override fun rfbBell() {} //Implement
    override fun rfbGotXCutText(text: String) = toClipboard(text)
    override fun rfbHandleCursorPos(x: Int, y: Int): Boolean = true

}