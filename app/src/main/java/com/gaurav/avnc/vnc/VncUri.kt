/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

import android.net.Uri

/**
 * This class wraps a [Uri] and provides accessor properties for various
 * components specific to [vnc URI Scheme](https://tools.ietf.org/html/rfc7869).
 */
class VncUri(private val uri: Uri) {

    /**
     * Allows creating new instance from string.
     * Also adds schema if missing.
     */
    constructor(uriString: String) : this(
            if (uriString.startsWith("vnc://"))
                Uri.parse(uriString)
            else
                Uri.parse("vnc://$uriString")
    )

    val host; get() = uri.host ?: ""
    val port; get() = if (uri.port == -1) 5900 else uri.port
    val connectionName; get() = uri.getQueryParameter("ConnectionName") ?: ""
    val username; get() = uri.getQueryParameter("VncUsername") ?: ""
    val password; get() = uri.getQueryParameter("VncPassword") ?: ""
    val securityType; get() = uri.getQueryParameter("SecurityType")?.toIntOrNull() ?: 0
    val channelType; get() = uri.getQueryParameter("ChannelType")?.toIntOrNull() ?: 0
    val colorLevel; get() = uri.getQueryParameter("ColorLevel")?.toIntOrNull() ?: 7
    val viewOnly; get() = uri.getBooleanQueryParameter("ViewOnly", false)
    val saveConnection; get() = uri.getBooleanQueryParameter("SaveConnection", false)

    //Some more parameter (SSH related) are specified in RFC but they are not
    //used/implemented yet.
}