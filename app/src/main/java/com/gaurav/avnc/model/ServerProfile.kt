/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

/**
 * This class holds connection configuration of a remote VNC server.
 *
 * Some of the fields remain unused until that feature is implemented.
 */
@Parcelize
@Serializable
@Entity(tableName = "profiles")
data class ServerProfile(

        @PrimaryKey(autoGenerate = true)
        var ID: Long = 0,

        /**
         * Descriptive name of the server (ex: 'Kitchen PC').
         */
        var name: String = "",

        /**
         * Internet address of the server. This can be hostname or IP address.
         * It does not contain the port number.
         */
        var host: String = "",

        /**
         * Port number of the remote server.
         */
        var port: Int = 5900,

        /**
         * Username which will be used when connecting to this server.
         */
        var username: String = "",

        /**
         * Password used for authenticating with this server.
         * Note: Username & password may not be used for all security types.
         */
        var password: String = "",

        /**
         * Security type to use when connecting to this server. e.g. VncAuth.
         * 0 enables all supported types.
         */
        var securityType: Int = 0,

        /**
         * Transport channel to be used for communicating with the server.
         * Ex: TCP, SSH Tunnel
         */
        var channelType: Int = CHANNEL_TCP,

        /**
         * Specifies the color level of received frames.
         * This value determines the pixel-format used for framebuffer.
         */
        var colorLevel: Int = 0,

        /**
         * Specifies the image quality of the frames.
         * This mainly affects the compression level used by some encodings.
         */
        var imageQuality: Int = 0,

        /**
         * Specifies whether 'View Only' mode should be used.
         * In this mode client does not send any input messages to remote server.
         */
        var viewOnly: Boolean = false,

        /**
         * Whether the cursor should be drawn by client instead of server.
         * Currently, only hides the remote cursor.
         */
        var useLocalCursor: Boolean = false,

        /**
         * Compatibility mode used for connection.
         * Usually denotes a server type, e.g. Vino, TigerVNC
         * 0 = Default behaviour
         */
        var compatMode: Int = 0,

        /**
         * Whether UltraVNC Repeater is used for connections.
         * When repeater is used, [host] & [port] identifies the repeater.
         */
        var useRepeater: Boolean = false,

        /**
         * When using a repeater, this value identifies the VNC server.
         * Valid IDs: [0, 999999999].
         */
        var idOnRepeater: Int = 0,

        /**
         * These values are used for SSH Tunnel
         */
        var sshHost: String = "",
        var sshPort: Int = 22,
        var sshUsername: String = "",
        var sshAuthType: Int = SSH_AUTH_KEY,
        var sshPassword: String = "",
        var sshPrivateKey: String = "",
        var sshPrivateKeyPassword: String = ""

) : Parcelable {

    companion object {
        // Channel types (from RFC 7869)
        const val CHANNEL_TCP = 1
        const val CHANNEL_SSH_TUNNEL = 24

        //SSH auth types
        const val SSH_AUTH_PASSWORD = 1
        const val SSH_AUTH_KEY = 2
    }
}
