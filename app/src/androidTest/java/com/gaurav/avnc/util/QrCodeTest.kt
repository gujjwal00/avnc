package com.gaurav.avnc.util

import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    @Test
    fun decodeJson() {
        val actual = QrCode.decode("AVNC:DATA;{\"profiles\":[]}")

        val json = actual as? QrCode.Content.Json
            ?: throw AssertionError("Expected Json content, got $actual")
        assertEquals("{\"profiles\":[]}", json.json)
    }

    @Test
    fun decodeJsonWithOption() {
        val actual = QrCode.decode("AVNC:DATA:opt1:opt2;{json}")

        val json = actual as? QrCode.Content.Json
            ?: throw AssertionError("Expected Json content, got $actual")
        assertEquals("{json}", json.json)
    }

    @Test
    fun decodeJsonPreservesSeparatorsInPayload() {
        // ':' and ';' inside the JSON payload should be preserved
        val actual = QrCode.decode("AVNC:DATA;{\"a\":\"x;y:z\"}")

        val json = actual as? QrCode.Content.Json
            ?: throw AssertionError("Expected Json content, got $actual")
        assertEquals("{\"a\":\"x;y:z\"}", json.json)
    }

    @Test
    fun decodeUri() {
        val actual = QrCode.decode("AVNC:URI;vnc://10.0.0.1:5901/")

        val uri = actual as? QrCode.Content.Uri
            ?: throw AssertionError("Expected Uri content, got $actual")
        assertEquals("vnc", uri.uri.scheme)
        assertEquals("10.0.0.1", uri.uri.host)
    }

    @Test
    fun decodeUriWithOption() {
        val actual = QrCode.decode("AVNC:URI:opt;https://example.com/config")

        val uri = actual as? QrCode.Content.Uri
            ?: throw AssertionError("Expected Uri content, got $actual")
        assertEquals("https", uri.uri.scheme)
        assertEquals("example.com", uri.uri.host)
    }

    @Test
    fun rejectAppendedTextInHeader() {
        // "AVNC:DATABLAH" should not be treated as AVNC:DATA
        assertThrows { QrCode.decode("AVNC:DATABLAH;{}") }
    }

    @Test
    fun rejectUnknownType() {
        assertThrows { QrCode.decode("AVNC:FOO;{}") }
    }

    @Test
    fun rejectMissingHeader() {
        assertThrows { QrCode.decode("{}") }
    }

    @Test
    fun decodeBlankJsonPayload() {
        // Structural decoding succeeds; JSON validity is checked by the importer later.
        val actual = QrCode.decode("AVNC:DATA;")

        val json = actual as? QrCode.Content.Json
            ?: throw AssertionError("Expected Json content, got $actual")
        assertEquals("", json.json)
    }

    @Test
    fun encodeJson() {
        val encoded = QrCode.encode("{\"profiles\":[]}")
        assertEquals("AVNC:DATA;{\"profiles\":[]}", encoded)
    }

    @Test
    fun encodeThenDecodeJson() {
        val original = "{\"a\":\"x;y:z\"}"
        val decoded = QrCode.decode(QrCode.encode(original))

        val json = decoded as? QrCode.Content.Json
            ?: throw AssertionError("Expected Json content, got $decoded")
        assertEquals(original, json.json)
    }

    private fun assertThrows(block: () -> Any?) {
        try {
            block()
        } catch (_: QrCode.InvalidQrCodeException) {
            return
        }
        throw AssertionError("Expected QrCode.InvalidQrCodeException")
    }

}
