/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gaurav.avnc.model.ServerProfile
import com.trilead.ssh2.crypto.PEMDecoder
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.forward.ForwardingFilter
import org.apache.sshd.server.forward.TcpForwardingFilter
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.PublicKey

class SshTunnelScenario {
    val sshServer = SshServer.setUpDefaultServer()
    val vncSession = VncSessionScenario()
    val profile get() = vncSession.profile

    private lateinit var testUser: String
    private lateinit var testPassword: String
    private lateinit var testPubKey: PublicKey

    init {
        sshServer.apply {
            host = InetAddress.getLocalHost().hostAddress
            port = ServerSocket(0).use { it.localPort }
            forwardingFilter = ForwardingFilter.asForwardingFilter(null, null, TcpForwardingFilter.DEFAULT)
            keyPairProvider = KeyPairProvider.wrap(hostKeyPair)
            passwordAuthenticator = PasswordAuthenticator { u, p, _ ->
                u == testUser && p == testPassword
            }
            publickeyAuthenticator = PublickeyAuthenticator { u, k, _ ->
                u == testUser && k.equals(testPubKey)
            }
        }

        profile.channelType = ServerProfile.CHANNEL_SSH_TUNNEL
        profile.sshHost = sshServer.host
        profile.sshPort = sshServer.port
    }

    fun setupAuthWithPassword(user: String, password: String) {
        testUser = user
        testPassword = password
        profile.sshAuthType = ServerProfile.SSH_AUTH_PASSWORD
        profile.sshUsername = testUser
        profile.sshPassword = testPassword
    }

    fun setupAuthWithKey(user: String, key: String, keyPassword: String?) {
        val pubKey = PEMDecoder.decode(key.toCharArray(), keyPassword).public
        setupAuthWithKey(user, pubKey, key)
    }

    fun setupAuthWithKey(user: String, pubKey: PublicKey, keyStr: String) {
        testUser = user
        testPubKey = pubKey
        profile.sshAuthType = ServerProfile.SSH_AUTH_KEY
        profile.sshUsername = testUser
        profile.sshPrivateKey = keyStr
    }

    fun start() = apply {
        sshServer.start()
        vncSession.startServer()
        vncSession.startActivity()
    }

    fun checkAndTrustHostFingerprint() {
        onView(withText(R.string.title_unknown_ssh_host)).checkWillBeDisplayed()
        onView(withSubstring(hostFingerprint)).checkWillBeDisplayed()
        onView(withText(R.string.title_continue)).doClick()
    }

    fun stop() = apply {
        vncSession.stop()
        sshServer.stop()
    }

    companion object {
        private val HOST_KEY = """
                    -----BEGIN OPENSSH PRIVATE KEY-----
                    b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAlwAAAAdzc2gtcn
                    NhAAAAAwEAAQAAAIEA0UtMRu2BZUpggGVwqbrxkoknLndRMClkofe148MFMBqT3drKFbMo
                    3I2qQeqOIL0XesRnJz1uXz2oYlwtP1BJqu6uxDvcgu2RUsD4/P5LmoOJgzX3j38jNihkGU
                    IN2pIEmXsJu7oOP2vdS7GD1WfBs1/nyHW8i53Oaa//98YE5E0AAAIITX/Jvk1/yb4AAAAH
                    c3NoLXJzYQAAAIEA0UtMRu2BZUpggGVwqbrxkoknLndRMClkofe148MFMBqT3drKFbMo3I
                    2qQeqOIL0XesRnJz1uXz2oYlwtP1BJqu6uxDvcgu2RUsD4/P5LmoOJgzX3j38jNihkGUIN
                    2pIEmXsJu7oOP2vdS7GD1WfBs1/nyHW8i53Oaa//98YE5E0AAAADAQABAAAAgG9RFkPPRP
                    hDw+nmijKsTJo8uos7SQJNscl3v9VhP5wjNqxUFxHNlZkg/AJNJ8T/7cINPjQft1mOqMWP
                    8zzujg8V4vuu7TEXpVh3cqshXwkWVgGz/7M3Q/fFjG5uj813/hxM573ymJQZ5HouI7T/He
                    jtse+uLidGWDiTtNV8WwuhAAAAQQDV8Iq28srDFKUVs6JMl0Ur6h/YGh39eiACl1cpx9/G
                    KrgZzOqzkBgAeZT5k2oezvbpA89wI+LddBs7LJooUUSgAAAAQQD9UMj89wfccLF3zMXIe6
                    qMWjp6MHr0N9CAZHg+f8OU1b92hGvvQi6DF4ES3qoeQ/D5oNehX5M/d5dlLVeoePKpAAAA
                    QQDTgxaEBZUeSQP1OfmAz5p/T3swUj7j3WnNTlpCT293mcwMd4rO9R2TIGei2rNdCPwbRV
                    ZA3JLs7Mj+tBgEVk8FAAAAD2dhdXJhdkBlbGVjdHJvbgECAw==
                    -----END OPENSSH PRIVATE KEY-----
                    """.trimIndent()

        val hostKeyPair = PEMDecoder.decode(HOST_KEY.toCharArray(), null)!!
        val hostFingerprint = "SHA256:h4L6a6+kb54WbUxcK4FqsS3ebocJc5ZhiDB56d32Zdk"

        init {
            val wd = Files.createTempDirectory("mina-sshd")
            OsUtils.setAndroid(true)
            OsUtils.setCurrentUser("Ross")
            OsUtils.setCurrentWorkingDirectoryResolver { wd }
            PathUtils.setUserHomeFolderResolver { wd }
        }
    }
}