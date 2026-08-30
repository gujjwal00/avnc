/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import com.gaurav.avnc.model.db.ServerProfileDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import java.net.URL

data class AvncQrPayload(
    val type: Char,
    val options: List<String> = emptyList(),
    val payload: String
)

private const val AVNC_PREFIX = "AVNC:"

private fun parseAvncFormat(raw: String): AvncQrPayload? {
    if (!raw.startsWith(AVNC_PREFIX)) return null
    val content = raw.substring(AVNC_PREFIX.length)
    val sepIndex = content.indexOf(';')
    if (sepIndex < 0) return null
    val header = content.substring(0, sepIndex)
    val payload = content.substring(sepIndex + 1)
    val headerParts = header.split(":")
    val type = headerParts.firstOrNull()?.firstOrNull() ?: return null
    val options = if (headerParts.size > 1) headerParts.drop(1) else emptyList()
    return AvncQrPayload(type, options, payload)
}

private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
    URL(url).openConnection().apply { connectTimeout = 10000; readTimeout = 10000 }
            .getInputStream().bufferedReader().use { it.readText() }
}

object QrProfileImporter {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromRawJson(raw: String, dao: ServerProfileDao): Result<ServerProfile> {
        val profile = runCatching {
            val dto = resolveDto(raw)
            validate(dto)
            mapToProfile(dto)
        }.getOrElse { return Result.failure(it) }

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

    private suspend fun resolveDto(raw: String): QrServerProfileDto {
        val avnc = parseAvncFormat(raw)
        val content = when (avnc?.type) {
            'U' -> fetchUrl(avnc.payload)
            'F' -> avnc.payload
            else -> raw
        }

        val dto = try {
            json.decodeFromString(QrServerProfileDto.serializer(), content)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("Cannot parse QR code content", e)
        }

        if (avnc == null && dto.type != "avnc_server_profile")
            throw IllegalArgumentException("Unrecognized QR code type: ${dto.type ?: "null"}")

        return dto
    }

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
                viewMode = if (dto.viewOnly) ServerProfile.VIEW_MODE_NO_INPUT else ServerProfile.VIEW_MODE_NORMAL,
                colorLevel = mapColorDepth(dto.colorDepth),
                channelType = if (ssh?.enabled == true) ServerProfile.CHANNEL_SSH_TUNNEL else ServerProfile.CHANNEL_TCP,
                sshHost = ssh?.host ?: "",
                sshPort = ssh?.port ?: 22,
                sshUsername = ssh?.username ?: "",
        )
    }

    private fun mapColorDepth(value: String?): Int = when (value) {
        "COLOR_8BIT" -> 1
        "COLOR_16BIT" -> 4
        "COLOR_24BIT" -> 7
        "COLOR_32BIT" -> 8
        else -> 7
    }
}
