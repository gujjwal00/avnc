/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.model

/**
 * Generic wrapper for login information.
 * This can be used to hold different [Type]s of credentials.
 */
data class LoginInfo(
        val type: Type,
        var username: String,
        var password: String,
) {
    enum class Type {
        VNC_PASSWORD,
        VNC_CREDENTIAL,  // Username & Password
        SSH_PASSWORD,
        SSH_KEY_PASSWORD
    }

    companion object {
        /**
         * Extracts given login [type] from [profile]
         */
        fun fromProfile(profile: ServerProfile, type: Type): LoginInfo {
            return when (type) {
                Type.VNC_PASSWORD -> LoginInfo(type, "", profile.password)
                Type.VNC_CREDENTIAL -> LoginInfo(type, profile.username, profile.password)
                Type.SSH_PASSWORD -> LoginInfo(type, "", profile.sshPassword)
                Type.SSH_KEY_PASSWORD -> LoginInfo(type, "", "") // Not present in profile
            }
        }
    }

    /**
     * Applies this login info to [profile]
     */
    fun applyTo(profile: ServerProfile) {
        when (type) {
            Type.VNC_PASSWORD -> {
                profile.password = password
            }
            Type.VNC_CREDENTIAL -> {
                profile.username = username
                profile.password = password
            }
            Type.SSH_PASSWORD -> {
                profile.sshPassword = password
            }
            Type.SSH_KEY_PASSWORD -> {}
        }
    }
}