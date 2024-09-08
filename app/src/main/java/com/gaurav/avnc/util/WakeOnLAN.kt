/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer

private const val BROADCAST_PORT = 9

/**
 * Parses given MAC address.
 * Throws an exception if it is not a valid MAC address.
 */
@OptIn(ExperimentalStdlibApi::class)
fun parseMacAddress(macAddress: String): ByteArray {
    return macAddress.hexToByteArray(HexFormat { bytes.byteSeparator = ":" }).also { check(it.size == 6) }
}

/**
 * Broadcasts Wake-on-LAN magic packet for [macAddress] to all available networks.
 * Cannot be called from main thread.
 */
fun broadcastWoLPackets(macAddress: String) {
    val macBytes = parseMacAddress(macAddress)
    val addresses = getBroadcastAddresses()

    check(addresses.isNotEmpty()) { "No network interface is active" }
    addresses.forEach { addr ->
        val socket = DatagramSocket().apply { broadcast = true }
        val packet = createMagicPacket(macBytes, addr)

        socket.use { it.send(packet) }
    }
}

/**
 * Returns list of all broadcast addresses available on this system.
 */
private fun getBroadcastAddresses(): List<InetAddress> {
    return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .map { it.interfaceAddresses }
            .fold(mutableListOf<InterfaceAddress>()) { list, addresses -> list.apply { addAll(addresses) } }
            .mapNotNull { it.broadcast }
}


/**
 * Creates WoL magic packet targeted at given broadcast address.
 */
private fun createMagicPacket(macBytes: ByteArray, broadcastAddress: InetAddress): DatagramPacket {
    check(macBytes.size == 6)
    val payload = ByteBuffer.allocate(6 + (16 * 6)).apply {
        repeat(6) { put(255.toByte()) }
        repeat(16) { put(macBytes) }
    }
    return DatagramPacket(payload.array(), payload.array().size, broadcastAddress, BROADCAST_PORT)
}
