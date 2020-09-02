/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class VncUriTest {

    @Test
    fun defaultUriTest() {
        val uri = VncUri(Uri.parse(""))
        assertEquals("", uri.host)
        assertEquals(5900, uri.port)
        assertEquals("", uri.username)
        assertEquals("", uri.password)
    }

    @Test
    fun simpleUriTest1() {
        val uri = VncUri(Uri.parse("vnc://10.0.0.1:5901?VncPassword=foo&SecurityType=2&ViewOnly=true"))
        assertEquals("10.0.0.1", uri.host)
        assertEquals(5901, uri.port)
        assertEquals("", uri.username)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
    }

    @Test
    fun simpleUriTest2() { //Same as above but with String argument to constructor
        val uri = VncUri("vnc://10.0.0.1:5901?VncPassword=foo&SecurityType=2&ViewOnly=true")
        assertEquals("10.0.0.1", uri.host)
        assertEquals(5901, uri.port)
        assertEquals("", uri.username)
        assertEquals("foo", uri.password)
        assertEquals(2, uri.securityType)
        assertEquals(true, uri.viewOnly)
    }

    @Test
    fun basicHostTest() {
        val uri = VncUri("host")
        assertEquals("host", uri.host)
    }
}