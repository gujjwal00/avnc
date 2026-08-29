/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import com.gaurav.avnc.model.db.ServerProfileDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecodingException

/**
 * Maps a QR-code payload to a [ServerProfile] and persists it.
 *
 * All failures are reported via the returned [Result] — never by throwing to the caller.
 */
object QrProfileImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes [raw] JSON, validates its payload, maps it to a [ServerProfile] and persists it.
     * Returns the saved profile, or a failure describing why the import could not be done.
     *
     * Note: [dao.save] is a `suspend` function, so this must run inside a coroutine. The
     * non-suspending decode/validate/map steps are wrapped in [runCatching]; the suspending
     * persistence step runs afterwards in the suspend context.
     */
    suspend fun importFromRawJson(raw: String, dao: ServerProfileDao): Result<ServerProfile> {
        val profile = runCatching {
            val dto = try {
                json.decodeFromString(QrServerProfileDto.serializer(), raw)
            } catch (e: JsonDecodingException) {
                throw IllegalArgumentException("Cannot parse QR code content", e)
            }

            if (dto.type != "avnc_server_profile")
                throw IllegalArgumentException("Unrecognized QR code type: ${dto.type ?: "null"}")

            validate(dto)
            mapToProfile(dto)
        }.getOrElse { return Result.failure(it) }

        // Avoid creating duplicate profiles for the same server: reuse an existing profile's ID.
        return try {
            dao.getByName(profile.name)
                    .firstOrNull { it.host == profile.host && it.port == profile.port }
                    ?.let { profile.ID = it.ID }

            dao.save(profile)
            Result.success(profile)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Rejects payloads that cannot produce a usable connection.
     */
    private fun validate(dto: QrServerProfileDto) {
        if (dto.host.isBlank())
            throw IllegalArgumentException("Server host is required")
        if (dto.port !in 1..65535)
            throw IllegalArgumentException("Invalid port number: ${dto.port}")
        val ssh = dto.sshTunnel
        if (ssh?.enabled == true && ssh.host.isBlank())
            throw IllegalArgumentException("SSH tunnel host is required when the tunnel is enabled")
    }

    private fun mapToProfile(dto: QrServerProfileDto): ServerProfile {
        val ssh = dto.sshTunnel
        return ServerProfile(
                name = dto.name,
                host = dto.host,
                port = dto.port,
                username = dto.username,
                // Password is intentionally NOT part of the QR payload (security).
                viewMode = if (dto.viewOnly) ServerProfile.VIEW_MODE_NO_INPUT else ServerProfile.VIEW_MODE_NORMAL,
                colorLevel = mapColorDepth(dto.colorDepth),
                channelType = if (ssh?.enabled == true) ServerProfile.CHANNEL_SSH_TUNNEL else ServerProfile.CHANNEL_TCP,
                sshHost = ssh?.host ?: "",
                sshPort = ssh?.port ?: 22,
                sshUsername = ssh?.username ?: "",
        )
    }

    /**
     * Maps the QR `colorDepth` string to the native [ServerProfile.colorLevel] integer.
     * Values follow the standard VNC color formats:
     *   8-bit -> 1, 16-bit -> 4, 24-bit -> 7, 32-bit -> 8.
     * Unknown/invalid values fall back to 24-bit (7).
     */
    private fun mapColorDepth(value: String?): Int = when (value) {
        "COLOR_8BIT" -> 1
        "COLOR_16BIT" -> 4
        "COLOR_24BIT" -> 7
        "COLOR_32BIT" -> 8
        else -> 7
    }
}
