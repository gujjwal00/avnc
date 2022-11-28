/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.graphics.Point
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.ViewConfiguration
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.gaurav.avnc.instrumentation
import com.gaurav.avnc.targetPrefs
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.vnc.PointerButton
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

/**
 * Tests for [TouchHandler]
 *
 * Although actual tests are very simple here, event creation & injection is quite complex.
 * But given the importance of gestures in AVNC and the complexity of [TouchHandler],
 * these tests are very valuable.
 */
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

        // Internally, mocks seems to be lazily initialized, and the initialization can take some time.
        // This is problematic here because gesture detection is very sensitive to timing of events.
        // So we eagerly trigger the initialization, to avoid messing with timings in actual tests.
        mockDispatcher.onXKeySym(0, false)
    }


    private fun setupWithPref(mousePassthrough: Boolean = false, dragEnabled: Boolean = false) {
        targetPrefs.edit {
            putBoolean("mouse_passthrough", mousePassthrough)
            putString("gesture_drag", if (dragEnabled) "remote-scroll" else "none")
        }
        setup()
    }

    /************************* Finger Gestures *******************************************************/

    @Test
    fun singleTap() {
        sendDown()
        sendUp()
        Thread.sleep(Delay.SINGLE_TAP_CONFIRM)
        verify { mockDispatcher.onTap1(testPoint) }
    }

    @Test
    fun doubleTap() {
        sendDown()
        sendUp()
        Thread.sleep(Delay.BETWEEN_DOUBLE_TAPS)
        sendDown()
        sendUp()
        verify { mockDispatcher.onDoubleTap(testPoint) }
    }

    @Test
    fun twoFingerTap() {
        sendDown()
        sendEvent(Factory.obtainPointerDownEvent(downEvent, testPoint, PointF(50f, 50f)))
        sendEvent(Factory.obtainPointerUpEvent(downEvent, testPoint, PointF(50f, 50f)))
        sendUp()
        verify { mockDispatcher.onTap2(testPoint) }
    }

    @Test
    fun longPress() {
        setupWithPref(dragEnabled = false)
        sendDown()
        Thread.sleep(Delay.LONG_PRESS_CONFIRM)
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
        sendEvent(Factory.obtainPointerDownEvent(downEvent, a1, b1))
        sendEvent(Factory.obtainMoveEvent(downEvent, a2, b2))
        verify { mockDispatcher.onSwipe2(a1, a2, 100f, 100f) }
    }

    @Test
    fun scale() {
        val a1 = PointF(300f, 300f)
        val b1 = PointF(400f, 400f)

        sendDown(a1)
        sendEvent(Factory.obtainPointerDownEvent(downEvent, a1, b1))
        sendEvent(Factory.obtainMoveEvent(downEvent, PointF(200f, 200f), PointF(500f, 500f)))
        sendEvent(Factory.obtainMoveEvent(downEvent, PointF(100f, 100f), PointF(600f, 600f)))
        sendEvent(Factory.obtainMoveEvent(downEvent, PointF(10f, 10f), PointF(690f, 690f)))
        verify { mockDispatcher.onScale(any(), any(), any()) }
    }

    @Test
    fun drag() {
        setupWithPref(dragEnabled = true)

        val a1 = PointF(100f, 100f)
        val a2 = PointF(150f, 150f)

        sendDown(a1)
        Thread.sleep(Delay.LONG_PRESS_CONFIRM)
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
            mockDispatcher.onGestureStart()
            mockDispatcher.onGestureStop(testPoint)
        }
    }

    @Test
    fun gestureStartStopOnCancel() {
        sendDown()
        sendEvent(Factory.obtainCancelEvent(downEvent, testPoint))
        verifyOrder {
            mockDispatcher.onGestureStart()
            mockDispatcher.onGestureStop(testPoint)
        }
    }

    /************************* Mouse Tests *******************************************************/

    @Test
    fun mouseLeftClick() {
        setupWithPref(mousePassthrough = true)
        sendMouseButtonPress(MotionEvent.BUTTON_PRIMARY)
        sendMouseButtonRelease()
        verifyOrder {
            mockDispatcher.onMouseButtonDown(PointerButton.Left, testPoint)
            mockDispatcher.onMouseButtonUp(PointerButton.Left, testPoint)
        }
    }

    @Test
    fun mouseRightClick() {
        setupWithPref(mousePassthrough = true)
        sendMouseButtonPress(MotionEvent.BUTTON_SECONDARY)
        sendMouseButtonRelease()
        verifyOrder {
            mockDispatcher.onMouseButtonDown(PointerButton.Right, testPoint)
            mockDispatcher.onMouseButtonUp(PointerButton.Right, testPoint)
        }
    }

    @Test
    fun mouseMove() {
        setupWithPref(mousePassthrough = true)
        sendMouseButtonPress(MotionEvent.BUTTON_PRIMARY)
        sendMouseButtonMove(testPoint)
        sendMouseButtonRelease()
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    @Test
    fun mouseScroll() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(Factory.obtainScrollEvent(testPoint))
        verify { mockDispatcher.onMouseScroll(testPoint, any(), any()) }
    }

    @Test
    fun mouseHover1() {
        setupWithPref(mousePassthrough = true)
        sendHoverEvent(Factory.obtainHoverEvent(testPoint, InputDevice.SOURCE_MOUSE))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    // We should be able to handle hover events sent as generic events.
    @Test
    fun mouseHover2() {
        setupWithPref(mousePassthrough = true)
        sendGenericEvent(Factory.obtainHoverEvent(testPoint, InputDevice.SOURCE_MOUSE))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }


    /************************* Stylus Tests ******************************************************/

    @Test
    fun stylusTap() {
        sendStylusDown()
        sendStylusUp()
        Thread.sleep(Delay.SINGLE_TAP_CONFIRM)
        verify { mockDispatcher.onStylusTap(testPoint) }
    }

    @Test
    fun stylusDoubleTap() {
        sendStylusDown()
        sendStylusUp()
        Thread.sleep(Delay.BETWEEN_DOUBLE_TAPS)
        sendStylusDown()
        sendStylusUp()
        verify { mockDispatcher.onStylusDoubleTap(testPoint) }
    }

    @Test
    fun stylusLongPress() {
        sendStylusDown()
        Thread.sleep(Delay.LONG_PRESS_CONFIRM)
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
        sendHoverEvent(Factory.obtainHoverEvent(testPoint, InputDevice.SOURCE_STYLUS))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    @Test
    fun stylusHover2() {
        sendGenericEvent(Factory.obtainHoverEvent(testPoint, InputDevice.SOURCE_STYLUS))
        verify { mockDispatcher.onMouseMove(testPoint) }
    }

    /********************* Swipe vs Scale detection  *************************/
    private val EXPECT_NONE = 0
    private val EXPECT_SCALE = 1
    private val EXPECT_SWIPE = 2

    /**
     * [p1Step] Step-size for pointer 1
     * [p2Step] Step-size for pointer 2
     * [expect] Expected gesture to be detected
     */
    private fun runSwipeVsScaleTest(p1Step: Point, p2Step: Point, expect: Int) {
        // SwipeVsScale is ony used when 2-finger swipe is set to 'remote-scroll'
        targetPrefs.edit { putString("gesture_swipe2", "remote-scroll") }
        setup()

        val p1 = PointF(500f, 500f)
        val p2 = PointF(1000f, 1000f)

        sendDown(p1)
        sendEvent(Factory.obtainPointerDownEvent(downEvent, p1, p2))
        repeat(30) {
            p1.offset(p1Step.x.toFloat(), p1Step.y.toFloat())
            p2.offset(p2Step.x.toFloat(), p2Step.y.toFloat())
            sendEvent(Factory.obtainMoveEvent(downEvent, p1, p2))
        }

        if (expect == EXPECT_SWIPE) verify { mockDispatcher.onSwipe2(any(), any(), any(), any()) }
        else verify(exactly = 0) { mockDispatcher.onSwipe2(any(), any(), any(), any()) }

        if (expect == EXPECT_SCALE) verify { mockDispatcher.onScale(any(), any(), any()) }
        else verify(exactly = 0) { mockDispatcher.onScale(any(), any(), any()) }
    }

    @Test
    fun swipeVsScale() {
        runSwipeVsScaleTest(Point(-10, -10), Point(10, 10), EXPECT_SCALE) // Pinch-out
        runSwipeVsScaleTest(Point(10, 10), Point(-10, -10), EXPECT_SCALE) // Pinch-in
        runSwipeVsScaleTest(Point(0, -10), Point(0, 10), EXPECT_SCALE) // 1st up, 2nd down
        runSwipeVsScaleTest(Point(0, 10), Point(0, -10), EXPECT_SCALE) // 1st down, 2nd up
        runSwipeVsScaleTest(Point(-6, 10), Point(6, 10), EXPECT_SCALE) // >45 degree

        runSwipeVsScaleTest(Point(0, 10), Point(0, 10), EXPECT_SWIPE) // Parallel down
        runSwipeVsScaleTest(Point(0, -10), Point(0, -10), EXPECT_SWIPE) // Parallel up
        runSwipeVsScaleTest(Point(10, 0), Point(10, 0), EXPECT_SWIPE) // Parallel right
        runSwipeVsScaleTest(Point(-10, 0), Point(-10, 0), EXPECT_SWIPE) // Parallel left
        runSwipeVsScaleTest(Point(-2, 10), Point(2, 10), EXPECT_SWIPE) // <30 degree

        runSwipeVsScaleTest(Point(-4, 10), Point(4, 10), EXPECT_NONE) // 30-45 degree
        runSwipeVsScaleTest(Point(-3, 10), Point(3, 10), EXPECT_NONE) // 30-45 degree
    }


    /************************ Event Generation ***************************************************/
    object Factory {
        private val setActionButtonMethod = MotionEvent::class.java.getDeclaredMethod("setActionButton", Int::class.java)

        private fun now() = SystemClock.uptimeMillis()

        private fun createPointerProperties(count: Int, source: Int): Array<PointerProperties> {
            val pointerToolType = when (source) {
                InputDevice.SOURCE_TOUCHSCREEN -> MotionEvent.TOOL_TYPE_FINGER
                InputDevice.SOURCE_MOUSE -> MotionEvent.TOOL_TYPE_MOUSE
                InputDevice.SOURCE_STYLUS -> MotionEvent.TOOL_TYPE_STYLUS
                else -> MotionEvent.TOOL_TYPE_UNKNOWN
            }

            return Array(count) {
                PointerProperties().apply {
                    id = it + 5 // Use larger ids to detect accidental misuse of pointer-id as pointer-index
                    toolType = pointerToolType
                }
            }
        }

        private fun createPointerCoords(coords: List<PointF>): Array<PointerCoords> {
            return Array(coords.size) {
                val coord = coords[it]
                PointerCoords().apply {
                    clear()
                    x = coord.x
                    y = coord.y
                }
            }
        }

        private fun obtainMotionEvent(downTime: Long, eventTime: Long, action: Int, actionButton: Int, coords: List<PointF>, source: Int): MotionEvent {
            val pointerProperties = createPointerProperties(coords.size, source)
            val pointerCoords = createPointerCoords(coords)
            val buttonState = actionButton

            val event = MotionEvent.obtain(downTime, eventTime, action, coords.size, pointerProperties, pointerCoords, 0, buttonState, 1f, 1f, 0, 0, source, 0)
            setActionButtonMethod.invoke(event, actionButton)
            return event
        }

        fun obtainDownEvent(coord: PointF, source: Int): MotionEvent {
            val time = now()
            return obtainMotionEvent(time, time, MotionEvent.ACTION_DOWN, 0, listOf(coord), source)
        }

        fun obtainPointerDownEvent(downEvent: MotionEvent, vararg coords: PointF): MotionEvent {
            check(coords.size >= 2)
            return obtainMotionEvent(downEvent.downTime, now(), MotionEvent.ACTION_POINTER_DOWN, 0, coords.toList(), downEvent.source)
        }

        fun obtainMoveEvent(downEvent: MotionEvent, vararg coords: PointF): MotionEvent {
            return obtainMotionEvent(downEvent.downTime, now(), MotionEvent.ACTION_MOVE, 0, coords.toList(), downEvent.source)
        }

        fun obtainPointerUpEvent(downEvent: MotionEvent, vararg coords: PointF): MotionEvent {
            check(coords.size >= 2)
            return obtainMotionEvent(downEvent.downTime, now(), MotionEvent.ACTION_POINTER_UP, 0, coords.toList(), downEvent.source)
        }

        fun obtainUpEvent(downEvent: MotionEvent, coord: PointF): MotionEvent {
            return obtainMotionEvent(downEvent.downTime, now(), MotionEvent.ACTION_UP, 0, listOf(coord), downEvent.source)
        }

        fun obtainButtonPressEvent(button: Int, coord: PointF, source: Int): MotionEvent {
            val time = now()
            return obtainMotionEvent(time, time, MotionEvent.ACTION_BUTTON_PRESS, button, listOf(coord), source)
        }

        fun obtainButtonReleaseEvent(pressEvent: MotionEvent, coord: PointF): MotionEvent {
            return obtainMotionEvent(pressEvent.downTime, now(), MotionEvent.ACTION_BUTTON_RELEASE, pressEvent.actionButton, listOf(coord), pressEvent.source)
        }

        fun obtainScrollEvent(coord: PointF): MotionEvent {
            val time = now()
            return obtainMotionEvent(time, time, MotionEvent.ACTION_SCROLL, 0, listOf(coord), InputDevice.SOURCE_MOUSE)
        }

        fun obtainHoverEvent(coord: PointF, source: Int): MotionEvent {
            val time = now()
            return obtainMotionEvent(time, time, MotionEvent.ACTION_HOVER_MOVE, 0, listOf(coord), source)
        }

        fun obtainCancelEvent(downEvent: MotionEvent, coord: PointF): MotionEvent {
            return obtainMotionEvent(downEvent.downTime, now(), MotionEvent.ACTION_CANCEL, 0, listOf(coord), downEvent.source)
        }
    }

    /************************* Event Injection *******************************************************/

    // Delays required by some tests
    object Delay {
        val BETWEEN_DOUBLE_TAPS = (ViewConfiguration.getDoubleTapTimeout() + doubleTapMinTime()) / 2L
        val SINGLE_TAP_CONFIRM = ViewConfiguration.getDoubleTapTimeout() + 200L
        val LONG_PRESS_CONFIRM = ViewConfiguration.getLongPressTimeout() + 200L

        private fun doubleTapMinTime(): Int {
            return ViewConfiguration::class.java.getDeclaredMethod("getDoubleTapMinTime").invoke(null) as Int
        }
    }

    private lateinit var downEvent: MotionEvent

    private fun sendEvent(event: MotionEvent) = instrumentation.runOnMainSync { touchHandler.onTouchEvent(event) }
    private fun sendGenericEvent(event: MotionEvent) = instrumentation.runOnMainSync { touchHandler.onGenericMotionEvent(event) }
    private fun sendHoverEvent(event: MotionEvent) = instrumentation.runOnMainSync { touchHandler.onHoverEvent(event) }

    private fun sendDown(p: PointF = testPoint) {
        downEvent = Factory.obtainDownEvent(p, InputDevice.SOURCE_TOUCHSCREEN)
        sendEvent(downEvent)
    }

    private fun sendUp(p: PointF = testPoint) = sendEvent(Factory.obtainUpEvent(downEvent, p))
    private fun sendMove(p: PointF) = sendEvent(Factory.obtainMoveEvent(downEvent, p))

    private fun sendMouseButtonPress(button: Int) {
        downEvent = Factory.obtainButtonPressEvent(button, testPoint, InputDevice.SOURCE_MOUSE)
        sendGenericEvent(downEvent)
    }

    private fun sendMouseButtonRelease() = sendGenericEvent(Factory.obtainButtonReleaseEvent(downEvent, testPoint))
    private fun sendMouseButtonMove(point: PointF) = sendEvent(Factory.obtainMoveEvent(downEvent, point))

    private fun sendStylusDown(p: PointF = testPoint) {
        downEvent = Factory.obtainDownEvent(p, InputDevice.SOURCE_STYLUS)
        sendEvent(downEvent)
    }

    private fun sendStylusUp(p: PointF = testPoint) = sendEvent(Factory.obtainUpEvent(downEvent, p))
    private fun sendStylusMove(p: PointF) = sendEvent(Factory.obtainMoveEvent(downEvent, p))

}