/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

class VncClientTest {

    class DummyObserver : VncClient.Observer {
        override fun rfbGetPassword(): String = ""
        override fun rfbGetCredential(): UserCredential = UserCredential()
        override fun rfbBell() {}
        override fun rfbGotXCutText(text: String) {}
        override fun rfbFinishedFrameBufferUpdate() {}
        override fun rfbHandleCursorPos(x: Int, y: Int): Boolean = true
        override fun onClientInfoChanged(info: VncClient.Info) {}
    }
}