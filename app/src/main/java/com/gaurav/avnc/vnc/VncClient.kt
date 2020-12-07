package com.gaurav.avnc.vnc

import androidx.annotation.Keep
import com.gaurav.avnc.vnc.VncClient.Observer

/**
 * This is a thin wrapper around native client.
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
 * For every new instance of [VncClient], we create a native 'rfbClient' and
 * store its pointer in [nativePtr]. To release the resources you must call
 * [cleanup] after you are done with this instance.
 *
 * All callbacks in [Observer] are invoked on the thread used to call
 * [processServerMessage].
 */
class VncClient(private val observer: Observer) {

    /**
     * [VncClient] Lifecycle:
     *
     *            Created ---------------+
     *               |                   |
     *               v                   |
     *          Connecting ----------+   |
     *               |               |   |
     *               v               |   |
     *           Connected           |   |
     *               |               |   |
     *               v               |   |
     *          Disconnected <-------+   |
     *               |                   |
     *               v                   |
     *           Destroyed <-------------+
     */
    enum class State {
        Created,
        Connecting,
        Connected,
        Disconnected,
        Destroyed
    }

    /**
     * Interface for event observer.
     */
    interface Observer {
        fun onPasswordRequired(): String
        fun onCredentialRequired(): UserCredential
        fun onGotXCutText(text: String)
        fun onFramebufferUpdated()
        fun onFramebufferSizeChanged(width: Int, height: Int)
        fun onClientStateChanged(newState: State)

        //fun onBell()
        //fun onCursorMoved(x: Int, y: Int): Boolean
    }

    /**
     * Value of the pointer to native 'rfbClient'. This is passed to all native methods.
     */
    private val nativePtr: Long

    init {
        nativePtr = nativeClientCreate()
        if (nativePtr == 0L)
            throw RuntimeException("Could not create native rfbClient!")
    }

    /**
     * Current client state.
     */
    var state: State = State.Created
        private set(value) {
            field = value
            observer.onClientStateChanged(value)
        }

    /**
     * Name of remote desktop
     */
    var desktopName = ""; private set

    /**
     * Whether connection is encrypted
     */
    var isEncrypted = false; private set


    /**
     * Initializes VNC connection.
     * TODO: Add Repeater support
     *
     * @return true if initialization was successful
     */
    fun connect(host: String, port: Int): Boolean {
        if (state != State.Created)
            return false

        state = State.Connecting

        if (nativeInit(nativePtr, host, port)) {
            desktopName = nativeGetDesktopName(nativePtr)
            isEncrypted = nativeIsEncrypted(nativePtr)
            state = State.Connected
            return true
        } else {
            state = State.Disconnected
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
    fun processServerMessage(uSecTimeout: Int = 1000000): Boolean {
        if (state != State.Connected)
            return false

        if (!nativeProcessServerMessage(nativePtr, uSecTimeout)) {
            disconnect() //Reason: Msg processing will only fail on irrecoverable errors
            return false
        }

        return true
    }

    /**
     * Sends Key event to remote server.
     *
     * @param keySym    Key symbol
     * @param isDown    true for key down, false for key up
     * @param translate Whether to convert [keySym] to corresponding X KeySym
     */
    fun sendKeyEvent(keySym: Int, isDown: Boolean, translate: Boolean) =
            nativeSendKeyEvent(nativePtr, keySym.toLong(), isDown, translate)

    /**
     * Sends pointer event to remote server.
     *
     * @param x    Horizontal pointer coordinate
     * @param y    Vertical pointer coordinate
     * @param mask Button mask to identify which button was pressed.
     */
    fun sendPointerEvent(x: Int, y: Int, mask: Int) = nativeSendPointerEvent(nativePtr, x, y, mask)


    /**
     * Sends text to remote desktop's clipboard.
     */
    fun sendCutText(text: String) = nativeSendCutText(nativePtr, text)

    /**
     * Sends a request for full frame buffer update to remote server.
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
     * This doesn't actually release the resources from server but
     * simply updates the state. [cleanup] is used for that.
     */
    fun disconnect() {
        state = State.Disconnected
    }

    /**
     * Releases all resource (native & managed) currently held.
     * After cleanup, this client MUST NOT be used any more.
     */
    fun cleanup() {
        if (state == State.Destroyed)
            return

        state = State.Destroyed
        nativeCleanup(nativePtr)
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
    private fun cbGetPassword() = observer.onPasswordRequired()

    @Keep
    private fun cbGetCredential() = observer.onCredentialRequired()

    @Keep
    private fun cbGotXCutText(text: String) = observer.onGotXCutText(text)

    @Keep
    private fun cbFinishedFrameBufferUpdate() = observer.onFramebufferUpdated()

    @Keep
    private fun cbFramebufferSizeChanged(w: Int, h: Int) = observer.onFramebufferSizeChanged(w, h)


    @Keep
    private fun cbBell() = Unit // observer.onBell()

    @Keep
    private fun cbHandleCursorPos(x: Int, y: Int) = true //observer.onCursorMoved(x, y)


    /**
     * Native library initialization
     */
    companion object {
        @JvmStatic
        private external fun initLibrary()

        init {
            System.loadLibrary("native-vnc")
            initLibrary()
        }
    }
}