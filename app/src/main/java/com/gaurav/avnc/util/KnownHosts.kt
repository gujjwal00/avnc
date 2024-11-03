/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import android.content.Context
import android.util.Log
import com.google.crypto.tink.subtle.Hex
import java.io.File
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

// Utilities related to known hosts & certificates

private fun getTrustedCertsDir(context: Context) = File(context.filesDir, "trusted_certs")

private fun getFileForTrustedCertificate(context: Context, certificate: Certificate): File {
    val certDigest = MessageDigest.getInstance("SHA1").digest(certificate.encoded)
    val certFile = Hex.encode(certDigest)
    val certDir = getTrustedCertsDir(context)
    return File(certDir, certFile)
}

/**
 * Adds given [certificate] to trusted list.
 */
fun trustCertificate(context: Context, certificate: Certificate) {
    runCatching {
        val certDir = getTrustedCertsDir(context)
        val certFile = getFileForTrustedCertificate(context, certificate)
        certDir.mkdirs()
        certFile.writeBytes(certificate.encoded)
    }.onFailure {
        Log.e("KnownHosts", "Error trusting certificate", it)
    }
}

/**
 * Checks whether given [certificate] is trusted.
 */
fun isCertificateTrusted(context: Context, certificate: Certificate): Boolean {
    runCatching {
        val trustedFile = getFileForTrustedCertificate(context, certificate)
        if (!trustedFile.exists())
            return false

        // This should always succeed once file exists
        val certFactory = CertificateFactory.getInstance("X.509")
        val trustedCert = trustedFile.inputStream().use { certFactory.generateCertificate(it) }
        if (trustedCert.equals(certificate))
            return true
    }.onFailure {
        Log.w("KnownHosts", "Error checking certificate", it)
    }
    return false
}

@OptIn(ExperimentalStdlibApi::class)
fun getUnknownCertificateMessage(certificate: X509Certificate): String {
    fun commonName(p: X500Principal) = p.name.split(',')  // Doesn't handle escaped comma
                                               .find { it.startsWith("CN=", true) }
                                               ?.drop(3) ?: "Unknown"

    val subject = commonName(certificate.subjectX500Principal)
    val issuer = commonName(certificate.issuerX500Principal)
    val fingerprint = MessageDigest.getInstance("SHA1").digest(certificate.encoded)
            .toHexString(HexFormat { upperCase = true; bytes { byteSeparator = " " } })

    return """
        Certificate received from server is not trusted. Someone might be impersonating the server.
        
        Subject: $subject
        Issuer: $issuer
        Fingerprint (SHA1): $fingerprint 
        
        Make sure you are connecting to right server. Click Continue to add this certificate to trusted list.
    """.trimIndent()
}
