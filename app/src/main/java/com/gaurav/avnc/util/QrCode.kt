package com.gaurav.avnc.util

import android.net.Uri as URI
import androidx.core.net.toUri

object QrCode {

    class InvalidQrCodeException : Exception()

    sealed class Content {
        data class Json(val json: String) : Content()
        data class Uri(val uri: URI) : Content()
    }

    /**
     * Parses AVNC QR content of the form `AVNC:TYPE[:option]*;<payload>`.
     */
    fun decode(content: String): Content {
        val sep = content.indexOf(';')
        val header = if (sep >= 0) content.substring(0, sep) else content
        val payload = if (sep >= 0) content.substring(sep + 1) else ""

        return when {
            header == "AVNC:DATA" || header.startsWith("AVNC:DATA:") -> Content.Json(payload)
            header == "AVNC:URI" || header.startsWith("AVNC:URI:") -> Content.Uri(payload.toUri())
            else -> throw InvalidQrCodeException()
        }
    }

    /**
     * Encodes the given [json] as AVNC QR content of the form `AVNC:DATA;<json>`.
     */
    fun encode(json: String): String = "AVNC:DATA;$json"

}
