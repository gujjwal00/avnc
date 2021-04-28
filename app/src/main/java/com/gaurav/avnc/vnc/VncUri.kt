/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.net.Uri
import com.gaurav.avnc.model.ServerProfile
import java.net.URI

/**
 * This class implements the `vnc` URI scheme.
 * Reference: https://tools.ietf.org/html/rfc7869
 *
 * If `host` in URI is an IPv6 address, it MUST be wrapped in square brackets.
 * (This requirement come from using Java [URI] internally.)
 */
class VncUri(private val uri: Uri) {

    /**
     * Create new instance from URI string.
     * VNC schema will be added if missing.
     */
    constructor(uriString: String) : this(
            if (uriString.startsWith("vnc://"))
                Uri.parse(uriString)
            else
                Uri.parse("vnc://$uriString")
    )

    /**
     * Older versions of Android [Uri] does not support IPv6 so we need to use Java [URI] for host & port.
     *
     * It also serves as a validation step because [URI] verifies that address is well-formed.
     */
    private val javaUri = runCatching { URI(uri.toString()) }.getOrDefault(URI(""))


    val host = javaUri.host?.trim('[', ']') ?: ""
    val port = if (javaUri.port == -1) 5900 else javaUri.port
    val connectionName = uri.getQueryParameter("ConnectionName") ?: ""
    val username = uri.getQueryParameter("VncUsername") ?: ""
    val password = uri.getQueryParameter("VncPassword") ?: ""
    val securityType = uri.getQueryParameter("SecurityType")?.toIntOrNull() ?: 0
    val channelType = uri.getQueryParameter("ChannelType")?.toIntOrNull() ?: ServerProfile.CHANNEL_TCP
    val colorLevel = uri.getQueryParameter("ColorLevel")?.toIntOrNull() ?: 7
    val viewOnly = uri.getBooleanQueryParameter("ViewOnly", false)
    val saveConnection = uri.getBooleanQueryParameter("SaveConnection", false)
    val sshHost = uri.getQueryParameter("SshHost") ?: host
    val sshPort = uri.getQueryParameter("SshPort")?.toIntOrNull() ?: 22
    val sshUsername = uri.getQueryParameter("SshUsername") ?: ""
    val sshPassword = uri.getQueryParameter("SshPassword") ?: ""

    /**
     * Generates a [ServerProfile] using this instance.
     */
    fun toServerProfile() = ServerProfile(
            name = connectionName,
            host = host,
            port = port,
            username = username,
            password = password,
            securityType = securityType,
            channelType = channelType,
            colorLevel = colorLevel,
            viewOnly = viewOnly,
            sshHost = sshHost,
            sshPort = sshPort,
            sshUsername = sshUsername,
            sshAuthType = ServerProfile.SSH_AUTH_PASSWORD,
            sshPassword = sshPassword
    )
}