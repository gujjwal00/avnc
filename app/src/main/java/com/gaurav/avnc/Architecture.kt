/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.ui.about.AboutActivity
import com.gaurav.avnc.ui.home.HomeActivity
import com.gaurav.avnc.ui.home.ServerTabs
import com.gaurav.avnc.ui.home.UrlBarActivity
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.ui.vnc.input.Dispatcher
import com.gaurav.avnc.ui.vnc.FrameState
import com.gaurav.avnc.ui.vnc.FrameView
import com.gaurav.avnc.ui.vnc.ConfirmationDialog
import com.gaurav.avnc.ui.vnc.LoginFragment
import com.gaurav.avnc.ui.vnc.VirtualKeys
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.viewmodel.service.Discovery
import com.gaurav.avnc.viewmodel.service.SshTunnel
import com.gaurav.avnc.vnc.Messenger
import com.gaurav.avnc.vnc.VncClient

/**
 * This is a brain dump of the design & architecture of this app.
 *
 *
 * Overview
 * ========
 *
 * There are three main layers:
 *
 *- +---------------------------------------------------------------------------------------------------------------+
 *-  UI
 *-
 *-                 +------------------+      +------------------+      +------------------+     +------------------+
 *-                 |  [HomeActivity]  |      |  [VncActivity]   |      |  [PrefsActivity] |     |  [AboutActivity] |
 *-                 +--------+---------+      +--------+---------+      +--------+---------+     +------------------+
 *-                          |                         |                         |
 *-                          |                         |                         |
 *- +------------------------|-------------------------|-------------------------|----------------------------------+
 *-  ViewModel               |                         |                         |
 *-                          v                         v                         v
 *-                 +------------------+      +------------------+      +------------------+
 *-                 |  [HomeViewModel] |      |  [VncViewModel]  |      | [PrefsViewModel] |
 *-                 +------------------+      +------------------+      +------------------+
 *-                          A                         A                                         +--------------+
 *-                          |                         |                                         |   Services   |
 *-                          |                         |                                         +--------------+
 *- +------------------------|-------------------------|------------------------------------------------------------+
 *-  Model & Client          |                         |
 *-                          V                         V
 *-                 +------------------+      +------------------+
 *-                 |  [ServerProfile] |      |   [VncClient]    |
 *-                 |                  |      |                  |
 *-                 |     Database     |      |   LibVNCClient   |
 *-                 +------------------+      +------------------+
 *-
 *- +---------------------------------------------------------------------------------------------------------------+
 *
 *
 * Home
 * ====
 *
 * [HomeActivity] is the main activity of the app. Components:
 *
 * - A urlbar, which launches [UrlBarActivity], allowing user to quickly connect
 *   to a server without creating a profile for it.
 *
 * - Lists of saved & discovered servers, in [ServerTabs].
 *
 * - Profile editors used for creating/editing [ServerProfile].
 *
 *
 * VNC UI
 * ======
 *
 * [VncActivity] is responsible for driving the connection to VNC server.
 *
 * - [FrameView] renders the VNC framebuffer on screen.
 * - [FrameState] maintains information related to framebuffer rendering,
 *   like zoom, pan etc.
 *
 * - See [Dispatcher] for overview of input handling.
 * - [VirtualKeys] are used for keys not normally found on Android Keyboards.
 *
 *
 * VNC Connection
 * ==============
 *
 * - Connection to VNC server is managed by [VncViewModel], using [VncClient].
 * - [VncClient] is a wrapper around native `rfbClient` from LibVNCClient.
 *
 * - [LoginFragment] is used to ask username & password from user.
 * - [SshTunnel] is used to create a SSH tunnel, which can be used for connection.
 * - [ConfirmationDialog] is used to verify unknown SSH hosts and X509 certs with user.
 * -
 * - [Messenger] is used to send events to VNC server.
 *
 *
 * Database
 * ========
 *
 * We use a Room Database, [MainDb], to save list of servers.
 * Servers are modeled by the [ServerProfile] entity.
 *
 *
 * Services
 * ========
 *
 * These are sort-of standalone components which perform a particular task:
 *
 * - Server discovery ([Discovery])
 * - SSH Tunnel ([SshTunnel])
 * - Import/Export (in [PrefsViewModel])
 *
 * Note: These are NOT Android Services (these are called services for lack of a better word).
 */
private fun avnc() {
}