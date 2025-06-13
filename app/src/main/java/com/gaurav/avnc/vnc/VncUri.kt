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
 * If host in given URI string is an IPv6 address, it MUST be wrapped in square brackets.
 * (This requirement come from using Java [URI] internally.)
 *
 * If given URI doesn't start with 'vnc://' scheme, it will be automatically added.
 */
class VncUri(str: String) {

    /**
     * Add scheme if missing.
     * It is also common for users to accidentally type 'vnc:host' instead of 'vnc://host',
     * so we gracefully handle that case too.
     */
    private val uriString = str.replaceFirst(Regex("^(vnc:/?/?)?", RegexOption.IGNORE_CASE), "vnc://")

    private val uri = Uri.parse(uriString)

    /**
     * Older versions of Android [Uri] does not support IPv6, so we need to use Java [URI] for host & port.
     * It also serves as a validation step because [URI] verifies that address is well-formed.
     */
    private val javaUri = runCatching { URI(uriString) }.getOrNull()


    val host = javaUri?.host?.trim('[', ']')
    val port = if (javaUri?.port == -1) null else javaUri?.port
    val connectionName = uri.getQueryParameter("ConnectionName")
    val connectionNameForProfile = connectionName ?: host?.let { "vnc://$it" }
    val saveConnection = uri.getBooleanQueryParameter("SaveConnection", false)

    val username = uri.getQueryParameter("VncUsername")
    val password = uri.getQueryParameter("VncPassword")
    val securityType = uri.getQueryParameter("SecurityType")?.toIntOrNull()
    val channelType = uri.getQueryParameter("ChannelType")?.toIntOrNull()
    val colorLevel = uri.getQueryParameter("ColorLevel")?.toIntOrNull()
    val viewOnly = uri.getQueryParameter("ViewOnly")?.let { uri.getBooleanQueryParameter("ViewOnly", false) }

    val sshHost = uri.getQueryParameter("SshHost") ?: host
    val sshPort = uri.getQueryParameter("SshPort")?.toIntOrNull()
    val sshUsername = uri.getQueryParameter("SshUsername")
    val sshPassword = uri.getQueryParameter("SshPassword")

    /**
     *  Applies this URI to given profile. Any parameter present in this URI will
     *  overwrite corresponding member of [profile]. Returns the same profile.
     */
    fun applyToProfile(profile: ServerProfile): ServerProfile {
        connectionNameForProfile?.let { profile.name = it }
        host?.let { profile.host = it }
        port?.let { profile.port = it }
        username?.let { profile.username = it }
        password?.let { profile.password = it }
        securityType?.let { profile.securityType = it }
        channelType?.let { profile.channelType = it }
        colorLevel?.let { profile.colorLevel = it }
        viewOnly?.let { profile.viewOnly = it }
        sshHost?.let { profile.sshHost = it }
        sshPort?.let { profile.sshPort = it }
        sshUsername?.let { profile.sshUsername = it }
        sshPassword?.let { profile.sshPassword = it }
        profile.sshAuthType = ServerProfile.SSH_AUTH_PASSWORD

        return profile
    }

    /**
     * Generates a [ServerProfile] using this instance.
     */
    fun toServerProfile() = applyToProfile(ServerProfile())

    override fun toString() = uriString
}