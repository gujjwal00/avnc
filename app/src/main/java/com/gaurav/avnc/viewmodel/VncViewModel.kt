/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.ui.vnc.FrameState
import com.gaurav.avnc.ui.vnc.FrameView
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.UserCredential
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * ViewModel for VncActivity
 */
class VncViewModel(app: Application) : BaseViewModel(app), VncClient.Observer {

    companion object {
        const val TAG = "VncViewModel"
    }

    /**
     * Current client instance.
     */
    val client = VncClient(this)

    /**
     * Information about client.
     */
    val clientInfo = MutableLiveData(VncClient.Info())

    /**
     * Bookmark which triggered this connection.
     *
     * Even though only [profile] is required for connection, we require that all
     * connection request to [connect] comes in form of a [Bookmark]. This unifies
     * information flow from HomeActivity to VncActivity.
     *
     * For connection which are not started from a real bookmark (ex: from Recent)
     * this will simply wrap that profile.
     */
    var bookmark = Bookmark()

    /**
     * Profile used for current connection.
     */
    var profile = bookmark.profile

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
     * After firing [credentialRequiredEvent] event, consumer will block
     * waiting for credentials. After producer has received credentials from
     * user, it will put them in this queue allowing consumer to continue.
     */
    val credentialQueue = LinkedBlockingQueue<UserCredential>()

    /**
     * List of known credentials. Used for providing suggestion when
     * new credentials are required.
     *
     * For simplicity, we are only retrieving credentials from bookmarks table.
     */
    val knownCredentials by lazy { bookmarkDao.getCredentials() }

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
     * Holds information about scaling, translation etc.
     */
    val frameState = FrameState(pref)

    /**
     * Used for sending events to remote server.
     * Only valid after VNC connection has been established.
     */
    lateinit var messenger: Messenger

    /**
     * Textual representation of [FrameState.zoomScale], updated during scale gesture.
     *
     * Setting its value to empty string will hide zoom level.
     */
    val zoomLevelText = MutableLiveData("")


    /**************************************************************************
     * Connection management
     **************************************************************************/

    /**
     * Whether connection has been initialized.
     */
    private var initialized = false

    /**
     * Initialize VNC connection using profile from given bookmark.
     */
    fun connect(bookmark: Bookmark) {
        if (initialized)
            return

        initialized = true
        this.bookmark = bookmark
        this.profile = bookmark.profile

        launchConnection()
    }

    /**
     * Connects to VNC server and then runs the message loop.
     */
    private fun launchConnection() {
        viewModelScope.launch(Dispatchers.IO) {

            if (client.init(profile.host, profile.port)) {

                try {
                    messenger = Messenger(client)

                    while (isActive && client.processServerMessage(1000 * 1000)) {
                        //Message Loop
                    }

                    messenger.shutdownNow()

                    delay(24 * 3600 * 1000)  //Wait for ViewModel cleanup
                } finally {
                    client.cleanup()
                }
            }
        }
    }

    /**
     * Disconnect VNC client.
     */
    fun disconnect() = client.setDisconnected()

    /**
     * Called when activity is finished.
     */
    override fun onCleared() {
        super.onCleared()

        //Put something in credential queue (just in case background thread is
        //stuck waiting for credentials)
        credentialQueue.offer(UserCredential())
    }

    /**************************************************************************
     * Frame management
     **************************************************************************/

    fun updateZoom(scaleFactor: Float, fx: Float, fy: Float) {
        val appliedScaleFactor = frameState.updateZoom(scaleFactor)

        //Calculate how much the focus would shift after scaling
        val dfx = (fx - frameState.translateX) * (appliedScaleFactor - 1)
        val dfy = (fy - frameState.translateY) * (appliedScaleFactor - 1)

        //Translate in opposite direction to keep focus fixed
        frameState.pan(-dfx, -dfy)

        frameViewRef.get()?.requestRender()
    }

    fun panFrame(deltaX: Float, deltaY: Float) {
        frameState.pan(deltaX, deltaY)
        frameViewRef.get()?.requestRender()
    }

    /**************************************************************************
     * [VncClient.Observer] Implementation
     **************************************************************************/

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
     * This is a blocking operation and will wait until credential dialog is finished.
     */
    private fun obtainCredential(usernameRequired: Boolean): UserCredential {
        credentialQueue.clear()
        credentialRequiredEvent.post(usernameRequired)
        return credentialQueue.take()   //Blocking call
    }

    override fun rfbFinishedFrameBufferUpdate() {
        frameViewRef.get()?.requestRender()
    }

    override fun rfbBell() {} //Nothing yet

    override fun rfbGotXCutText(text: String) = toClipboard(text)

    override fun rfbHandleCursorPos(x: Int, y: Int): Boolean = true

    override fun onClientInfoChanged(info: VncClient.Info) {
        viewModelScope.launch(Dispatchers.Main) {
            clientInfo.value = info
            frameState.setFramebufferSize(info.frameWidth.toFloat(), info.frameHeight.toFloat())
        }

        //TODO: This callback can be invoked more than once for same state.
        //      We should make sure we are not repeatedly saving same thing
        if (info.state == VncClient.State.Connected) {
            async {
                recentDao.insert(bookmark.toRecent().apply { connectedAt = Date().time })

                //If user has asked to remember credential then Credential Dialog
                //will put them in current profile (associated with bookmark).
                if (bookmark.ID != 0L) {
                    bookmarkDao.update(bookmark)
                }
            }
        }
    }
}