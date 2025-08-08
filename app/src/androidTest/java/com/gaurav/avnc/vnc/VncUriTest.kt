/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import com.gaurav.avnc.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VncUriTest {

    @Test
    fun blankTest() {
        val uri = VncUri("")
        assertNull(uri.host)
        assertNull(uri.port)
        assertNull(uri.connectionName)
        assertNull(uri.sshHost)
    }

    @Test
    fun simpleUriTest() {
        val uri = VncUri("vnc://10.0.0.1:5901/?VncPassword=foo&SecurityType=2&ViewOnly=true")
        assertEquals("10.0.0.1", uri.host)
        assertEquals(5901, uri.port)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
    }

    @Test
    fun sshUriTest() {
        val uri = VncUri("vnc://10.0.0.1/?ChannelType=24&SshHost=10.0.0.2&SshPort=222&SshPassword=foo&SshUsername=bar")
        assertEquals("10.0.0.1", uri.host)
        assertEquals("10.0.0.2", uri.sshHost)
        assertEquals(222, uri.sshPort)
        assertEquals("foo", uri.sshPassword)
        assertEquals("bar", uri.sshUsername)
        assertEquals(ServerProfile.CHANNEL_SSH_TUNNEL, uri.channelType)
    }

    @Test
    fun ipv6Test1() {
        val uri = VncUri("[fe80::2a10:8d70:b54:b62b]")
        assertEquals("fe80::2a10:8d70:b54:b62b", uri.host)
    }

    @Test
    fun ipv6Test2() {
        val uri = VncUri("vnc://[fe80::2a10:8d70:b54:b62b]:123/?VncPassword=foo&SecurityType=2&ViewOnly=true")
        assertEquals("fe80::2a10:8d70:b54:b62b", uri.host)
        assertEquals(123, uri.port)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
    }

    @Test
    fun ipv6InvalidAddressTest() {
        //Parsing should fail for this address because it is invalid.
        val uri = VncUri("[fe80:2a10:b62b]")
        assertNull(uri.host)
        assertNull(uri.connectionName)
        assertNull(uri.connectionNameForProfile)
    }

    @Test
    fun schemaVariationTest() {
        // We should gracefully handle these "invalid" schema types
        assertEquals("vnc://host", VncUri("host").toString())       // Completely missing
        assertEquals("vnc://host", VncUri("vnc:host").toString())   // Slashes missing
        assertEquals("vnc://host", VncUri("vnc:/host").toString())  // Slash missing
        assertEquals("vnc://host", VncUri("VNC://host").toString()) // Uppercase

        // Make sure only start of URI is checked for variations
        assertEquals("vnc://avnc://host", VncUri("avnc://host").toString())
    }

    @Test
    fun applyToProfileTest() {
        val profile = ServerProfile(name = "Name1", host = "10.0.0.1", username = "User1")
        val uri = VncUri("vnc://10.0.0.2:5901/?ConnectionName=Name2&VncPassword=foo&ViewOnly=true")

        uri.applyToProfile(profile)
        assertEquals("10.0.0.2", profile.host)
        assertEquals(5901, profile.port)
        assertEquals("foo", profile.password)
        assertEquals(ServerProfile.VIEW_MODE_NO_INPUT, profile.viewMode)
        assertEquals("Name2", profile.name)

        // These were not given in URI, so should remain unchanged
        assertEquals("User1", profile.username)

        // Only SSH password type is supported in URIs
        assertEquals(ServerProfile.SSH_AUTH_PASSWORD, profile.sshAuthType)
    }
}