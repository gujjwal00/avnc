package com.gaurav.avnc.vnc

import androidx.annotation.Keep
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * This is a thin wrapper around native client.
 *
 *
 * -       +------------+                                    +----------+
 * -       | Public API |                                    | Observer |
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
 * store its pointer in [nativePtr]. Parameters for connection can be setup using
 * [configure]. Connection is then started using [connect]. Then incoming
 * messages are handled by [processServerMessage].
 *
 * To release the resources you must call [cleanup] after you are done with
 * this instance.
 */
class VncClient(private val observer: Observer) {

    /**
     * Interface for event observer.
     * DO NOT throw exceptions from these methods.
     * There is NO guarantee about which thread will invoke [Observer] methods.
     */
    interface Observer {
        fun onPasswordRequired(): String
        fun onCredentialRequired(): UserCredential
        fun onGotXCutText(text: String)
        fun onFramebufferUpdated()
        fun onFramebufferSizeChanged(width: Int, height: Int)
        fun onPointerMoved(x: Int, y: Int)

        //fun onBell()
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

    @Volatile
    var connected = false
        private set

    /**
     * Name of remote desktop
     */
    val desktopName; get() = nativeGetDesktopName(nativePtr)

    /**
     * Whether connection is encrypted
     */
    val isEncrypted; get() = nativeIsEncrypted(nativePtr)

    /**
     * In 'View-only' mode input to remote server is disabled
     */
    var viewOnlyMode = false; private set

    /**
     * Latest pointer position. See [moveClientPointer].
     */
    var pointerX = 0; private set
    var pointerY = 0; private set

    /**
     * Client-side cursor rendering creates a synchronization issue.
     * Suppose if pointer is moved to (50,10) by client. A PointerEvent is sent
     * to the server and cursor is immediately rendered on (50,10).
     * Some servers (e.g. Vino) will send back a PointerPosition event for (50, 10).
     * But, by the time that event is received from server, pointer on client
     * might have already moved to (60,20) (this is almost guaranteed to happen
     * with touchpad/relative action mode). So the cursor will probably 'jump back'
     * depending on the order of these events.
     *
     * This flags works around the issue by temporarily ignoring serer-side updates.
     */
    @Volatile
    var ignorePointerMovesByServer = false

    /**
     * Setup different properties for this client.
     *
     * @param securityType RFB security type to use.
     */
    fun configure(viewOnly: Boolean, securityType: Int, useLocalCursor: Boolean) {
        viewOnlyMode = viewOnly
        nativeConfigure(nativePtr, securityType, useLocalCursor)
    }

    fun setupRepeater(serverId: Int) {
        nativeSetDest(nativePtr, "ID", serverId)
    }

    /**
     * Initializes VNC connection.
     */
    fun connect(host: String, port: Int) {
        connected = nativeInit(nativePtr, host, port)
        if (!connected) throw IOException(nativeGetLastErrorStr())
    }

    /**
     * Waits for incoming server message, parses it and then invokes appropriate callbacks.
     *
     * @param uSecTimeout Timeout in microseconds.
     */
    fun processServerMessage(uSecTimeout: Int = 1000000) {
        if (!connected)
            return

        if (!nativeProcessServerMessage(nativePtr, uSecTimeout)) {
            connected = false
            throw IOException(nativeGetLastErrorStr())
        }
    }

    /**
     * Sends Key event to remote server.
     *
     * @param keySym    Key symbol
     * @param isDown    true for key down, false for key up
     */
    fun sendKeyEvent(keySym: Int, isDown: Boolean) = executeSend {
        nativeSendKeyEvent(nativePtr, keySym.toLong(), isDown)
    }

    /**
     * Sends pointer event to remote server.
     *
     * @param x    Horizontal pointer coordinate
     * @param y    Vertical pointer coordinate
     * @param mask Button mask to identify which button was pressed.
     */
    fun sendPointerEvent(x: Int, y: Int, mask: Int) = executeSend {
        nativeSendPointerEvent(nativePtr, x, y, mask)
    }

    /**
     * Updates client-side pointer position.
     * No event is sent to server.
     *
     * Primary use-case is to update pointer position during gestures.
     * This way we can immediately render the cursor on new position without
     * waiting for Network IO.
     *
     * It also helps with servers which don't send pointer-position updates
     * if pointer was moved by the client.
     *
     * @param x    Horizontal pointer coordinate
     * @param y    Vertical pointer coordinate
     */
    fun moveClientPointer(x: Int, y: Int) = executeSend {
        pointerX = x
        pointerY = y
        observer.onPointerMoved(x, y)
    }


    /**
     * Sends text to remote desktop's clipboard.
     */
    fun sendCutText(text: String) = executeSend {
        nativeSendCutText(nativePtr, text.toByteArray(StandardCharsets.ISO_8859_1))
    }

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
     * Upload cursor shape into framebuffer texture.
     */
    fun uploadCursor() = nativeUploadCursor(nativePtr, pointerX, pointerY)

    /**
     * Release all resources allocated by the client.
     * DO NOT use this client after [cleanup].
     */
    fun cleanup() {
        connected = false
        nativeCleanup(nativePtr)
    }

    /**
     * Small utility method to check for valid state before sending
     * events to remote server.
     */
    private inline fun executeSend(block: () -> Unit) {
        if (connected && !viewOnlyMode)
            block()
    }

    private external fun nativeClientCreate(): Long
    private external fun nativeConfigure(clientPtr: Long, securityType: Int, useLocalCursor: Boolean)
    private external fun nativeInit(clientPtr: Long, host: String, port: Int): Boolean
    private external fun nativeSetDest(clientPtr: Long, host: String, port: Int)
    private external fun nativeProcessServerMessage(clientPtr: Long, uSecTimeout: Int): Boolean
    private external fun nativeSendKeyEvent(clientPtr: Long, key: Long, isDown: Boolean): Boolean
    private external fun nativeSendPointerEvent(clientPtr: Long, x: Int, y: Int, mask: Int): Boolean
    private external fun nativeSendCutText(clientPtr: Long, bytes: ByteArray): Boolean
    private external fun nativeRefreshFrameBuffer(clientPtr: Long): Boolean
    private external fun nativeGetDesktopName(clientPtr: Long): String
    private external fun nativeGetWidth(clientPtr: Long): Int
    private external fun nativeGetHeight(clientPtr: Long): Int
    private external fun nativeIsEncrypted(clientPtr: Long): Boolean
    private external fun nativeUploadFrameTexture(clientPtr: Long)
    private external fun nativeUploadCursor(clientPtr: Long, px: Int, py: Int)
    private external fun nativeGetLastErrorStr(): String
    private external fun nativeCleanup(clientPtr: Long)

    @Keep
    private fun cbGetPassword() = observer.onPasswordRequired()

    @Keep
    private fun cbGetCredential() = observer.onCredentialRequired()

    @Keep
    private fun cbGotXCutText(bytes: ByteArray) {
        observer.onGotXCutText(StandardCharsets.ISO_8859_1.decode(ByteBuffer.wrap(bytes)).toString())
    }

    @Keep
    private fun cbFinishedFrameBufferUpdate() = observer.onFramebufferUpdated()

    @Keep
    private fun cbFramebufferSizeChanged(w: Int, h: Int) = observer.onFramebufferSizeChanged(w, h)


    @Keep
    private fun cbBell() = Unit // observer.onBell()

    @Keep
    private fun cbHandleCursorPos(x: Int, y: Int) {
        if (!ignorePointerMovesByServer)
            moveClientPointer(x, y)
    }


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