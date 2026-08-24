/*
 * Copyright (c) 2026 Gaurav Ujjwal.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.ui.vnc.foldable

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Provides only the posture needed by Workspace Mode; vertical/book posture remains unchanged. */
class FoldStateProvider(activity: Activity) {
    private val tracker = WindowInfoTracker.getOrCreate(activity)

    val isHorizontalTabletop: Flow<Boolean> = tracker.windowLayoutInfo(activity).map { info: WindowLayoutInfo ->
        info.displayFeatures.filterIsInstance<FoldingFeature>().any {
            it.state == FoldingFeature.State.HALF_OPENED &&
                it.orientation == FoldingFeature.Orientation.HORIZONTAL
        }
    }
}
