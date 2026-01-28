/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.concurrent.LinkedTransferQueue
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.DHPublicKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * A tiny VNC server.
 *
 * Some tests (mainly for VncActivity) require a VNC server to connect to.
 * This server allow us to run these tests without requiring an external server.
 *
 * It also enables us to completely control the behaviour of the server,
 * so we can simulate different scenarios, error conditions etc.
 */
class TestServer(name: String = "Friends") {

    //Protocol config
    private val protocol = "RFB 003.008\n"
    private val serverName = name.toByteArray()
    private val frameWidth: Short = 10
    private val frameHeight: Short = 10
    private val frameBuffer = ByteArray(frameWidth * frameHeight * 4)

    //Server config
    private val ss = ServerSocket(0)
    private val serverJob = Thread { theServer() }
    private val queuedActions = LinkedTransferQueue<(OutputStream) -> Unit>()
    val host = ss.inetAddress.hostAddress!!
    val port = ss.localPort

    //Security config
    private val securityTypes = mutableListOf<Byte>(1)
    private val securityFailReason = "We should take a break!"
    private var username = ""
    private var password = ""

    @Volatile
    private var stopRequested = false

    //Event log
    val receivedKeySyms = mutableListOf<Pair<Int, Boolean>>()
    val receivedKeyDowns get() = receivedKeySyms.filter { it.second }.map { it.first }
    var receivedCutText = ""; private set
    var receivedIncrementalUpdateRequests = 0; private set


    fun start() {
        if (!serverJob.isAlive && !stopRequested)
            serverJob.start()
    }

    fun stop() {
        stopRequested = true
        ss.close()
        awaitStop()
    }

    fun awaitStop() {
        serverJob.join()
    }

    fun sendCutText(str: String) {
        queuedActions.transfer {
            val bytes = StandardCharsets.ISO_8859_1.encode(str).array()
            it.write(3) //ServerCutText
            it.write(ByteArray(3)) //Padding
            it.write(toByteArray(bytes.size))
            it.write(bytes)
        }
    }

    fun setupVncAuth(password: String) {
        this.password = password
        securityTypes.add(0, 2 /*VncAuth*/)
    }

    fun setupDHAuth(username: String, password: String) {
        this.username = username
        this.password = password
        securityTypes.add(0, 30 /*DHAuth*/)
    }

    /**
     * Behold The Server
     */
    private fun theServer() {
        val socket = ss.use { runCatching { it.accept() }.onFailure { return }.getOrThrow() }
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        //Protocol Handshake
        output.write(protocol.toByteArray())
        input.skip(12)

        //Security Handshake
        output.write(securityTypes.size)
        output.write(securityTypes.toByteArray())
        val successful = when (input.read()) { //Security Type
            // NoAuth
            1 -> true

            //VncAuth
            2 -> {
                val challenge = Random.nextBytes(16)
                val response = ByteArray(16)
                val expected = encryptVncAuthChallenge(challenge, password)

                output.write(challenge)
                input.read(response)

                response.contentEquals(expected)
            }

            // DH/Apple auth
            30 -> {
                val dh = DH()

                output.write(toByteArray(dh.getGen().toShort()))
                output.write(toByteArray(dh.getKeyLengthInBytes().toShort()))
                output.write(dh.getPrimeBytes())
                output.write(dh.getPublicBytes())

                val encrypted = ByteArray(128)
                val clientPublic = ByteArray(dh.getKeyLengthInBytes())
                input.read(encrypted)
                input.read(clientPublic)

                dh.verify(encrypted, clientPublic, username, password)
            }

            else -> false
        }
        output.write(toByteArray(if (successful) 0 else 1))  // Security result
        if (!successful) {
            output.write(toByteArray(securityFailReason.length))
            output.write(securityFailReason.toByteArray())
            socket.close()
            return
        }

        // Init
        input.skip(1)  // Client Init
        output.write(toByteArray(frameWidth))
        output.write(toByteArray(frameHeight))
        output.write(ByteArray(16))  //Pixel format
        output.write(toByteArray(serverName.size))
        output.write(serverName)

        // Msg Loop
        while (!stopRequested) {

            //Read the incoming message type with a timeout to allow
            //processing of queued actions.
            socket.soTimeout = 100
            val msgType = runCatching { input.read() }.getOrNull() ?: -2
            socket.soTimeout = 0

            when (msgType) {

                3 -> { //FramebufferUpdateRequest

                    val incremental = (input.read() == 1)
                    input.skip(8)    //Discard rest of the msg as we always send the whole framebuffer
                    if (incremental) {
                        ++receivedIncrementalUpdateRequests
                        continue //Nothing to send in incremental updates
                    }

                    //Header
                    output.write(toByteArray(1)) //Equivalent to 1 rectangle

                    //Rectangle info
                    output.write(toByteArray(0)) //Equivalent to x,y = 0
                    output.write(toByteArray(frameWidth))
                    output.write(toByteArray(frameHeight))
                    output.write(toByteArray(0)) //Raw encoding

                    output.write(frameBuffer)
                }

                4 -> { //KeyEvent
                    val isDown = input.read() != 0
                    input.skip(2)
                    val key = readInt(input)

                    // Record it
                    receivedKeySyms.add(Pair(key, isDown))
                }

                0 -> input.skip(19) //SetPixelFormat
                5 -> input.skip(5)  //PointerEvent


                2 -> { //SetEncodings
                    input.skip(1) //padding
                    val encodingCount = input.read().shl(8) + input.read()
                    input.skip(encodingCount * 4L)
                }

                6 -> { //ClientCutText
                    input.skip(3)//padding
                    val length = readInt(input)
                    val textBuffer = ByteArray(length)
                    val read = input.read(textBuffer)
                    check(read == length)
                    receivedCutText = textBuffer.toString(StandardCharsets.ISO_8859_1)
                }

                -1 -> break //EOF
            }

            queuedActions.poll()?.invoke(output)
        }

        socket.close()
    }

    private fun toByteArray(i: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(i).order(ByteOrder.BIG_ENDIAN).array()
    }

    private fun toByteArray(s: Short): ByteArray {
        return ByteBuffer.allocate(2).putShort(s).order(ByteOrder.BIG_ENDIAN).array()
    }

    private val intBytes = ByteArray(4)
    private fun readInt(input: InputStream): Int {
        input.read(intBytes)
        return ByteBuffer.wrap(intBytes).order(ByteOrder.BIG_ENDIAN).getInt()
    }


    /**
     * Encrypts given challenge according to VncAuth scheme
     */
    private fun encryptVncAuthChallenge(challenge: ByteArray, password: String): ByteArray {
        val effectivePassword = password.padEnd(8, Char(0)).take(8)
        val keyBytes = effectivePassword.toByteArray().map { reverseBits(it) }.toByteArray()
        val keySpec = DESKeySpec(keyBytes)
        val key = SecretKeyFactory.getInstance("DES").generateSecret(keySpec)
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(challenge)
    }

    private fun reverseBits(b: Byte) = Integer.reverse(b.toInt()).shr(24).toByte()

    /**
     * Implements Apple DH auth scheme
     */
    class DH {
        private val privateKey: DHPrivateKey
        private val publicKey: DHPublicKey

        init {
            val paramSpec = DHParameterSpec(PRIME, GEN)
            val gen = KeyPairGenerator.getInstance("DH")
            gen.initialize(paramSpec)
            val keyPair = gen.generateKeyPair()

            privateKey = keyPair.private as DHPrivateKey
            publicKey = keyPair.public as DHPublicKey
        }

        fun getKeyLengthInBytes() = PRIME.bitLength() / 8
        fun getGen() = GEN.toShort()
        fun getPrimeBytes() = toByteArrayWithoutSignByte(PRIME)
        fun getPublicBytes() = toByteArrayWithoutSignByte(publicKey.y)

        fun verify(encrypted: ByteArray, clientPublic: ByteArray, username: String, password: String): Boolean {
            val clientPubKeySpec = DHPublicKeySpec(BigInteger(1, clientPublic), PRIME, GEN)
            val clientPublicKey = KeyFactory.getInstance("DH").generatePublic(clientPubKeySpec)

            val agreement = KeyAgreement.getInstance("DH")
            agreement.init(privateKey)
            agreement.doPhase(clientPublicKey, true)
            val sharedSecret = agreement.generateSecret()

            val md5 = MessageDigest.getInstance("MD5")
            val secretDigest = md5.digest(sharedSecret)

            val aesKeySpec = SecretKeySpec(secretDigest, "AES")
            val aes = Cipher.getInstance("AES/ECB/NoPadding")
            aes.init(Cipher.DECRYPT_MODE, aesKeySpec)
            val decrypted = aes.doFinal(encrypted)

            check(decrypted.size == 128)
            fun unpackString(bytes: ByteArray) = String(bytes, 0, bytes.indexOf(0))
            val clientUsername = unpackString(decrypted.copyOfRange(0, 64))
            val clientPassword = unpackString(decrypted.copyOfRange(64, 128))

            return username == clientUsername && password == clientPassword
        }

        /**
         * Removes the initial sign byte if present
         */
        private fun toByteArrayWithoutSignByte(bi: BigInteger): ByteArray {
            val bytes = bi.toByteArray()
            if (bytes.first() != 0.toByte())
                return bytes

            return bytes.copyOfRange(1, bytes.size)
        }

        companion object {
            // 2048-bit group from RFC 3526
            val PRIME = BigInteger("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                                   "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                                   "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                                   "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                                   "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                                   "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                                   "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                                   "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                                   "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                                   "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                                   "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16)
            val GEN = BigInteger("2")
        }
    }
}