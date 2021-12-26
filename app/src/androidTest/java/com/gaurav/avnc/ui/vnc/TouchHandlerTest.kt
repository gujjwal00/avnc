/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import io.mockk.*
import org.junit.Before
import org.junit.Test

@SdkSuppress(minSdkVersion = 28)
class TouchHandlerTest {

    /************************* Setup  ************************************************************/

    private lateinit var touchHandler: TouchHandler
    private lateinit var mockDispatcher: Dispatcher
    private val testPoint = PointF(10f, 10f)

    @Before
    fun setup() {
        instrumentation.runOnMainSync {
            mockDispatcher = mockk(relaxed = true)
            touchHandler = TouchHandler(VncViewModel(ApplicationProvider.getApplicationContext()), mockDispatcher)
        }
    }

    private fun setupWithPref(mousePassthrough: Boolean = false, dragEnabled: Boolean = false) {
        instrumentation.runOnMainSync {
            val viewModel = spyk(VncViewModel(ApplicationProvider.getApplicationContext()))

            // Both of these needs to be mocked
            every { viewModel.pref.input.mousePassthrough } returns mousePassthrough
            every { viewModel.pref.input.gesture.dragEnabled } returns dragEnabled
            touchHandler = TouchHandler(viewModel, mockDispatcher)
        }
    }


    /************************ Event Generation ***************************************************/

    private fun now() = SystemClock.uptimeMillis()

    private fun createEvent(action: Int, p: PointF): MotionEvent {
        return MotionEvent.obtain(now(), now(), action, p.x, p.y, 0)
    }

    private fun createEvent2(action: Int, p1: PointF, p2: PointF): MotionEvent {
        val props = arrayOf(PointerProperties().apply { id = 0 }, PointerProperties().apply { id = 1 })
        val coord = arrayOf(PointerCoords().apply { x = p1.x; y = p1.y }, PointerCoords().apply { x = p2.x; y = p2.y })
        return MotionEvent.obtain(now(), now(), action, 2, props, coord, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private fun createMouseEvent(action: Int, p: PointF, button: Int? = null): MotionEvent {
        val event = spyk(createEvent(action, p))
        event.source = InputDevice.SOURCE_MOUSE
        event.action = action
        event.setLocation(p.x, p.y)
        every { event.getToolType(any()) } returns MotionEvent.TOOL_TYPE_MOUSE
        button?.let { every { event.actionButton } returns it }
        return event
    }

    private fun createStylusEvent(action: Int, p: PointF): MotionEvent {
        val event = createMouseEvent(action, p)
        event.source = InputDevice.SOURCE_STYLUS
        every { event.getToolType(any()) } returns MotionEvent.TOOL_TYPE_STYLUS
        return event
    }

    private fun sendEvent(event: MotionEvent) = instrumentation.runOnMainSync {
        touchHandler.onTouchEvent(event)
    }

    private fun sendGenericEvent(event: MotionEvent) = instrumentation.runOnMainSync {
        touchHandler.onGenericMotionEvent(event)
    }

    private fun sendHoverEvent(event: MotionEvent) = instrumentation.runOnMainSync {
        touchHandler.onHoverEvent(event)
    }


    /************************* Gesture Tests *******************************************************/

    private val timeoutGrace = 100L
    private fun sendDown(p: PointF = testPoint) = sendEvent(createEvent(MotionEvent.ACTION_DOWN, p))
    private fun sendUp(p: PointF = testPoint) = sendEvent(createEvent(MotionEvent.ACTION_UP, p))
    private fun sendMove(p: PointF) = sendEvent(createEvent(MotionEvent.ACTION_MOVE, p))

    @Test
    fun singleTap() {
        sendDown()
        sendUp()
        Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + timeoutGrace)
        verify { mockDispatcher.onTap1(testPoint) }
    }

    @Test
    fun doubleTap() {
        sendDown()
        sendUp()
        Thread.sleep(50) //Required due to the minimum time limit in GestureDetector
        sendDown()
        sendUp()
        verify { mockDispatcher.onDoubleTap(testPoint) }
    }

    @Test
    fun twoFingerTap() {
        sendDown()
        sendEvent(createEvent2(MotionEvent.ACTION_POINTER_DOWN, testPoint, PointF(50f, 50f)))
        sendEvent(createEvent2(MotionEvent.ACTION_POINTER_UP, testPoint, PointF(50f, 50f)))
        sendUp()
        verify { mockDispatcher.onTap2(testPoint) }
    }

    @Test
    fun longPress() {
        setupWithPref(dragEnabled = false)
        sendDown()
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + timeoutGrace)
        verify { mockDispatcher.onLongPress(testPoint) }
    }

    @Test
    fun swipe1Finger() {
        val a1 = PointF(100f, 100f)
        val a2 = PointF(200f, 200f)
        sendDown(a1)
        sendMove(a2)
        verify { mockDispatcher.onSwipe1(a1, a2, 100f, 100f) }
    }

    @Test
    fun swipe2Finger() {
        val a1 = PointF(100f, 100f)
        val a2 = PointF(200f, 200f)
        val b1 = PointF(300f, 300f)
        val b2 = PointF(400f, 400f)

        sendDown(a1)
        sendEvent(createEvent2(MotionEvent.ACTION_POINTER_DOWN, a1, b1))
        sendEvent(createEvent2(MotionEvent.ACTION_MOVE, a2, b2))
        verify { mockDispatcher.onSwipe2(a1, a2, 100f, 100f) }
    }

    @Test
    fun scale() {
        val a1 = PointF(100f, 100f)
        val a2 = PointF(10f, 10f)
        val b1 = PointF(200f, 200f)
        val b2 = PointF(300f, 300f)

        sendDown(a1)
        sendEvent(createEvent2(MotionEvent.ACTION_POINTER_DOWN, a1, b1))
        sendEvent(createEvent2(MotionEvent.ACTION_MOVE, a2, b2))
        verify { mockDispatcher.onScale(any(), any(), any()) }
    }

    @Test
    fun drag() {
        setupWithPref(dragEnabled = true)

        val a1 = PointF(100f, 100f)
        val a2 = PointF(150f, 150f)

        sendDown(a1)
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + timeoutGrace)
        sendMove(a2)
        sendUp(a2)

        verifyOrder {
            mockDispatcher.onDrag(a1, a2, 50f, 50f)
            mockDispatcher.onGestureStop(a2)
        }
    }

    @Test
    fun gestureStartStop() {
        sendDown()
        sendUp()
        verifyOrder {
            mockDispatcher.onGestureStart(testPoint)
            mockDispatcher.onGestureStop(testPoint)
        }
    }

    @Test
    fun gestureStartStopOnCancel() {
        sendDown()
        sendEvent(createEvent(MotionEvent.ACTION_CANCEL, testPoint))
        verifyOrder {
            mockDispatcher.onGestureStart(testPoint)
            mockDispatcher.onGestureStop(testPoint)
        }
    }

    /************************* Mouse Tests *******************************************************/

    @Test
    fun mouseLeftClick() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_PRESS, testPoint, MotionEvent.BUTTON_PRIMARY))
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_RELEASE, testPoint, MotionEvent.BUTTON_PRIMARY))
        verifyOrder {
            mockDispatcher.onMouseButtonDown(PointerButton.Left, testPoint)
            mockDispatcher.onMouseButtonUp(PointerButton.Left, testPoint)
        }
    }

    @Test
    fun mouseRightClick() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_PRESS, testPoint, MotionEvent.BUTTON_SECONDARY))
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_BUTTON_RELEASE, testPoint, MotionEvent.BUTTON_SECONDARY))
        verifyOrder {
            mockDispatcher.onMouseButtonDown(PointerButton.Right, testPoint)
            mockDispatcher.onMouseButtonUp(PointerButton.Right, testPoint)
        }
    }

    @Test
    fun mouseMove() {
        setupWithPref(mousePassthrough = true)
        sendEvent(createMouseEvent(MotionEvent.ACTION_MOVE, testPoint))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    @Test
    fun mouseScroll() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_SCROLL, testPoint))
        verify { mockDispatcher.onMouseScroll(testPoint, any(), any()) }
    }

    @Test
    fun mouseHover1() {
        setupWithPref(mousePassthrough = true)
        sendHoverEvent(createMouseEvent(MotionEvent.ACTION_HOVER_MOVE, testPoint))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    // We should be able to handle hover events sent as generic events.
    @Test
    fun mouseHover2() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(createMouseEvent(MotionEvent.ACTION_HOVER_MOVE, testPoint))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }


    /************************* Stylus Tests ******************************************************/

    private fun sendStylusDown(p: PointF = testPoint) = sendEvent(createStylusEvent(MotionEvent.ACTION_DOWN, p))
    private fun sendStylusUp(p: PointF = testPoint) = sendEvent(createStylusEvent(MotionEvent.ACTION_UP, p))
    private fun sendStylusMove(p: PointF) = sendEvent(createStylusEvent(MotionEvent.ACTION_MOVE, p))

    @Test
    fun stylusTap() {
        sendStylusDown()
        sendStylusUp()
        Thread.sleep(ViewConfiguration.getDoubleTapTimeout() + timeoutGrace)
        verify { mockDispatcher.onStylusTap(testPoint) }
    }

    @Test
    fun stylusDoubleTap() {
        sendStylusDown()
        sendStylusUp()
        Thread.sleep(50) //Required due to the minimum time limit in GestureDetector
        sendStylusDown()
        sendStylusUp()
        verify { mockDispatcher.onStylusDoubleTap(testPoint) }
    }

    @Test
    fun stylusLongPress() {
        sendStylusDown()
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + timeoutGrace)
        verify { mockDispatcher.onStylusLongPress(testPoint) }
    }

    @Test
    fun stylusSwipe() {
        val a1 = PointF(100f, 100f)
        val a2 = PointF(200f, 200f)
        sendStylusDown(a1)
        sendStylusMove(a2)
        verify { mockDispatcher.onStylusScroll(a2) }
    }

    @Test
    fun stylusHover1() {
        sendHoverEvent(createStylusEvent(MotionEvent.ACTION_HOVER_MOVE, testPoint))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    @Test
    fun stylusHover2() {
        sendGenericEvent(createStylusEvent(MotionEvent.ACTION_HOVER_MOVE, testPoint))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }
}