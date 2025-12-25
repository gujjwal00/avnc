/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel.service

import android.system.ErrnoException
import android.system.OsConstants
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.model.ServerProfile
import com.trilead.ssh2.Connection
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.crypto.OpenSSHKeyEncoder
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.PEMStructure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ServerSocket
import java.security.KeyPair
import java.security.MessageDigest

/**
 * Implements Host Key verification.
 *
 * Known hosts & keys are stored in a file inside app's private storage.
 * For unknown host, user is prompted to confirm the key.
 */
class HostKeyVerifier(private val observer: SshTunnel.Observer) : ServerHostKeyVerifier {

    override fun verifyServerHostKey(hostname: String, port: Int, keyAlgorithm: String, key: ByteArray): Boolean {
        val knownHostsFile = observer.getKnownSshHostsFile()
        val knownHosts = KnownHosts(knownHostsFile)
        val verificationResult = knownHosts.verifyHostkey(hostname, keyAlgorithm, key)

        if (verificationResult == KnownHosts.HOSTKEY_IS_OK)
            return true

        // Unknown key, confirm with user
        val keyDigest = MessageDigest.getInstance("SHA-256").digest(key)
        val keyDigestStr = Base64.encodeToString(keyDigest, Base64.NO_PADDING)
        val isNew = (verificationResult == KnownHosts.HOSTKEY_IS_NEW)
        val message = """
                 |
                 |Host:   $hostname
                 |Key type:   ${keyAlgorithm.uppercase()}
                 |Key fingerprint: 
                 |  
                 |SHA256:$keyDigestStr
                 |
                 |Make sure your are connecting to correct SSH host.
                 |
                 |If you continue, this key will be added to trusted list.
                 """.trimMargin()

        if (observer.confirmHostKey(message, isNew)) {
            //User has confirmed the key, so remember it.
            KnownHosts.addHostkeyToFile(knownHostsFile, arrayOf(hostname), keyAlgorithm, key)
            return true
        }

        return false
    }
}

/**
 * Handler for PEM encoded private keys
 */
class PemKey(source: String) {
    private var keyPair: KeyPair? = null
    private val ps: PEMStructure?

    init {
        if (source.contains("-----BEGIN PRIVATE KEY-----")) {
            ps = null
            keyPair = parsePKCS8Key(source)
        } else {
            ps = PEMDecoder.parsePEM(source.toCharArray())
            if (!PEMDecoder.isPEMEncrypted(ps))
                keyPair = PEMDecoder.decode(ps, "")
        }

        check(keyPair != null || ps != null) { "Unsupported private key file" }
    }

    private fun parsePKCS8Key(key: String): KeyPair {
        val pkBytes = key.substringAfter("-----BEGIN PRIVATE KEY-----")
                .substringBefore("-----END PRIVATE KEY-----")
                .let { kotlin.io.encoding.Base64.Mime.decode(it) }

        return OpenSSHKeyEncoder.recoverKeyPair(pkBytes)
    }

    fun isEncrypted(): Boolean {
        return keyPair == null && PEMDecoder.isPEMEncrypted(ps)
    }

    fun decrypt(password: String) {
        checkNotNull(ps)
        keyPair = PEMDecoder.decode(ps, password)
    }

    fun getKeyPair() = keyPair!!
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

class SshTunnelException(message: String = "", cause: Throwable? = null) : IOException(message, cause)

/**
 * Manager for SSH Tunnel
 */
class SshTunnel(private val observer: Observer) {

    interface Observer {
        fun getKnownSshHostsFile(): File
        fun confirmHostKey(message: String, isNewHost: Boolean): Boolean
        fun getSshPassword(): String
        fun getSshKeyPassword(): String
    }

    private var connection: Connection? = null
    private val localHost = "127.0.0.1"

    /**
     * Opens the tunnel according to [profile]
     */
    fun open(profile: ServerProfile): TunnelGate {
        check(connection == null) { "Connection already open" }

        connection = connect(profile)
        authenticate(connection!!, profile)
        return createPortForwarder(connection!!, profile)
    }

    fun close() {
        connection?.close()
        connection = null
    }


    /**
     * It is possible for a host to have multiple IP addresses.
     * If connection failed due to [NoRouteToHostException], we try the next address (if available).
     */
    private fun connect(profile: ServerProfile): Connection {
        for (address in InetAddress.getAllByName(profile.sshHost)) {
            try {
                return Connection(address.hostAddress, profile.sshPort).apply { connect(HostKeyVerifier(observer)) }
            } catch (e: IOException) {
                if (e.cause is NoRouteToHostException) continue
                else throw unwrapLibraryException(e)
            }
        }
        // We will reach here only if every address throws NoRouteToHostException
        throw SshTunnelException("Unreachable SSH host: ${profile.sshHost}")
    }

    private fun authenticate(connection: Connection, profile: ServerProfile) {
        when (profile.sshAuthType) {
            ServerProfile.SSH_AUTH_PASSWORD -> {
                val password = observer.getSshPassword()  //Possibly blocking call
                connection.authenticateWithPassword(profile.sshUsername, password)
                if (!connection.isAuthenticationComplete)
                    throw SshTunnelException("SSH Password authentication failed")
            }
            ServerProfile.SSH_AUTH_KEY -> {
                val pk = profile.sshPrivateKey
                val cached = KeyCache.get(pk)
                if (cached != null) {
                    connection.authenticateWithPublicKey(profile.sshUsername, cached)
                } else {
                    val pemKey = PemKey(pk)
                    if (pemKey.isEncrypted()) {
                        val password = observer.getSshKeyPassword()  //Blocking call
                        pemKey.decrypt(password)
                    }
                    val keyPair = pemKey.getKeyPair()
                    connection.authenticateWithPublicKey(profile.sshUsername, keyPair)
                    KeyCache.put(pk, keyPair)
                }

                if (!connection.isAuthenticationComplete)
                    throw SshTunnelException("SSH Key authentication failed")
            }
            else -> throw SshTunnelException("Unknown SSH auth type: ${profile.sshAuthType}")
        }

        if (!connection.isAuthenticationComplete)
            throw SshTunnelException("SSH authentication failed")
    }

    private fun createPortForwarder(connection: Connection, profile: ServerProfile): TunnelGate {
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
            } catch (e: Throwable) {
                throw unwrapLibraryException(e)
            }
        }
        throw SshTunnelException("Cannot find a local port for SSH Tunnel")
    }

    /**
     * In many error conditions, SSHLib throws [IOException] with a generic message
     * like 'An error happened when connecting'. This gives no indication to users about
     * actual cause of the error. So this function attempts to find the root cause of the
     * error, and provide a better message to the user.
     */
    private fun unwrapLibraryException(e: Throwable): Throwable {
        if (e is SshTunnelException) return e

        // collect error history
        val errors = mutableListOf(e)
        var cause = e.cause
        while (cause != null) {
            errors.add(cause)
            cause = cause.cause
        }

        errors.find { it is ErrnoException }?.let {
            val msg = when ((it as ErrnoException).errno) {
                OsConstants.ECONNREFUSED -> "SSH server is not running, or port is incorrect"
                OsConstants.ECONNABORTED -> "SSH connection aborted"
                OsConstants.ECONNRESET -> "SSH connection closed abruptly by remote host"
                else -> "SSH: " + it.message?.substringAfter('(')?.substringBefore(')')
            }
            return SshTunnelException(msg, e)
        }

        return e
    }

    /**
     * A very simple key cache to keep unlocked/decoded keys in memory
     * Strategy:
     * 1. Keep keys in memory as long as app is in foreground
     * 2. Clear cache if app goes in background for more than 15 minutes
     */
    private object KeyCache {
        private val cache = mutableMapOf<String, KeyPair>()
        private var lifecycleObserver: DefaultLifecycleObserver? = null

        fun get(pk: String): KeyPair? {
            synchronized(cache) {
                return cache[pk]
            }
        }

        fun put(pk: String, keyPair: KeyPair) {
            synchronized(cache) {
                cache[pk] = keyPair
                addLifecycleObserver()
            }
        }

        private fun addLifecycleObserver() {
            if (lifecycleObserver != null)
                return // Already added

            lifecycleObserver = object : DefaultLifecycleObserver {
                var cleanupJob: Job? = null

                override fun onStart(owner: LifecycleOwner) {
                    cleanupJob?.let { if (it.isActive) it.cancel() }
                    cleanupJob = null
                }

                override fun onStop(owner: LifecycleOwner) {
                    cleanupJob = owner.lifecycleScope.launch {
                        delay(15 * 60 * 1000)
                        synchronized(cache) {
                            cache.clear()
                        }
                    }
                }
            }

            ProcessLifecycleOwner.get().let {
                // Observer needs to be set on main thread,
                // and lifecycleScope is already bound to main thread
                it.lifecycleScope.launch {
                    it.lifecycle.addObserver(lifecycleObserver!!)
                }
            }
        }
    }
}
