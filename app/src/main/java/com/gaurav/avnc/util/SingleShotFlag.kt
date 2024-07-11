/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.util

import java.util.concurrent.CountDownLatch

/**
 * Thin wrapper around [CountDownLatch] with count = 1.
 */
class SingleShotFlag {
    private val latch = CountDownLatch(1)

    val isSet get() = latch.count == 0L
    val isNotSet get() = !isSet
    fun set() = latch.countDown()
    fun await() = runCatching { latch.await() }
}