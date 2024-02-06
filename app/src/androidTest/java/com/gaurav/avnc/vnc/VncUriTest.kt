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
import org.junit.Test

class VncUriTest {

    @Test
    fun blankTest() {
        val uri = VncUri("")
        assertEquals("", uri.host)
    }

    @Test
    fun basicHostTest() {
        val uri = VncUri("host")
        assertEquals("host", uri.host)
        assertEquals(5900, uri.port)
        assertEquals("", uri.username)
        assertEquals("", uri.password)
    }

    @Test
    fun simpleUriTest() {
        val uri = VncUri("vnc://10.0.0.1:5901/?VncPassword=foo&SecurityType=2&ViewOnly=true")
        assertEquals("10.0.0.1", uri.host)
        assertEquals(5901, uri.port)
        assertEquals("", uri.username)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
        assertEquals(ServerProfile.CHANNEL_TCP, uri.channelType)
    }

    @Test
    fun ipv6Test1() {
        val uri = VncUri("[fe80::2a10:8d70:b54:b62b]")
        assertEquals("fe80::2a10:8d70:b54:b62b", uri.host)
        assertEquals(5900, uri.port)
    }

    @Test
    fun ipv6Test2() {
        val uri = VncUri("vnc://[fe80::2a10:8d70:b54:b62b]:123/?VncPassword=foo&SecurityType=2&ViewOnly=true")
        assertEquals("fe80::2a10:8d70:b54:b62b", uri.host)
        assertEquals(123, uri.port)
        assertEquals("", uri.username)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
    }

    @Test
    fun ipv6InvalidAddressTest() {
        //Parsing should fail for this address because it is invalid.
        val uri = VncUri("[fe80:2a10:b62b]")
        assertEquals("", uri.host)
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
}