/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel.service

import android.util.Base64
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.viewmodel.VncViewModel
import com.trilead.ssh2.Connection
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.crypto.PEMDecoder
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.security.MessageDigest

/**
 * Checks if given private key is encrypted.
 * [key] is in PEM format.
 *
 * Throws [IOException] if [key] is not a valid private key.
 */
fun isPrivateKeyEncrypted(key: String): Boolean {
    return PEMDecoder.isPEMEncrypted(PEMDecoder.parsePEM(key.toCharArray()))
}


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
 * Small wrapper around [LocalPortForwarder].
 *
 * Once connection has been successfully established via [host] & [port],
 * this gate should be closed to stop new connections.
 */
class TunnelGate(val host: String, val port: Int, private val forwarder: LocalPortForwarder) : Closeable {
    override fun close() {
        forwarder.close()
    }
}

/**
 * Manager for SSH Tunnel
 */
class SshTunnel(private val viewModel: VncViewModel) {

    private var connection: Connection? = null
    private val localHost = "127.0.0.1"

    /**
     * Opens the tunnel according to current profile in [viewModel].
     */
    fun open(): TunnelGate {
        val profile = viewModel.profile
        val connection = connect(profile)
        this.connection = connection

        when (profile.sshAuthType) {
            ServerProfile.SSH_AUTH_PASSWORD -> {
                val password = viewModel.getLoginInfo(LoginInfo.Type.SSH_PASSWORD).password
                connection.authenticateWithPassword(profile.sshUsername, password)
            }
            ServerProfile.SSH_AUTH_KEY -> {
                var keyPassword = ""
                if (isPrivateKeyEncrypted(profile.sshPrivateKey))
                    keyPassword = viewModel.getLoginInfo(LoginInfo.Type.SSH_KEY_PASSWORD).password
                connection.authenticateWithPublicKey(profile.sshUsername, profile.sshPrivateKey.toCharArray(), keyPassword)
            }
            else -> throw IOException("Unknown SSH auth type: ${profile.sshAuthType}")
        }

        if (!connection.isAuthenticationComplete)
            throw IOException("SSH authentication failed")


        // SSHLib does not expose internal ServerSocket used for local port forwarder.
        // Hence, if we pass 0 as local port to let the system pick a port for us, we have no way
        // to know the port system picked.
        // So we create a temporary ServerSocket, close it immediately and try to use its port.
        // But between the close-reuse, that port can be assigned to someone else, so we try again.
        for (i in 1..50) {
            val attemptedPort = ServerSocket(0).use { it.localPort }
            val address = InetSocketAddress(localHost, attemptedPort)

            try {
                val forwarder = connection.createLocalPortForwarder(address, profile.host, profile.port)
                return TunnelGate(localHost, attemptedPort, forwarder)
            } catch (e: IOException) {
                //Retry
            }
        }
        throw IOException("Cannot find a local port for SSH Tunnel")
    }

    /**
     * It is possible for a host to have multiple IP addresses.
     * If connection failed due to [NoRouteToHostException], we try the next address (if available).
     */
    private fun connect(profile: ServerProfile): Connection {
        for (address in InetAddress.getAllByName(profile.sshHost)) {
            try {
                return Connection(address.hostAddress, profile.sshPort).apply { connect(HostKeyVerifier(viewModel)) }
            } catch (e: IOException) {
                if (e.cause is NoRouteToHostException) continue
                else throw e
            }
        }
        // We will reach here only if every address throws NoRouteToHostException
        throw NoRouteToHostException("Unreachable SSH host: ${profile.sshHost}")
    }

    fun close() {
        connection?.close()
    }
}
