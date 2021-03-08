/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

/**
 * A tiny VNC server.
 *
 * Some tests (mainly for VncActivity) require a VNC server to connect to.
 * This server allow us to run these tests without requiring an external server.
 *
 * It also enables us to completely control the behaviour of the server,
 * so we can simulate different scenarios, error conditions etc.
 */
class TestServer {

    //Protocol config
    private val protocol = "RFB 003.008\n"
    private val serverName = "Friends"
    private val securityTypes = byteArrayOf(1, 2, 18, 19)
    private val securityFailReason = "We should take a break!"
    private val frameWidth: Short = 10
    private val frameHeight: Short = 10
    private val frameBuffer = ByteArray(frameWidth * frameHeight * 4)

    //Server config
    private val ss = ServerSocket(0)
    private val stopLatch = CountDownLatch(1)
    val host = ss.inetAddress.hostAddress!!
    val port = ss.localPort
    var receivedKeySyms = arrayListOf<Int>()


    fun start() {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                theServer()
            }
            stopLatch.countDown()
            ss.close()
        }
    }

    fun awaitStop() {
        stopLatch.await()
    }


    /**
     * Behold The Server
     */
    private fun theServer() {
        val socket = ss.accept()
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        //Protocol Handshake
        output.write(protocol.toByteArray())
        input.skip(12)

        //Security Handshake
        output.write(securityTypes.size)
        output.write(securityTypes)
        when (input.read()) { //Security Type
            // NoAuth
            1 -> output.write(ByteArray(4))   //Security result

            //VncAuth
            2 -> {
                output.write(ByteArray(16))   //Challenge
                input.skip(16)                  //Response
                output.write(ByteArray(4))    //Security result
            }

            else -> {
                output.write(toByteArray(securityFailReason.length))
                output.write(securityFailReason.toByteArray())
                socket.close()
                return
            }
        }

        // Init
        input.skip(1)  // Client Init
        output.write(toByteArray(frameWidth))
        output.write(toByteArray(frameHeight))
        output.write(ByteArray(16))  //Pixel format
        output.write(toByteArray(serverName.length))
        output.write(serverName.toByteArray())

        // Msg Loop
        while (true) {
            when (input.read()) {

                3 -> { //FramebufferUpdateRequest

                    val incremental = (input.read() == 1)
                    input.skip(8)    //Discard rest of the msg as we always send the whole framebuffer
                    if (incremental)   //Nothing to send in incremental updates
                        continue

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
                    if (isDown)
                        receivedKeySyms.add(key)
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
                    input.skip(length.toLong())
                }

                -1 -> return //EOF
            }
        }
    }

    private fun toByteArray(i: Int): ByteArray {
        return ByteBuffer.allocate(4).putInt(i).order(ByteOrder.BIG_ENDIAN).array()
    }

    private fun toByteArray(s: Short): ByteArray {
        return ByteBuffer.allocate(2).putShort(s).order(ByteOrder.BIG_ENDIAN).array()
    }

    private fun readInt(input: InputStream) =
            input.read().shl(24) +
            input.read().shl(16) +
            input.read().shl(8) +
            input.read()
}