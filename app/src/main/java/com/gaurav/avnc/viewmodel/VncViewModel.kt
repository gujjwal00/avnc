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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.FrameState
import com.gaurav.avnc.ui.vnc.FrameView
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.UserCredential
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.ref.WeakReference

/**
 * ViewModel for VncActivity
 *
 * Connection
 * ==========
 *
 * At construction, we instantiate a [VncClient] referenced by [client]. Then
 * activity starts the connection by calling [initConnection] which starts a coroutine to
 * handle connection setup.
 *
 * After successful connection, we continue to operate normally until the remote
 * server closes the connection, or an error occurs. Once disconnected, we
 * wait for the activity to finish and then cleanup any acquired resources.
 *
 * Currently, lifecycle of [client] is tied to this view model. So one [VncViewModel]
 * manages only one [VncClient].
 *
 *
 * Threading
 * =========
 *
 * Receiver thread :- This thread is started (as a coroutine) in [launchConnection].
 * It handles the protocol initialization, and after that processes incoming messages.
 * Most of the callbacks of [VncClient.Observer] are invoked on this thread. In most
 * cases it is stopped when activity is finished and this view model is cleaned up.
 *
 * Sender thread :- This thread is created (as an executor) by [messenger]. It is
 * used to send messages to remote server. We use this dedicated thread instead
 * of coroutines to preserve the order of sent messages.
 *
 * UI thread :- Main thread of the app. Used for updating UI and controlling other
 * Threads. This is where [frameState] is updated.
 *
 * Renderer thread :- This is managed by [FrameView] and used for rendering frame
 * via OpenGL ES. [frameState] is read from this thread to decide how/where frame
 * should be drawn.
 */
class VncViewModel(app: Application) : BaseViewModel(app), VncClient.Observer {

    val client = VncClient(this)

    /**
     * Client state is exposed as live data here to allow dynamic data binding
     * from layouts.
     */
    val clientState = MutableLiveData(client.state)

    /**
     * Reason for [clientState] being [VncClient.State.Disconnected].
     */
    val disconnectReason = MutableLiveData("")

    /**
     * [ServerProfile] used for current connection.
     */
    var profile = ServerProfile()

    /**
     * Fired when [VncClient] has asked for credential. It is used to
     * show Credentials dialog to user and return the result to receiver
     * thread.
     *
     * Value of this request is true if username & password are required
     * and false if only password is required.
     */
    val credentialRequest = LiveRequest<Boolean, UserCredential>(UserCredential(), viewModelScope)

    /**
     * List of known credentials. Used for providing suggestion when
     * new credentials are required.
     */
    val knownCredentials by lazy { serverProfileDao.getCredentials() }

    /**
     * Holds a weak reference to [FrameView] instance.
     *
     * This is used to tell [FrameView] to re-render its content when VncClient's
     * framebuffer is updated. Instead of using LiveData/LiveEvent, we keep a
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
     */
    val messenger = Messenger(client)

    private val sshTunnel = SshTunnel(this)

    /**
     * Used to confirm unknown hosts.
     */
    val sshHostKeyVerifyRequest = LiveRequest<HostKey, Boolean>(false, viewModelScope)


    /**************************************************************************
     * Connection management
     **************************************************************************/

    /**
     * Because [initConnection] can be called multiple times due to activity restarts,
     * initialization inside it is performed only once.
     */
    private var initialized = false

    /**
     * Initialize VNC connection using given profile.
     */
    fun initConnection(profile: ServerProfile) {
        if (initialized)
            return

        initialized = true
        this.profile = profile

        launchConnection()
    }

    private fun launchConnection() {
        viewModelScope.launch(Dispatchers.IO) {

            runCatching {

                configureClient()
                connect()
                processMessages()

            }.onFailure {
                client.disconnect()
                if (it is IOException)
                    disconnectReason.postValue(it.message)
                Log.e("ReceiverCoroutine", "Connection failed", it)
            }

            //Wait until activity is finished and viewmodel is cleaned up.
            runCatching { awaitCancellation() }
            cleanup()
        }
    }

    private fun configureClient() {
        client.configure(profile.viewOnly, profile.securityType, profile.useLocalCursor)

        if (profile.useRepeater)
            client.setupRepeater(profile.idOnRepeater)
    }

    private fun connect() {
        val channel = profile.channelType
        if (channel != ServerProfile.CHANNEL_TCP && channel != ServerProfile.CHANNEL_SSH_TUNNEL)
            throw IOException("Unsupported Channel: $channel")

        var host = profile.host
        var port = profile.port

        if (channel == ServerProfile.CHANNEL_SSH_TUNNEL) {
            sshTunnel.open()
            host = sshTunnel.localHost
            port = sshTunnel.localPort
        }

        if (!client.connect(host, port))
            throw IOException(client.getLastErrorStr())
    }

    private fun processMessages() {
        while (viewModelScope.isActive && client.processServerMessage()) {
            //Message Loop
        }

        disconnectReason.postValue(client.getLastErrorStr())
    }

    private fun cleanup() {
        messenger.cleanup()
        client.cleanup()
        sshTunnel.close()
    }

    /**
     * Can be used to persist any changes made to [profile]
     */
    fun saveProfile() {
        if (profile.ID != 0L)
            asyncIO { serverProfileDao.update(profile) }
    }

    /**************************************************************************
     * Frame management
     **************************************************************************/

    fun updateZoom(scaleFactor: Float, fx: Float, fy: Float) {
        val appliedScaleFactor = frameState.updateZoom(scaleFactor)

        //Calculate how much the focus would shift after scaling
        val dfx = (fx - frameState.frameX) * (appliedScaleFactor - 1)
        val dfy = (fy - frameState.frameY) * (appliedScaleFactor - 1)

        //Translate in opposite direction to keep focus fixed
        frameState.pan(-dfx, -dfy)

        frameViewRef.get()?.requestRender()
    }

    fun resetZoom() {
        frameState.resetZoom()
        frameViewRef.get()?.requestRender()
    }

    fun panFrame(deltaX: Float, deltaY: Float) {
        frameState.pan(deltaX, deltaY)
        frameViewRef.get()?.requestRender()
    }

    fun moveFrameTo(x: Float, y: Float) {
        frameState.moveTo(x, y)
        frameViewRef.get()?.requestRender()
    }

    /**************************************************************************
     * Clipboard Sync
     **************************************************************************/

    fun sendClipboardText() {
        viewModelScope.launch(Dispatchers.Main) {
            if (pref.server.clipboardSync)
                getClipboardText()?.let { messenger.sendClipboardText(it) }
        }
    }

    private fun receiveClipboardText(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            if (pref.server.clipboardSync)
                setClipboardText(text)
        }
    }

    /**************************************************************************
     * [VncClient.Observer] Implementation
     **************************************************************************/

    /**
     * Called when remote server has asked for password.
     */
    override fun onPasswordRequired(): String {
        if (profile.password.isNotBlank())
            return profile.password

        return obtainCredential(false).password
    }

    /**
     * Called when remote server has asked for both username & password.
     */
    override fun onCredentialRequired(): UserCredential {
        if (profile.username.isNotBlank() && profile.password.isNotBlank())
            return UserCredential(profile.username, profile.password)

        return obtainCredential(true)
    }

    private fun obtainCredential(usernameRequired: Boolean): UserCredential {
        return credentialRequest.requestResponse(usernameRequired)   //Blocking call
    }

    override fun onFramebufferUpdated() {
        frameViewRef.get()?.requestRender()
    }

    override fun onGotXCutText(text: String) {
        receiveClipboardText(text)
    }

    override fun onClientStateChanged(newState: VncClient.State) {
        clientState.postValue(newState)

        if (newState == VncClient.State.Connected)
            sendClipboardText() //Initial sync
    }

    override fun onFramebufferSizeChanged(width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.Main) {
            frameState.setFramebufferSize(width.toFloat(), height.toFloat())
        }
    }
}