/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.ui.vnc.createVncIntent
import org.junit.Before

/**
 * Helper class to test a VNC session using [TestServer]
 */
class VncSessionScenario {
    val server = TestServer()
    val profile = ServerProfile(host = server.host, port = server.port)
    var activityScenario: ActivityScenario<VncActivity>? = null


    fun startServer() = apply {
        server.start()
    }

    fun startActivity(suppressHelp: Boolean = true) = apply {
        if (suppressHelp)
            suppressViewerHelp()
        activityScenario = ActivityScenario.launch(createVncIntent(targetContext, profile))
    }

    fun assertConnected() = apply {
        onView(withId(R.id.frame_view)).checkWillBeDisplayed()
        pollingAssert { onView(withId(R.id.status_container)).checkIsNotDisplayed() }
    }

    fun start() = apply {
        startServer()
        startActivity()
        assertConnected()
    }

    fun stop() = apply {
        activityScenario?.close()
        activityScenario = null
        server.stop()
    }

    fun run(tests: () -> Unit) = apply {
        start()
        try {
            tests()
        } finally {
            stop()
        }
    }

    private fun suppressViewerHelp() {
        targetPrefs.edit { putBoolean("run_info_has_shown_viewer_help", true) }
    }
}

/**
 * Base class for tests targeted against a VNC session
 */
open class VncSessionTest {
    lateinit var vncSession: VncSessionScenario

    @Before
    fun createVncSession() {
        vncSession = VncSessionScenario()
    }
}
