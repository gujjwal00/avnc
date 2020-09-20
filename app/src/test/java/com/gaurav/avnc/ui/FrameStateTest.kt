/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui

import com.gaurav.avnc.ui.vnc.FrameState
import com.gaurav.avnc.util.AppPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStateTest {

    private fun getPrefMock(minZoom: Float = .5F, maxZoom: Float = 5F): AppPreferences {
        return mockk {
            every { zoom } returns
                    mockk {
                        every { min } returns minZoom
                        every { max } returns maxZoom
                    }
        }
    }

    @Test
    fun scalingCoerceTest() {
        val minMaxScale = arrayOf(Pair(1F, 1F), Pair(0F, 1F), Pair(1F, 2F), Pair(0F, 5F))

        for (limit in minMaxScale) {
            for (scaleFactor in arrayOf(0F, 0.5F, 1F, 1.5F, 2F, 2.5F, 5F, 10F)) {

                val prefMock = getPrefMock(limit.first, limit.second)
                val state = FrameState(prefMock)

                state.updateZoom(scaleFactor)

                assertTrue(state.zoomScale >= limit.first)
                assertTrue(state.zoomScale <= limit.second)
            }
        }
    }

    @Test
    fun baseScaleTest1() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)  //Same as viewport
        assertEquals(1F, state.baseScale)
    }


    @Test
    fun baseScaleTest2() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(50f, 100f) //Width is half, But height is same
        assertEquals(1F, state.baseScale)
    }

    @Test
    fun baseScaleTest3() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(50f, 50f) //Half size
        assertEquals(2F, state.baseScale)
    }

    @Test
    fun baseScaleTest4() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(200f, 200f) //Double size
        assertEquals(.5F, state.baseScale)
    }

    @Test
    fun translateCoerceTest1() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        //Because frame and viewport are of same size, we should not be able to translate
        state.pan(10f, 10f)
        assertEquals(0f, state.translateX)
        assertEquals(0f, state.translateY)
    }


    @Test
    fun translateCoerceTest2() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)
        state.updateZoom(.5F)

        //Because frame is half in size, we should be centered
        assertEquals(25f, state.translateX)
        assertEquals(25f, state.translateY)
    }


    @Test
    fun translateCoerceTest3() {
        val state = FrameState(getPrefMock())
        state.setViewportSize(100f, 100f)
        state.setFramebufferSize(100f, 100f)

        //Double size
        state.updateZoom(2F)

        //Should not be able to move too much right/down
        state.pan(150f, 150f)
        assertEquals(0f, state.translateX) //Left side of framebuffer and Viewport are aligned
        assertEquals(0f, state.translateY) //Top side of framebuffer and Viewport are aligned


        //Should not be able to move too much left/up
        state.pan(-150f, -150f)
        assertEquals(-100f, state.translateX) //Right side of framebuffer and Viewport are aligned
        assertEquals(-100f, state.translateY) //Bottom side of framebuffer and Viewport are aligned
    }
}