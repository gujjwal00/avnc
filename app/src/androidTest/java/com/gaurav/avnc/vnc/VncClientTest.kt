/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import com.gaurav.avnc.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate

class VncClientTest {

    open class TestObserver : VncClient.Observer {
        var cutText = ""
        var password = ""

        override fun getVncPassword() = password
        override fun getVncCredentials() = UserCredential()
        override fun verifyVncServerCertificate(certificate: X509Certificate) = false
        override fun onFramebufferUpdated() {}
        override fun onFramebufferSizeChanged(width: Int, height: Int) {}
        override fun onPointerMoved(x: Int, y: Int) {}
        override fun onCutTextReceived(text: String) {
            cutText = text
        }

        override fun onBell() {}
    }


    private lateinit var server: TestServer
    private lateinit var client: VncClient
    private lateinit var observer: TestObserver

    private val sampleText = "Smelly Cat"
    private val sampleTextWithAccent = "Pokémon Fõbár"


    @Before
    fun before() {
        server = TestServer()
        observer = TestObserver()
        client = VncClient(observer)
    }

    private fun connect() {
        server.start()
        client.connect(server.host, server.port)
        client.processServerMessage()
    }


    /*************************************************************************/

    @Test
    fun serverName() {
        server = TestServer(sampleText)
        connect()
        assertEquals(sampleText, client.getDesktopName())
    }

    @Test
    fun serverNameWithAccent() {
        server = TestServer(sampleTextWithAccent)
        connect()
        assertEquals(sampleTextWithAccent, client.getDesktopName())
    }

    @Test
    fun serverCutText() {
        connect()
        server.sendCutText(sampleText)
        client.processServerMessage()
        assertEquals(sampleText, observer.cutText)
    }

    @Test
    fun serverCutTextWithAccent() {
        connect()
        server.sendCutText(sampleTextWithAccent)
        client.processServerMessage()
        assertEquals(sampleTextWithAccent, observer.cutText)
    }

    @Test
    fun clientCutText() {
        connect()
        client.sendCutText(sampleText)
        client.cleanup()
        server.awaitStop()
        assertEquals(sampleText, server.receivedCutText)
    }

    @Test
    fun clientCutTextWithAccent() {
        connect()
        client.sendCutText(sampleTextWithAccent)
        client.cleanup()
        server.awaitStop()
        assertEquals(sampleTextWithAccent, server.receivedCutText)
    }

    @Test
    fun vncAuth() {
        val testPassword = "Pivot!"
        server.setupVncAuth(testPassword)
        observer.password = testPassword

        connect()
        assertTrue(client.connected)

        client.cleanup()
        server.awaitStop()
    }
}