/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.graphics.RectF
import com.gaurav.avnc.vnc.CursorInfo

/**
 * This class manages the cursor when it is rendered on client-side.
 *
 * Cursor is drawn on top of [Frame].
 */
class Cursor : Image() {

    /**
     * Updates position/geometry of the cursor
     */
    fun update(x: Float, y: Float, ci: CursorInfo, frame: Frame) {
        val width = ci.width
        val height = ci.height
        val frameRect = frame.getFrameRect()
        val cursorRect = RectF(x, y, x + width, y + height)

        // This is the rectangle where cursor would normally be drawn
        val normalDrawRect = RectF(cursorRect).apply { offset(-ci.xHot.toFloat(), -ci.yHot.toFloat()) }

        // But if it moves near the edges, some part of the cursor can go outside the frame
        // So it needs to be clipped to be inside the frame.
        val clippedDrawRect = RectF(normalDrawRect).apply { intersect(frameRect) }

        // Texture rectangle has to account for clipped portions
        val clippedTextureRect = RectF(
                (clippedDrawRect.left - normalDrawRect.left) / width,
                (clippedDrawRect.top - normalDrawRect.top) / height,
                1 - (normalDrawRect.right - clippedDrawRect.right) / width,
                1 - (normalDrawRect.bottom - clippedDrawRect.bottom) / height,
        )

        updateDrawRect(clippedDrawRect)
        updateTextureRect(clippedTextureRect)
    }
}