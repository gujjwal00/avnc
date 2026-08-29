/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import com.gaurav.avnc.model.QrProfileImporter
import com.gaurav.avnc.model.ServerProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrProfileImporterTest {

    private fun fakeDao(existing: List<ServerProfile> = emptyList()): Pair<ServerProfileDao, CapturingSlot<ServerProfile>> {
        val dao = mockk<ServerProfileDao>()
        val slot = slot<ServerProfile>()
        coEvery { dao.save(capture(slot)) } returns 0L
        coEvery { dao.getByName(any()) } returns existing
        return dao to slot
    }

    @Test
    fun validPayloadMapsCorrectly() = runBlocking {
        val json = """{
            "type":"avnc_server_profile",
            "name":"Home PC",
            "host":"192.168.1.5",
            "port":5901,
            "username":"user",
            "viewOnly":true,
            "colorDepth":"COLOR_16BIT",
            "sshTunnel":{"enabled":true,"host":"ssh.example.com","port":2222,"username":"sshuser"}
        }"""
        val (dao, slot) = fakeDao()

        val result = QrProfileImporter.importFromRawJson(json, dao)

        assertTrue(result.isSuccess)
        val p = slot.captured
        assertEquals("Home PC", p.name)
        assertEquals("192.168.1.5", p.host)
        assertEquals(5901, p.port)
        assertEquals("user", p.username)
        assertEquals(ServerProfile.VIEW_MODE_NO_INPUT, p.viewMode)
        assertEquals(ServerProfile.CHANNEL_SSH_TUNNEL, p.channelType)
        assertEquals(4, p.colorLevel)
        assertEquals("ssh.example.com", p.sshHost)
        assertEquals(2222, p.sshPort)
        assertEquals("sshuser", p.sshUsername)
    }

    @Test
    fun defaultsAreApplied() = runBlocking {
        val json = """{"type":"avnc_server_profile","name":"Min","host":"h"}"""
        val (dao, slot) = fakeDao()

        val result = QrProfileImporter.importFromRawJson(json, dao)

        assertTrue(result.isSuccess)
        val p = slot.captured
        assertEquals(5900, p.port)
        assertEquals(ServerProfile.VIEW_MODE_NORMAL, p.viewMode)
        assertEquals(ServerProfile.CHANNEL_TCP, p.channelType)
        assertEquals(7, p.colorLevel)
    }

    @Test
    fun unknownTypeFails() = runBlocking {
        val json = """{"type":"something_else","name":"x","host":"h"}"""
        val (dao, _) = fakeDao()
        val result = QrProfileImporter.importFromRawJson(json, dao)
        assertFalse(result.isSuccess)
    }

    @Test
    fun corruptJsonFails() = runBlocking {
        val (dao, _) = fakeDao()
        val result = QrProfileImporter.importFromRawJson("{not valid json", dao)
        assertFalse(result.isSuccess)
    }

    @Test
    fun blankHostFails() = runBlocking {
        val json = """{"type":"avnc_server_profile","name":"x","host":""}"""
        val (dao, _) = fakeDao()
        assertFalse(QrProfileImporter.importFromRawJson(json, dao).isSuccess)
    }

    @Test
    fun invalidPortFails() = runBlocking {
        val json = """{"type":"avnc_server_profile","name":"x","host":"h","port":70000}"""
        val (dao, _) = fakeDao()
        assertFalse(QrProfileImporter.importFromRawJson(json, dao).isSuccess)
    }

    @Test
    fun sshEnabledWithoutHostFails() = runBlocking {
        val json = """{"type":"avnc_server_profile","name":"x","host":"h","sshTunnel":{"enabled":true,"host":""}}"""
        val (dao, _) = fakeDao()
        assertFalse(QrProfileImporter.importFromRawJson(json, dao).isSuccess)
    }

    @Test
    fun duplicateUpdatesExisting() = runBlocking {
        val existing = ServerProfile(name = "Home", host = "h", port = 5900, ID = 5)
        val json = """{"type":"avnc_server_profile","name":"Home","host":"h","port":5900}"""
        val (dao, slot) = fakeDao(listOf(existing))
        val result = QrProfileImporter.importFromRawJson(json, dao)
        assertTrue(result.isSuccess)
        assertEquals(5, slot.captured.ID)
    }
}
