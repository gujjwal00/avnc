/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.gl

import android.graphics.RectF

/**
 * This class manges rendering of the framebuffer.
 */
class Frame : Image() {

    private val frameRect = RectF()

    init {
        // Texture rectangle is fixed and covers the entire texture
        updateTextureRect(RectF(0f, 0f, 1f, 1f))
    }

    fun getFrameRect() = RectF(frameRect)

    /**
     * Should be called whenever the size of framebuffer is changed.
     * This size will be used to calculate frame vertices.
     */
    fun updateFbSize(width: Float, height: Float) {
        if (frameRect.width() == width && frameRect.height() == height)
            return //Nothing to do

        frameRect.set(0f, 0f, width, height)
        updateDrawRect(frameRect)
    }
}