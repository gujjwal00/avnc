package com.gaurav.avnc.vnc

import androidx.annotation.Keep

/**
 * This is a thin wrapper around native RFB client.
 *
 *
 * -       +------------+                                    +----------+
 * -       | Public API |                                    | Listener |
 * -       +------------+                                    +-----A----+
 * -              |                                                |
 * -              |                                                |
 * -   JNI -------|------------------------------------------------|-----------
 * -              |                                                |
 * -              |                                                |
 * -      +-------v--------+       +--------------+       +--------v---------+
 * -      | Native Methods |------>| LibVNCClient |<----->| Native Callbacks |
 * -      +----------------+       +--------------+       +------------------+
 *
 *
 * After successful [init], you must call [cleanup] on this object
 * once you are done with it so that allocated resources can be freed.
 */
class VncClient(
        /**
         * RFB callback listener.
         */
        private val observer: Observer
) {
    /**
     * Value of the pointer to native 'rfbClient'.
     */
    private val nativePtr: Long

    /**
     * Information related to this client.
     *
     * Whenever this value is changed, [observer] is notified via [Observer.onClientInfoChanged].
     */
    var info = Info(); private set

    /**
     * Constructor.
     */
    init {
        nativePtr = nativeClientCreate()
        if (nativePtr == 0L)
            throw RuntimeException("Could not create native rfbClient!")
    }

    companion object {
        @JvmStatic
        private external fun initLibrary()

        init {
            System.loadLibrary("native-vnc")
            initLibrary()
        }
    }

    /**
     * Initializes VNC connection.
     * TODO: Add Repeater support
     *
     * @param host Server address
     * @param port Port number
     * @return true if initialization was successful
     */
    fun init(host: String, port: Int): Boolean {
        if (info.state != State.Created)
            return false

        setState(State.Initializing)

        if (nativeInit(nativePtr, host, port)) {
            refreshInfo(State.Connected)
            return true
        } else {
            setDisconnected()
            return false
        }
    }

    /**
     * Waits for incoming server message, parses it and then invokes appropriate
     * callbacks.
     *
     * @param uSecTimeout Timeout in microseconds.
     * @return true if message was successfully handled or no message was received within timeout,
     *         false otherwise.
     */
    fun processServerMessage(uSecTimeout: Int): Boolean {
        if (info.state != State.Connected)
            return false

        if (!nativeProcessServerMessage(nativePtr, uSecTimeout)) {
            setDisconnected() //Msg processing will fail on irrecoverable errors
            return false
        }

        return true
    }

    /**
     * Sends Key event to remote server.
     *
     * @param key    Key code
     * @param isDown Whether it is an DOWN or UP event
     * @return true if sent successfully, false otherwise
     */
    fun sendKeyEvent(key: Int, isDown: Boolean, translate: Boolean) = nativeSendKeyEvent(nativePtr, key.toLong(), isDown, translate)

    /**
     * Sends pointer event to remote server.
     *
     * @param x    Horizontal pointer coordinate
     * @param y    Vertical pointer coordinate
     * @param mask Button mask to identify which button was pressed.
     * @return true if sent successfully, false otherwise
     */
    fun sendPointerEvent(x: Int, y: Int, mask: Int) = nativeSendPointerEvent(nativePtr, x, y, mask)


    /**
     * Sends text to remote desktop's clipboard.
     * TODO: Hook with clipboard.
     *
     * @param text Text to send
     * @return Whether sent successfully.
     */
    fun sendCutText(text: String) = nativeSendCutText(nativePtr, text)

    /**
     * Sends a request for full frame buffer update to remote server.
     *
     * @return Whether sent successfully
     */
    fun refreshFrameBuffer() = nativeRefreshFrameBuffer(nativePtr)

    /**
     * Puts framebuffer contents in currently active OpenGL texture.
     * Must be called from an OpenGL ES context (i.e. from renderer thread).
     */
    fun uploadFrameTexture() = nativeUploadFrameTexture(nativePtr)

    /**
     * Marks this client as 'disconnected'.
     *
     * This doesn't actually disconnects from server but only updates the
     * state. Then [processServerMessage] will notice this change and will
     * return false to allow normal cleanup.
     *
     */
    fun setDisconnected() = setState(State.Disconnected)

    /**
     * Releases all resource (native & managed) currently held.
     * After cleanup, this client MUST NOT be used any more.
     */
    fun cleanup() {
        if (info.state == State.Destroyed)
            return

        setState(State.Destroyed)
        nativeCleanup(nativePtr)
    }

    /**
     * Set current information to given value and notifies observer.
     */
    private fun setInfo(newInfo: Info) {
        info = newInfo
        observer.onClientInfoChanged(info)
    }

    /**
     * Changes state to given value.
     */
    private fun setState(newState: State) = setInfo(info.copy(state = newState))

    /**
     * Refresh current information from native side.
     * Optionally allows to specify a new state.
     */
    private fun refreshInfo(newState: State? = null) {
        val newInfo = info.copy(state = newState ?: info.state,
                serverName = nativeGetDesktopName(nativePtr),
                frameWidth = nativeGetWidth(nativePtr),
                frameHeight = nativeGetHeight(nativePtr),
                isEncrypted = nativeIsEncrypted(nativePtr))

        setInfo(newInfo)
    }

    private external fun nativeClientCreate(): Long
    private external fun nativeInit(clientPtr: Long, host: String, port: Int): Boolean
    private external fun nativeProcessServerMessage(clientPtr: Long, uSecTimeout: Int): Boolean
    private external fun nativeSendKeyEvent(clientPtr: Long, key: Long, isDown: Boolean, translate: Boolean): Boolean
    private external fun nativeSendPointerEvent(clientPtr: Long, x: Int, y: Int, mask: Int): Boolean
    private external fun nativeSendCutText(clientPtr: Long, text: String): Boolean
    private external fun nativeRefreshFrameBuffer(clientPtr: Long): Boolean
    private external fun nativeGetDesktopName(clientPtr: Long): String
    private external fun nativeGetWidth(clientPtr: Long): Int
    private external fun nativeGetHeight(clientPtr: Long): Int
    private external fun nativeIsEncrypted(clientPtr: Long): Boolean
    private external fun nativeUploadFrameTexture(clientPtr: Long)
    private external fun nativeCleanup(clientPtr: Long)

    @Keep
    private fun cbGetPassword(): String {
        setState(State.Authenticating)
        return observer.rfbGetPassword()
    }

    @Keep
    private fun cbGetCredential(): UserCredential {
        setState(State.Authenticating)
        return observer.rfbGetCredential()
    }

    @Keep
    private fun cbBell() = observer.rfbBell()

    @Keep
    private fun cbGotXCutText(text: String) = observer.rfbGotXCutText(text)

    @Keep
    private fun cbHandleCursorPos(x: Int, y: Int) = observer.rfbHandleCursorPos(x, y)

    @Keep
    private fun cbFinishedFrameBufferUpdate() = observer.rfbFinishedFrameBufferUpdate()

    @Keep
    private fun cbFrameBufferSizeChanged() = refreshInfo()

    /**
     * Interface for RFB callback listener.
     */
    interface Observer {
        fun rfbGetPassword(): String
        fun rfbGetCredential(): UserCredential
        fun rfbBell()
        fun rfbGotXCutText(text: String)
        fun rfbFinishedFrameBufferUpdate()
        fun rfbHandleCursorPos(x: Int, y: Int): Boolean
        fun onClientInfoChanged(info: Info)
    }

    /**
     * Lifecycle state:
     *
     *            CREATED ---------------+
     *               |                   |
     *               v                   |
     *          INITIALIZING --------+   |
     *               |               |   |
     *               v               |   |
     *         AUTHENTICATING -------+   |
     *               |               |   |
     *               v               |   |
     *           CONNECTED           |   |
     *               |               |   |
     *               v               |   |
     *          DISCONNECTED <-------+   |
     *               |                   |
     *               v                   |
     *           DESTROYED <-------------+
     *
     *
     */
    enum class State {
        Created,
        Initializing,
        Authenticating,
        Connected,
        Disconnected,
        Destroyed
    }

    /**
     * This class is used for representing information about this client.
     *
     * Properties (except [state]) are valid iff [state] == [State.Connected].
     */
    data class Info(
            val state: State = State.Created,
            val serverName: String = "",
            val frameWidth: Int = 0,
            val frameHeight: Int = 0,
            val isEncrypted: Boolean = false
    )
}