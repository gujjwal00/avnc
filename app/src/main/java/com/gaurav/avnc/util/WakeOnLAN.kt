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

/**
 * Parses given MAC address.
 * Throws an exception if it is not a valid MAC address.
 */
@OptIn(ExperimentalStdlibApi::class)
fun parseMacAddress(macAddress: String): ByteArray {
    return macAddress.hexToByteArray(HexFormat { bytes.byteSeparator = ":" }).also { check(it.size == 6) }
}

/**
 * Coverts to [InetAddress].
 */
fun parseBroadcastAddress(address: String): InetAddress {
    try {
        return InetAddress.getByName(address)
    } catch (t: Throwable) {
        throw IllegalArgumentException("Invalid broadcast address", t)
    }
}


/**
 * Broadcasts Wake-on-LAN magic packet for [macAddress] to all available networks.
 * Cannot be called from main thread.
 */
fun broadcastWoLPackets(macAddress: String, broadcastAddress: String, port: Int) {
    val macBytes = parseMacAddress(macAddress)
    val addresses = mutableSetOf<InetAddress>()

    if (broadcastAddress.isNotBlank())
        addresses += parseBroadcastAddress(broadcastAddress)

    if (broadcastAddress.isBlank() || broadcastAddress == "255.255.255.255")
        addresses += getSystemBroadcastAddresses()

    check(addresses.isNotEmpty()) { "No broadcast address available" }
    addresses.forEach { addr ->
        val socket = DatagramSocket().apply { broadcast = true }
        val packet = createMagicPacket(macBytes, addr, port)

        socket.use { it.send(packet) }
    }
}

/**
 * Returns list of all broadcast addresses available on this system.
 */
private fun getSystemBroadcastAddresses(): List<InetAddress> {
    return NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .map { it.interfaceAddresses }
            .fold(mutableListOf<InterfaceAddress>()) { list, addresses -> list.apply { addAll(addresses) } }
            .mapNotNull { it.broadcast }
}


/**
 * Creates WoL magic packet targeted at given broadcast address.
 */
private fun createMagicPacket(macBytes: ByteArray, broadcastAddress: InetAddress, port: Int): DatagramPacket {
    check(macBytes.size == 6)
    val payload = ByteBuffer.allocate(6 + (16 * 6)).apply {
        repeat(6) { put(255.toByte()) }
        repeat(16) { put(macBytes) }
    }
    return DatagramPacket(payload.array(), payload.array().size, broadcastAddress, port)
}
