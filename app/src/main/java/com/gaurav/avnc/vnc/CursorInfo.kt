/*
 * Copyright (c) 2025  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.vnc


/**
 * Used for storing cursor info received from native side.
 * Default values here matches with the default cursor in native side.
 */
data class CursorInfo(
        var width: Int = 10,
        var height: Int = 16,
        var xHot: Int = 1,
        var yHot: Int = 1,
)
