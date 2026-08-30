/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import kotlinx.serialization.Serializable

/**
 * Wire format for a server profile shared via QR code.
 *
 * Only the fields needed to connect are included; AVNC fills the rest with defaults.
 */
@Serializable
data class QrServerProfileDto(
        val version: Int = 1,
        val type: String? = null,
        val name: String = "",
        val host: String = "",
        val port: Int = 5900,
        val username: String = "",
        val viewOnly: Boolean = false,
        val colorDepth: String? = null,
        val sshTunnel: SshTunnel? = null,
) {
    @Serializable
    data class SshTunnel(
            val enabled: Boolean = false,
            val host: String = "",
            val port: Int = 22,
            val username: String = "",
    )
}
