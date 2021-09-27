/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.util.Base64
import com.gaurav.avnc.model.ServerProfile
import com.trilead.ssh2.Connection
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.ServerHostKeyVerifier
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest


/**
 * Container for SSH Host Key
 */
class HostKey(
        val host: String,
        val isKnownHost: Boolean,
        val algo: String,
        val key: ByteArray,
) {
    /**
     * Returns SHA-256 fingerprint of the [key].
     */
    fun getFingerprint(): String {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(key)
        val base64 = Base64.encodeToString(sha256, Base64.NO_PADDING)
        return "SHA256:$base64"
    }
}

/**
 * Implements Host Key verification.
 *
 * Known hosts & keys are stored in a file inside app's private storage.
 * For unknown host, user is prompted to confirm the key.
 */
class HostKeyVerifier(private val viewModel: VncViewModel) : ServerHostKeyVerifier {

    private val knownHostsFile = File(viewModel.app.filesDir, "known-hosts")

    private val knownHosts = KnownHosts(knownHostsFile)

    override fun verifyServerHostKey(hostname: String, port: Int, keyAlgorithm: String, key: ByteArray): Boolean {
        val verification = knownHosts.verifyHostkey(hostname, keyAlgorithm, key)

        if (verification == KnownHosts.HOSTKEY_IS_OK)
            return true

        val isKnownHost = (verification == KnownHosts.HOSTKEY_HAS_CHANGED)
        val hostKey = HostKey(hostname, isKnownHost, keyAlgorithm, key)

        if (viewModel.sshHostKeyVerifyRequest.requestResponse(hostKey)) {
            //User has confirmed the key, so remember it.
            KnownHosts.addHostkeyToFile(knownHostsFile, arrayOf(hostname), keyAlgorithm, key)
            return true
        }

        return false
    }
}

/**
 * Manager for SSH Tunnel
 */
class SshTunnel(private val viewModel: VncViewModel) {

    private var connection: Connection? = null
    private var forwarder: LocalPortForwarder? = null

    val localHost = "127.0.0.1"
    var localPort = 0

    /**
     * Opens the tunnel according to current profile in [viewModel].
     */
    fun open() {
        val profile = viewModel.profile
        val connection = Connection(profile.sshHost, profile.sshPort)

        connection.connect(HostKeyVerifier(viewModel))
        this.connection = connection

        if (profile.sshAuthType == ServerProfile.SSH_AUTH_PASSWORD)
            connection.authenticateWithPassword(profile.sshUsername, profile.sshPassword)
        else
            connection.authenticateWithPublicKey(profile.sshUsername, profile.sshPrivateKey.toCharArray(), profile.sshPrivateKeyPassword)

        if (!connection.isAuthenticationComplete)
            throw IOException("SSH authentication failed")


        // SSHLib does not expose internal ServerSocket used for local port forwarder.
        // Hence, if we pass 0 as local port to let the system pick a port for us, we have no way
        // to know the port system picked.
        // So we create a temporary ServerSocket, close it immediately and try to use its port.
        // But between the close-reuse, that port can be assigned to someone else so we try again.
        for (i in 1..50) {
            val attemptedPort = with(ServerSocket(0)) { close(); localPort }
            val address = InetSocketAddress(localHost, attemptedPort)

            try {
                forwarder = connection.createLocalPortForwarder(address, profile.host, profile.port)
                localPort = attemptedPort
                break
            } catch (e: IOException) {
                //Retry
            }
        }

        if (localPort == 0)
            throw IOException("Cannot find a local port for SSH Tunnel")
    }

    /**
     * Stop accepting new connections.
     * This has no effect on connections already established through the tunnel.
     */
    fun stopAcceptingConnections() {
        forwarder?.close()
        forwarder = null
    }

    fun close() {
        forwarder?.close()
        connection?.close()
    }
}
