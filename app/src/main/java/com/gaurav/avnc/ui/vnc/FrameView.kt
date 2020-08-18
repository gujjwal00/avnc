/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class FrameView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {

    constructor(context: Context?) : this(context, null)
}