/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui

import com.gaurav.avnc.ui.vnc.FrameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStateTest {

    @Test
    fun scalingCoerceTest() {
        val minMaxScale = arrayOf(Pair(1F, 1F), Pair(0F, 1F), Pair(1F, 2F), Pair(0F, 5F))

        for (limit in minMaxScale) {
            for (scaleFactor in arrayOf(0F, 0.5F, 1F, 1.5F, 2F, 2.5F, 5F, 10F)) {

                val state = FrameState(limit.first, limit.second)

                state.updateZoom(scaleFactor)

                assertTrue(state.zoomScale >= limit.first)
                assertTrue(state.zoomScale <= limit.second)
            }
        }
    }

    @Test
    fun baseScaleTest1() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)  //Same as viewport
        assertEquals(1F, state.baseScale)
    }


    @Test
    fun baseScaleTest2() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(50f, 100f) //Width is half, But height is same
        assertEquals(1F, state.baseScale)
    }

    @Test
    fun baseScaleTest3() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(50f, 50f) //Half size
        assertEquals(2F, state.baseScale)
    }

    @Test
    fun baseScaleTest4() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(200f, 200f) //Double size
        assertEquals(.5F, state.baseScale)
    }

    @Test
    fun baseScaleTest5() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        state.setViewportSize(50f, 50f)       //If viewport size is reduced
        assertEquals(.5F, state.baseScale) //base scale should also reduce
    }


    @Test
    fun baseScaleTest6() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        state.setViewportSize(200f, 200f)    //If viewport size is increased
        assertEquals(2F, state.baseScale) //base scale should also increase
    }

    @Test
    fun translateCoerceTest1() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        //Because frame and viewport are of same size, we should not be able to translate
        state.pan(10f, 10f)
        assertEquals(0f, state.frameX)
        assertEquals(0f, state.frameY)
    }


    @Test
    fun translateCoerceTest2() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)
        state.updateZoom(.5F)

        //Because frame is half in size, we should be centered
        assertEquals(25f, state.frameX)
        assertEquals(25f, state.frameY)
    }


    @Test
    fun translateCoerceTest3() {
        val state = FrameState()
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        //Double size
        state.updateZoom(2F)

        //Should not be able to move too much right/down
        state.pan(150f, 150f)
        assertEquals(0f, state.frameX) //Left side of frame and Viewport are aligned
        assertEquals(0f, state.frameY) //Top side of frame and Viewport are aligned


        //Should not be able to move too much left/up
        state.pan(-150f, -150f)
        assertEquals(-100f, state.frameX) //Right side of frame and Viewport are aligned
        assertEquals(-100f, state.frameY) //Bottom side of frame and Viewport are aligned
    }
}