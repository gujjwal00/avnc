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
import com.gaurav.avnc.vnc.VncUri
import kotlinx.android.parcel.Parcelize

/**
 * This class holds connection configuration of a remote VNC server.
 */
@Parcelize
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
        var address: String = "",

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
         * Security type to be used when connecting to this server.
         * Ex: VncAuth
         */
        var securityType: Int = 0,

        /**
         * Transport type to be used for communicating with the server.
         * Ex: TCP, SSH Tunnel
         */
        var transportType: Int = 0,

        /**
         * Specifies the initial color quality of received frames.
         * This value determines the pixel-format used for framebuffer.
         */
        var colorQuality: Int = 0,

        /**
         * Specifies the image quality of the frames.
         * This mainly affects the compression level used by some encodings.
         */
        var imageQuality: Int = 0,

        /**
         * Specifies whether 'View Only' mode should be used.
         * In this mode client does not send any input messages to remote server.
         */
        var viewOnly: Boolean = false

) : Parcelable {

    /**
     * Construct from [VncUri]
     */
    constructor(uri: VncUri) : this() {
        name = uri.connectionName
        address = uri.host
        port = uri.port
        username = uri.username
        password = uri.password
        securityType = uri.securityType
        transportType = uri.channelType
        colorQuality = uri.colorLevel
        viewOnly = uri.viewOnly
    }
}
