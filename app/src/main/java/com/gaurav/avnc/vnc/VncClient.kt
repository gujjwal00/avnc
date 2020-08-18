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
    val nativePtr: Long

    /**
     * Holds information about the current connection.
     */
    private var connectionInfo: ConnectionInfo? = null

    /**
     * Constructor.
     *
     * After successful construction, you must call cleanup() on this object
     * once you are done with it so that allocated resources can be freed.
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
        connectionInfo = null
        return nativeInit(nativePtr, host, port)
    }

    /**
     * Waits for incoming server message, parses it and then invokes appropriate
     * callbacks.
     *
     * @param uSecTimeout Timeout in microseconds.
     * @return true if message was successfully handled or no message was received within timeout,
     * false otherwise.
     */
    fun processServerMessage(uSecTimeout: Int): Boolean {
        return nativeProcessServerMessage(nativePtr, uSecTimeout)
    }

    /**
     * Sends Key event to remote server.
     *
     * @param key    Key code
     * @param isDown Whether it is an DOWN or UP event
     * @return true if sent successfully, false otherwise
     */
    fun sendKeyEvent(key: Long, isDown: Boolean): Boolean {
        return nativeSendKeyEvent(nativePtr, key, isDown)
    }

    /**
     * Sends pointer event to remote server.
     *
     * @param x    Horizontal pointer coordinate
     * @param y    Vertical pointer coordinate
     * @param mask Button mask to identify which button was pressed.
     * @return true if sent successfully, false otherwise
     */
    fun sendPointerEvent(x: Int, y: Int, mask: Int): Boolean {
        return nativeSendPointerEvent(nativePtr, x, y, mask)
    }

    /**
     * Sends text to remote desktop's clipboard.
     *
     * @param text Text to send
     * @return Whether sent successfully.
     */
    fun sendCutText(text: String): Boolean {
        return nativeSendCutText(nativePtr, text)
    }

    /**
     * Sends a request for full frame buffer update to remote server.
     *
     * @return Whether sent successfully
     */
    fun refreshFrameBuffer(): Boolean {
        return nativeSendFrameBufferUpdateRequest(nativePtr,
                0,
                0,
                getConnectionInfo().frameWidth,
                getConnectionInfo().frameHeight,
                true)
    }

    /**
     * Releases all resource (native & managed) currently held.
     * After cleanup, this object should not be used any more.
     */
    fun cleanup() {
        nativeCleanup(nativePtr)
    }

    /**
     * Returns information about current connection.
     *
     * @return
     */
    fun getConnectionInfo(): ConnectionInfo {
        return getConnectionInfo(false)
    }

    /**
     * Returns information about current connection.
     *
     * @param refresh Whether information should be reloaded from native rfbClient.
     */
    fun getConnectionInfo(refresh: Boolean): ConnectionInfo {
        var info = connectionInfo
        if (info == null || refresh) {
            info = nativeGetConnectionInfo(nativePtr)
            connectionInfo = info
        }
        return info
    }

    /**
     * This class is used for representing information about the current connection.
     *
     * Note: This class is instantiated by the native code. Any change in fields & constructor
     * arguments should be synchronized with native side.
     *
     * TODO: Should we make this a standalone class?
     * TODO: Add info about encoding etc.
     */
    class ConnectionInfo @Keep constructor(val desktopName: String, val frameWidth: Int, val frameHeight: Int, val isEncrypted: Boolean)

    private external fun nativeClientCreate(): Long
    private external fun nativeInit(clientPtr: Long, host: String, port: Int): Boolean
    private external fun nativeProcessServerMessage(clientPtr: Long, uSecTimeout: Int): Boolean
    private external fun nativeSendKeyEvent(clientPtr: Long, key: Long, isDown: Boolean): Boolean
    private external fun nativeSendPointerEvent(clientPtr: Long, x: Int, y: Int, mask: Int): Boolean
    private external fun nativeSendCutText(clientPtr: Long, text: String): Boolean
    private external fun nativeSendFrameBufferUpdateRequest(clientPtr: Long, x: Int, y: Int, w: Int, h: Int, incremental: Boolean): Boolean
    private external fun nativeGetConnectionInfo(clientPtr: Long): ConnectionInfo
    private external fun nativeCleanup(clientPtr: Long)

    @Keep
    private fun cbGetPassword(): String {
        return observer.rfbGetPassword()
    }

    @Keep
    private fun cbGetCredential(): UserCredential {
        return observer.rfbGetCredential()
    }

    @Keep
    private fun cbBell() {
        observer.rfbBell()
    }

    @Keep
    private fun cbGotXCutText(text: String) {
        observer.rfbGotXCutText(text)
    }

    @Keep
    private fun cbHandleCursorPos(x: Int, y: Int): Boolean {
        return observer.rfbHandleCursorPos(x, y)
    }

    @Keep
    private fun cbFinishedFrameBufferUpdate() {
        observer.rfbFinishedFrameBufferUpdate()
    }

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
    }

}