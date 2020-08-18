/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc

/**
 * This class is used for returning user credentials from callbacks.
 */
data class UserCredential(
        @JvmField
        var username: String = "",

        @JvmField
        var password: String = ""
)