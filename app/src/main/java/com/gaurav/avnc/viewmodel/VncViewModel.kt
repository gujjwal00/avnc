/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.graphics.RectF
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.FrameScroller
import com.gaurav.avnc.ui.vnc.FrameState
import com.gaurav.avnc.ui.vnc.FrameView
import com.gaurav.avnc.util.LiveRequest
import com.gaurav.avnc.util.SingleShotFlag
import com.gaurav.avnc.util.broadcastWoLPackets
import com.gaurav.avnc.util.getClipboardText
import com.gaurav.avnc.util.getUnknownCertificateMessage
import com.gaurav.avnc.util.isCertificateTrusted
import com.gaurav.avnc.util.setClipboardText
import com.gaurav.avnc.util.trustCertificate
import com.gaurav.avnc.viewmodel.VncViewModel.State.Companion.isConnected
import com.gaurav.avnc.viewmodel.service.SshTunnel
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.UserCredential
import com.gaurav.avnc.vnc.VncClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.cert.X509Certificate
import kotlin.concurrent.thread

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

    /**
     * Connection lifecycle:
     *
     *            Created
     *               |
     *               v
     *          Connecting ----------+
     *               |               |
     *               v               |
     *           Connected           |
     *               |               |
     *               v               |
     *          Disconnected <-------+
     *
     */
    enum class State {
        Created,
        Connecting,
        Connected,
        Disconnected;

        companion object {
            val State?.isConnected get() = (this == Connected)
            val State?.isDisconnected get() = (this == Disconnected)
        }
    }

    lateinit var profile: ServerProfile

    /**
     * Live version of [profile], allows easier access from UI layer
     */
    val profileLive = MutableLiveData<ServerProfile>()

    val client = VncClient(this)

    /**
     * We have two places for connection state (both are synced):
     *
     * [VncClient.connected] - Simple boolean state, used most of the time
     * [state]               - More granular, used by observers & data binding
     */
    val state = MutableLiveData(State.Created)

    /**
     * Reason for disconnecting.
     */
    val disconnectReason = MutableLiveData("")

    /**
     * Fired when we need some credentials from user.
     * It will trigger the Login dialog.
     */
    val loginInfoRequest = LiveRequest<LoginInfo.Type, LoginInfo>(LoginInfo(), viewModelScope)

    /**
     * Fired to unlock saved servers.
     */
    val serverUnlockRequest = LiveRequest<Any?, Boolean>(false, viewModelScope)

    /**
     * List of saved profiles.
     * Used by login-autocompletion.
     */
    val savedProfiles by lazy { serverProfileDao.getLiveList() }

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
    val frameState = with(pref.viewer) { FrameState(zoomMin, zoomMax, perOrientationZoom) }

    /**
     * Used for scrolling/animating the frame.
     */
    val frameScroller = FrameScroller(this)

    /**
     * Currently active gesture style
     */
    val activeGestureStyle = MutableLiveData<String>()

    /**
     * Used for sending events to remote server.
     */
    val messenger = Messenger(client)

    private val sshTunnel = SshTunnel(this)

    /**
     * Used to confirm something with user before continuing.
     * This is mostly used to warn about unknown SSH host, x509 certificates etc.
     * This request accepts two strings: First is used as title, second contains the message.
     */
    val confirmationRequest = LiveRequest<Pair<String, String>, Boolean>(false, viewModelScope)

    /**
     * Indicates if this view model has been cleared.
     * Used by the receiver thread to control it's execution.
     */
    private val viewModelClearedFlag = SingleShotFlag()

    override fun onCleared() {
        super.onCleared()
        if (state.value == State.Connecting)
            client.interrupt()
        viewModelClearedFlag.set()
    }


    /**************************************************************************
     * Connection management
     **************************************************************************/

    /**
     * Initialize VNC connection.
     * It can be called multiple times due to activity restarts.
     */
    fun initConnection(profile: ServerProfile) {
        if (state.value == State.Created) {
            this.profile = profile
            profileLive.value = profile
            state.value = State.Connecting
            frameState.setZoom(profile.zoom1, profile.zoom2)
            applyProfileGestureStyle()
            launchConnection()
        }
    }

    private fun launchConnection() {
        thread(name = "VncConnection") {
            runCatching {

                preConnect()
                connect()
                processMessages()

            }.onFailure {
                if (it is IOException) disconnectReason.postValue(it.message)
                Log.e(javaClass.simpleName, "Connection failed", it)
            }

            state.postValue(State.Disconnected)
            cleanup()
        }
    }

    private fun preConnect() {
        if (profile.ID != 0L && pref.server.lockSavedServer)
            if (!serverUnlockRequest.requestResponse(null))
                throw IOException("Could not unlock server")

        client.configure(profile.viewOnly, profile.securityType, true  /* Hardcoded to true */,
                         profile.imageQuality, profile.useRawEncoding)

        if (profile.useRepeater)
            client.setupRepeater(profile.idOnRepeater)

        if (profile.enableWol)
            runCatching { broadcastWoLPackets(profile.wolMAC) }
                    .onFailure {
                        launchMain {
                            Toast.makeText(app, "Wake-on-LAN: ${it.message}", Toast.LENGTH_LONG).show()
                            Log.w(javaClass.simpleName, "Cannot send WoL packet", it)
                        }
                    }
    }

    private fun connect() {
        when (profile.channelType) {
            ServerProfile.CHANNEL_TCP ->
                client.connect(profile.host, profile.port)

            ServerProfile.CHANNEL_SSH_TUNNEL ->
                sshTunnel.open().use {
                    client.connect(it.host, it.port)
                }

            else -> throw IOException("Unknown Channel: ${profile.channelType}")
        }

        state.postValue(State.Connected)

        // Initial sync, slightly delayed to allow extended clipboard negotiations
        launchIO { delay(1000L); sendClipboardText() }
    }

    private fun processMessages() {
        while (viewModelClearedFlag.isNotSet)
            client.processServerMessage()
    }

    private fun cleanup() {
        //Wait until activity is finished and viewmodel is cleaned up.
        viewModelClearedFlag.await()

        messenger.cleanup()
        client.cleanup()
        sshTunnel.close()
    }

    /**
     * Can be used to persist any changes made to [profile]
     */
    fun saveProfile() {
        if (profile.ID != 0L)
            launch { serverProfileDao.update(profile) }
    }

    /**************************************************************************
     * Frame management
     **************************************************************************/

    fun updateZoom(scaleFactor: Float, fx: Float, fy: Float) {
        if (profile.fZoomLocked) return

        val appliedScaleFactor = frameState.updateZoom(scaleFactor)

        //Calculate how much the focus would shift after scaling
        val dfx = (fx - frameState.frameX) * (appliedScaleFactor - 1)
        val dfy = (fy - frameState.frameY) * (appliedScaleFactor - 1)

        //Translate in opposite direction to keep focus fixed
        frameState.pan(-dfx, -dfy)

        frameViewRef.get()?.requestRender()
    }

    fun resetZoom() {
        frameState.setZoom(1f, 1f)
        frameViewRef.get()?.requestRender()
    }

    fun resetZoomToDefault() {
        frameState.setZoom(profile.zoom1, profile.zoom2)
        frameViewRef.get()?.requestRender()
    }

    fun setZoom(zoom1: Float, zoom2: Float) {
        frameState.setZoom(zoom1, zoom2)
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

    fun toggleZoomLock(enabled: Boolean) {
        profile.fZoomLocked = enabled
        saveProfile()
    }

    fun saveZoom() {
        profile.zoom1 = frameState.zoomScale1
        profile.zoom2 = frameState.zoomScale2
        saveProfile()
    }

    fun setSafeArea(safeArea: RectF) {
        frameState.setSafeArea(safeArea)
        frameViewRef.get()?.requestRender()
    }

    /**************************************************************************
     * Miscellaneous
     **************************************************************************/

    fun sendClipboardText() {
        if (pref.server.clipboardSync && client.connected) launchIO {
            getClipboardText(app)?.let { messenger.sendClipboardText(it) }
        }
    }

    private var clipReceiverJob: Job? = null
    private fun receiveClipboardText(text: String) {
        if (!pref.server.clipboardSync)
            return

        // This is a protective measure against servers which send every 'selection' made on the server.
        // Setting clip text involves IPC, so these events can exhaust Binder resources, leading to ANRs.
        if (clipReceiverJob?.isActive == true) {
            Log.w(javaClass.simpleName, "Dropping clip text received from server, previous text is still pending")
            return
        }

        clipReceiverJob = launchIO {
            setClipboardText(app, text)
        }
    }

    fun getLoginInfo(type: LoginInfo.Type): LoginInfo {
        val vu = profile.username
        val vp = profile.password
        val sp = profile.sshPassword

        if (type == LoginInfo.Type.VNC_PASSWORD && vp.isNotBlank())
            return LoginInfo(password = vp)

        if (type == LoginInfo.Type.VNC_CREDENTIAL && vu.isNotBlank() && vp.isNotBlank())
            return LoginInfo(username = vu, password = vp)

        if (type == LoginInfo.Type.SSH_PASSWORD && sp.isNotBlank())
            return LoginInfo(password = sp)

        // Something is missing, so we have to ask the user
        return loginInfoRequest.requestResponse(type)  // Blocking call
    }

    /**
     * Resize remote desktop to match with local window size (if requested by user).
     * In portrait mode, safe area is used instead of window to exclude the keyboard.
     */
    fun resizeRemoteDesktop() {
        if (state.value.isConnected && profile.resizeRemoteDesktop) frameState.let {
            if (it.windowWidth > it.windowHeight)
                messenger.setDesktopSize(it.windowWidth.toInt(), it.windowHeight.toInt())
            else
                messenger.setDesktopSize(it.safeArea.width().toInt(), it.safeArea.height().toInt())
        }
    }

    fun pauseFrameBufferUpdates() {
        messenger.pauseFramebufferUpdates(true)
    }

    fun resumeFrameBufferUpdates() {
        messenger.pauseFramebufferUpdates(false)
    }

    fun refreshFrameBuffer() {
        messenger.refreshFrameBuffer()
    }

    /**
     * Sets gesture style of profile to given value.
     * Any change will be reflected in [activeGestureStyle].
     */
    fun setProfileGestureStyle(newStyle: String) {
        if (newStyle == profile.gestureStyle)
            return

        profile.gestureStyle = newStyle
        saveProfile()
        applyProfileGestureStyle()
    }

    private fun applyProfileGestureStyle() {
        if (profile.gestureStyle == "auto")
            activeGestureStyle.value = pref.input.gesture.style
        else
            activeGestureStyle.value = profile.gestureStyle
    }

    /**************************************************************************
     * [VncClient.Observer] Implementation
     **************************************************************************/

    override fun onPasswordRequired(): String {
        return getLoginInfo(LoginInfo.Type.VNC_PASSWORD).password
    }

    override fun onCredentialRequired(): UserCredential {
        return getLoginInfo(LoginInfo.Type.VNC_CREDENTIAL).let { UserCredential(it.username, it.password) }
    }

    override fun onVerifyCertificate(certificate: X509Certificate): Boolean {
        if (isCertificateTrusted(app, certificate))
            return true

        val title = "Unknown server certificate"
        val message = getUnknownCertificateMessage(certificate)
        if (!confirmationRequest.requestResponse(Pair(title, message)))
            return false

        trustCertificate(app, certificate)
        return true
    }

    override fun onFramebufferUpdated() {
        frameViewRef.get()?.requestRender()
    }

    override fun onGotXCutText(text: String) {
        receiveClipboardText(text)
    }

    override fun onFramebufferSizeChanged(width: Int, height: Int) {
        launchMain {
            frameState.setFramebufferSize(width.toFloat(), height.toFloat())
        }
    }

    override fun onPointerMoved(x: Int, y: Int) {
        frameViewRef.get()?.requestRender()
    }
}