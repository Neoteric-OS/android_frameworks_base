/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.input

import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayViewport
import android.hardware.input.InputManagerInternal
import android.os.IInputConstants
import android.os.test.TestLooper
import android.platform.test.annotations.Presubmit
import android.view.Display
import android.view.PointerIcon
import androidx.test.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.View.OnKeyListener
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.InputEventInjectionSync
import android.graphics.Canvas

/**
 * Tests for {@link InputManagerService}.
 *
 * Build/Install/Run:
 * atest FrameworksServicesTests:InputManagerServiceTests
 */
@Presubmit
class InputManagerServiceTests {

    @get:Rule
    val rule = MockitoJUnit.rule()!!

    @Mock
    private lateinit var native: NativeInputManagerService

    @Mock
    private lateinit var wmCallbacks: InputManagerService.WindowManagerCallbacks

    private lateinit var service: InputManagerService
    private lateinit var localService: InputManagerInternal
    private lateinit var context: Context
    private lateinit var testLooper: TestLooper

    @Before
    fun setup() {
        context = spy(ContextWrapper(InstrumentationRegistry.getContext()))
        testLooper = TestLooper()
        service =
            InputManagerService(object : InputManagerService.Injector(context, testLooper.looper) {
                override fun getNativeService(
                    service: InputManagerService?
                ): NativeInputManagerService {
                    return native
                }

                override fun registerLocalService(service: InputManagerInternal?) {
                    localService = service!!
                }
            })
        assertTrue("Local service must be registered", this::localService.isInitialized)
        service.setWindowManagerCallbacks(wmCallbacks)
    }

    @Test
    fun testPointerDisplayUpdatesWhenDisplayViewportsChanged() {
        val displayId = 123
        `when`(wmCallbacks.pointerDisplayId).thenReturn(displayId)
        val viewports = listOf<DisplayViewport>()
        localService.setDisplayViewports(viewports)
        verify(native).setDisplayViewports(any(Array<DisplayViewport>::class.java))
        verify(native).setPointerDisplayId(displayId)

        val x = 42f
        val y = 314f
        service.onPointerDisplayIdChanged(displayId, x, y)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(displayId, x, y)
    }

    @Test
    fun testSetVirtualMousePointerDisplayId() {
        // Set the virtual mouse pointer displayId, and ensure that the calling thread is blocked
        // until the native callback happens.
        var countDownLatch = CountDownLatch(1)
        val overrideDisplayId = 123
        Thread {
            assertTrue("Setting virtual pointer display should succeed",
                localService.setVirtualMousePointerDisplayId(overrideDisplayId))
            countDownLatch.countDown()
        }.start()
        assertFalse("Setting virtual pointer display should block",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))

        val x = 42f
        val y = 314f
        service.onPointerDisplayIdChanged(overrideDisplayId, x, y)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(overrideDisplayId, x, y)
        assertTrue("Native callback unblocks calling thread",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))
        verify(native).setPointerDisplayId(overrideDisplayId)

        // Ensure that setting the same override again succeeds immediately.
        assertTrue("Setting the same virtual mouse pointer displayId again should succeed",
            localService.setVirtualMousePointerDisplayId(overrideDisplayId))

        // Ensure that we did not query WM for the pointerDisplayId when setting the override
        verify(wmCallbacks, never()).pointerDisplayId

        // Unset the virtual mouse pointer displayId, and ensure that we query WM for the new
        // pointer displayId and the calling thread is blocked until the native callback happens.
        countDownLatch = CountDownLatch(1)
        val pointerDisplayId = 42
        `when`(wmCallbacks.pointerDisplayId).thenReturn(pointerDisplayId)
        Thread {
            assertTrue("Unsetting virtual mouse pointer displayId should succeed",
                localService.setVirtualMousePointerDisplayId(Display.INVALID_DISPLAY))
            countDownLatch.countDown()
        }.start()
        assertFalse("Unsetting virtual mouse pointer displayId should block",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))

        service.onPointerDisplayIdChanged(pointerDisplayId, x, y)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(pointerDisplayId, x, y)
        assertTrue("Native callback unblocks calling thread",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))
        verify(native).setPointerDisplayId(pointerDisplayId)
    }

    @Test
    fun testSetVirtualMousePointerDisplayId_unsuccessfulUpdate() {
        // Set the virtual mouse pointer displayId, and ensure that the calling thread is blocked
        // until the native callback happens.
        val countDownLatch = CountDownLatch(1)
        val overrideDisplayId = 123
        Thread {
            assertFalse("Setting virtual pointer display should be unsuccessful",
                localService.setVirtualMousePointerDisplayId(overrideDisplayId))
            countDownLatch.countDown()
        }.start()
        assertFalse("Setting virtual pointer display should block",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))

        val x = 42f
        val y = 314f
        // Assume the native callback updates the pointerDisplayId to the incorrect value.
        service.onPointerDisplayIdChanged(Display.INVALID_DISPLAY, x, y)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(Display.INVALID_DISPLAY, x, y)
        assertTrue("Native callback unblocks calling thread",
            countDownLatch.await(100, TimeUnit.MILLISECONDS))
        verify(native).setPointerDisplayId(overrideDisplayId)
    }

    @Test
    fun testSetVirtualMousePointerDisplayId_competingRequests() {
        val firstRequestSyncLatch = CountDownLatch(1)
        doAnswer {
            firstRequestSyncLatch.countDown()
        }.`when`(native).setPointerDisplayId(anyInt())

        val firstRequestLatch = CountDownLatch(1)
        val firstOverride = 123
        Thread {
            assertFalse("Setting virtual pointer display from thread 1 should be unsuccessful",
                localService.setVirtualMousePointerDisplayId(firstOverride))
            firstRequestLatch.countDown()
        }.start()
        assertFalse("Setting virtual pointer display should block",
            firstRequestLatch.await(100, TimeUnit.MILLISECONDS))

        assertTrue("Wait for first thread's request should succeed",
            firstRequestSyncLatch.await(100, TimeUnit.MILLISECONDS))

        val secondRequestLatch = CountDownLatch(1)
        val secondOverride = 42
        Thread {
            assertTrue("Setting virtual mouse pointer from thread 2 should be successful",
                localService.setVirtualMousePointerDisplayId(secondOverride))
            secondRequestLatch.countDown()
        }.start()
        assertFalse("Setting virtual mouse pointer should block",
            secondRequestLatch.await(100, TimeUnit.MILLISECONDS))

        val x = 42f
        val y = 314f
        // Assume the native callback updates directly to the second request.
        service.onPointerDisplayIdChanged(secondOverride, x, y)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(secondOverride, x, y)
        assertTrue("Native callback unblocks first thread",
            firstRequestLatch.await(100, TimeUnit.MILLISECONDS))
        assertTrue("Native callback unblocks second thread",
            secondRequestLatch.await(100, TimeUnit.MILLISECONDS))
        verify(native, times(2)).setPointerDisplayId(anyInt())
    }

    @Test
    fun onDisplayRemoved_resetAllAdditionalInputProperties() {
        setVirtualMousePointerDisplayIdAndVerify(10)

        localService.setPointerIconVisible(false, 10)
        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NULL))
        localService.setPointerAcceleration(5f, 10)
        verify(native).setPointerAcceleration(eq(5f))

        service.onDisplayRemoved(10)
        verify(native).displayRemoved(eq(10))
        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NOT_SPECIFIED))
        verify(native).setPointerAcceleration(
            eq(IInputConstants.DEFAULT_POINTER_ACCELERATION.toFloat()))
        verifyNoMoreInteractions(native)

        // This call should not block because the virtual mouse pointer override was never removed.
        localService.setVirtualMousePointerDisplayId(10)

        verify(native).setPointerDisplayId(eq(10))
        verifyNoMoreInteractions(native)
    }

    @Test
    fun updateAdditionalInputPropertiesForOverrideDisplay() {
        setVirtualMousePointerDisplayIdAndVerify(10)

        localService.setPointerIconVisible(false, 10)
        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NULL))
        localService.setPointerAcceleration(5f, 10)
        verify(native).setPointerAcceleration(eq(5f))

        localService.setPointerIconVisible(true, 10)
        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NOT_SPECIFIED))
        localService.setPointerAcceleration(1f, 10)
        verify(native).setPointerAcceleration(eq(1f))

        // Verify that setting properties on a different display is not propagated until the
        // pointer is moved to that display.
        localService.setPointerIconVisible(false, 20)
        localService.setPointerAcceleration(6f, 20)
        verifyNoMoreInteractions(native)

        clearInvocations(native)
        setVirtualMousePointerDisplayIdAndVerify(20)

        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NULL))
        verify(native).setPointerAcceleration(eq(6f))
    }

    @Test
    fun setAdditionalInputPropertiesBeforeOverride() {
        localService.setPointerIconVisible(false, 10)
        localService.setPointerAcceleration(5f, 10)

        verifyNoMoreInteractions(native)

        setVirtualMousePointerDisplayIdAndVerify(10)

        verify(native).setPointerIconType(eq(PointerIcon.TYPE_NULL))
        verify(native).setPointerAcceleration(eq(5f))
    }

    private fun setVirtualMousePointerDisplayIdAndVerify(overrideDisplayId: Int) {
        val thread = Thread { localService.setVirtualMousePointerDisplayId(overrideDisplayId) }
        thread.start()

        // Allow some time for the set override call to park while waiting for the native callback.
        Thread.sleep(100 /*millis*/)
        verify(native).setPointerDisplayId(overrideDisplayId)

        service.onPointerDisplayIdChanged(overrideDisplayId, 0f, 0f)
        testLooper.dispatchNext()
        verify(wmCallbacks).notifyPointerDisplayIdChanged(overrideDisplayId, 0f, 0f)
        thread.join(100 /*millis*/)
    }

    @Test
    fun addUniqueIdAssociationByDescriptor_verifyAssociations() {
        // Overall goal is to have 2 displays and verify that events from the InputDevice are
        // sent only to the view that is on the associated display.
        // So, associate the InputDevice with display 1, then send and verify KeyEvents.
        // Then remove associations, then associate the InputDevice with display 2, then send
        // and verify commands.

        // Make 2 virtual displays with some mock Surfaces and SurfaceViews
        val displayManager: DisplayManager = context.getSystemService(
            DisplayManager::class.java
        )
        val mockSurface1 = mock(Surface::class.java)
        val mockSurface2 = mock(Surface::class.java)
        val mockSurfaceView1 = mock(SurfaceView::class.java)
        val mockSurfaceView2 = mock(SurfaceView::class.java)
        val mockSurfaceHolder1 = mock(SurfaceHolder::class.java)
        `when`(mockSurfaceView1.holder).thenReturn(mockSurfaceHolder1)
        `when`(mockSurfaceHolder1.surface).thenReturn(mockSurface1)
        val mockSurfaceHolder2 = mock(SurfaceHolder::class.java)
        `when`(mockSurfaceView2.holder).thenReturn(mockSurfaceHolder2)
        `when`(mockSurfaceHolder2.surface).thenReturn(mockSurface2)

        // Create mock canvas objects
        val mockCanvas1 = mock(Canvas::class.java)
        `when`(mockSurface1.lockCanvas(any())).thenReturn(mockCanvas1)
        val mockCanvas2 = mock(Canvas::class.java)
        `when`(mockSurface2.lockCanvas(any())).thenReturn(mockCanvas2)

        val virtualDisplay1: VirtualDisplay = displayManager.createVirtualDisplay(
            /* displayName= */ "testVirtualDisplay1",
            /* width= */ 100,
            /* height= */ 100,
            /* densityDpi= */ 100,
            /* surface= */ null,
            /* flags= */ 0
        )
        val virtualDisplay2: VirtualDisplay = displayManager.createVirtualDisplay(
            /* displayName= */ "testVirtualDisplay2",
            /* width= */ 100,
            /* height= */ 100,
            /* densityDpi= */ 100,
            /* surface= */ null,
            /* flags= */ 0
        )

        // Simulate an InputDevice
        val inputDeviceName = "abc"
        val inputDeviceDescriptor = "def"
        val inputDeviceId = 789
        val keyCharacterMap = mock(KeyCharacterMap::class.java)
        val inputDevice = InputDevice(
            /* id= */ inputDeviceId,
            /* generation= */ -1,
            /* controllerNumber= */ 1,
            /* name= */ inputDeviceName,
            /* vendorId= */ 0x0453,
            /* productId= */ 0x0b12,
            /* descriptor= */ inputDeviceDescriptor,
            /* isExternal= */ true,
            /* sources= */ 1,
            /* keyboardType= */ -1,
            /* keyCharacterMap= */ keyCharacterMap,
            /* hasVibrator= */ false,
            /* hasMicrophone= */ false,
            /* hasButtonUnderPad= */ false,
            /* hasSensor= */ false,
            /* hasBattery= */ false
        )

        // Associate input device with display
        service.addUniqueIdAssociationByDescriptor(
            inputDevice.descriptor,
            virtualDisplay1.display.displayId.toString()
        )

        // Simulate 2 different KeyEvents
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(
            /* downTime= */ eventTime,
            /* eventTime= */ eventTime,
            /* action= */ KeyEvent.ACTION_DOWN,
            /* code= */ KeyEvent.KEYCODE_A,
            /* repeat= */ 0,
            /* metaState= */ 0,
            /* deviceId= */ inputDevice.id,
            /* scanCode= */ 0,
            /* flags= */ KeyEvent.FLAG_FROM_SYSTEM,
            /* source= */ InputDevice.SOURCE_KEYBOARD
        )
        val upEvent = KeyEvent(
            /* downTime= */ eventTime,
            /* eventTime= */ eventTime,
            /* action= */ KeyEvent.ACTION_UP,
            /* code= */ KeyEvent.KEYCODE_A,
            /* repeat= */ 0,
            /* metaState= */ 0,
            /* deviceId= */ inputDevice.id,
            /* scanCode= */ 0,
            /* flags= */ KeyEvent.FLAG_FROM_SYSTEM,
            /* source= */ InputDevice.SOURCE_KEYBOARD
        )

        // Create a mock OnKeyListener object
        val mockOnKeyListener = mock(OnKeyListener::class.java)

        // Verify that the event went to Display1 not Display2
        service.injectInputEvent(downEvent, InputEventInjectionSync.NONE)

        // Call the onKey method on the mock OnKeyListener object
        mockOnKeyListener.onKey(mockSurfaceView1, /* keyCode= */ KeyEvent.KEYCODE_A, downEvent)
        mockOnKeyListener.onKey(mockSurfaceView2, /* keyCode= */ KeyEvent.KEYCODE_A, upEvent)

        // Verify that the onKey method was called with the expected arguments
        verify(mockOnKeyListener).onKey(mockSurfaceView1, KeyEvent.KEYCODE_A, downEvent)
        verify(mockOnKeyListener, never()).onKey(mockSurfaceView2, KeyEvent.KEYCODE_A, downEvent)

        // Remove association
        service.removeUniqueIdAssociationByDescriptor(inputDevice.descriptor)

        // Associate with Display2
        service.addUniqueIdAssociationByDescriptor(
            inputDevice.descriptor,
            virtualDisplay2.display.displayId.toString()
        )

        // Simulate a KeyEvent
        service.injectInputEvent(upEvent, InputEventInjectionSync.NONE)

        // Verify that the event went to Display2 not Display1
        verify(mockOnKeyListener).onKey(mockSurfaceView2, KeyEvent.KEYCODE_A, upEvent)
        verify(mockOnKeyListener, never()).onKey(mockSurfaceView1, KeyEvent.KEYCODE_A, upEvent)

        mockSurface1.unlockCanvasAndPost(mockCanvas1)
        mockSurface2.unlockCanvasAndPost(mockCanvas2)
    }

    @Test
    fun addUniqueIdAssociationByPort_verifyAssociations() {
        // Overall goal is to have 2 displays and verify that events from the InputDevice are
        // sent only to the view that is on the associated display.
        // So, associate the InputDevice with display 1, then send and verify KeyEvents.
        // Then remove associations, then associate the InputDevice with display 2, then send
        // and verify commands.

        // Make 2 virtual displays with some mock Surfaces and SurfaceViews
        val displayManager: DisplayManager = context.getSystemService(
            DisplayManager::class.java
        )
        val mockSurface1 = mock(Surface::class.java)
        val mockSurface2 = mock(Surface::class.java)
        val mockSurfaceView1 = mock(SurfaceView::class.java)
        val mockSurfaceView2 = mock(SurfaceView::class.java)
        val mockSurfaceHolder1 = mock(SurfaceHolder::class.java)
        `when`(mockSurfaceView1.holder).thenReturn(mockSurfaceHolder1)
        `when`(mockSurfaceHolder1.surface).thenReturn(mockSurface1)
        val mockSurfaceHolder2 = mock(SurfaceHolder::class.java)
        `when`(mockSurfaceView2.holder).thenReturn(mockSurfaceHolder2)
        `when`(mockSurfaceHolder2.surface).thenReturn(mockSurface2)

        // Create mock canvas objects
        val mockCanvas1 = mock(Canvas::class.java)
        `when`(mockSurface1.lockCanvas(any())).thenReturn(mockCanvas1)
        val mockCanvas2 = mock(Canvas::class.java)
        `when`(mockSurface2.lockCanvas(any())).thenReturn(mockCanvas2)

        val virtualDisplay1: VirtualDisplay = displayManager.createVirtualDisplay(
            /* displayName= */ "testVirtualDisplay1",
            /* width= */ 100,
            /* height= */ 100,
            /* densityDpi= */ 100,
            /* surface= */ null,
            /* flags= */ 0
        )
        val virtualDisplay2: VirtualDisplay = displayManager.createVirtualDisplay(
            /* displayName= */ "testVirtualDisplay2",
            /* width= */ 100,
            /* height= */ 100,
            /* densityDpi= */ 100,
            /* surface= */ null,
            /* flags= */ 0
        )

        // Simulate an InputDevice
        val inputDeviceName = "abc"
        val inputDeviceDescriptor = "def"
        val inputDeviceId = 789
        val keyCharacterMap = mock(KeyCharacterMap::class.java)
        val inputDevice = InputDevice(
            /* id= */ inputDeviceId,
            /* generation= */ -1,
            /* controllerNumber= */ 1,
            /* name= */ inputDeviceName,
            /* vendorId= */ 0x0453,
            /* productId= */ 0x0b12,
            /* descriptor= */ inputDeviceDescriptor,
            /* isExternal= */ true,
            /* sources= */ 1,
            /* keyboardType= */ -1,
            /* keyCharacterMap= */ keyCharacterMap,
            /* hasVibrator= */ false,
            /* hasMicrophone= */ false,
            /* hasButtonUnderPad= */ false,
            /* hasSensor= */ false,
            /* hasBattery= */ false
        )

        // Associate input device with display
        service.addUniqueIdAssociationByPort(
            inputDevice.name,
            virtualDisplay1.display.displayId.toString()
        )

        // Simulate 2 different KeyEvents
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(
            /* downTime= */ eventTime,
            /* eventTime= */ eventTime,
            /* action= */ KeyEvent.ACTION_DOWN,
            /* code= */ KeyEvent.KEYCODE_A,
            /* repeat= */ 0,
            /* metaState= */ 0,
            /* deviceId= */ inputDevice.id,
            /* scanCode= */ 0,
            /* flags= */ KeyEvent.FLAG_FROM_SYSTEM,
            /* source= */ InputDevice.SOURCE_KEYBOARD
        )
        val upEvent = KeyEvent(
            /* downTime= */ eventTime,
            /* eventTime= */ eventTime,
            /* action= */ KeyEvent.ACTION_UP,
            /* code= */ KeyEvent.KEYCODE_A,
            /* repeat= */ 0,
            /* metaState= */ 0,
            /* deviceId= */ inputDevice.id,
            /* scanCode= */ 0,
            /* flags= */ KeyEvent.FLAG_FROM_SYSTEM,
            /* source= */ InputDevice.SOURCE_KEYBOARD
        )

        // Create a mock OnKeyListener object
        val mockOnKeyListener = mock(OnKeyListener::class.java)

        // Verify that the event went to Display1 not Display2
        service.injectInputEvent(downEvent, InputEventInjectionSync.NONE)

        // Call the onKey method on the mock OnKeyListener object
        mockOnKeyListener.onKey(mockSurfaceView1, /* keyCode= */ KeyEvent.KEYCODE_A, downEvent)
        mockOnKeyListener.onKey(mockSurfaceView2, /* keyCode= */ KeyEvent.KEYCODE_A, upEvent)

        // Verify that the onKey method was called with the expected arguments
        verify(mockOnKeyListener).onKey(mockSurfaceView1, KeyEvent.KEYCODE_A, downEvent)
        verify(mockOnKeyListener, never()).onKey(mockSurfaceView2, KeyEvent.KEYCODE_A, downEvent)

        // Remove association
        service.removeUniqueIdAssociationByPort(inputDevice.name)

        // Associate with Display2
        service.addUniqueIdAssociationByPort(
            inputDevice.name,
            virtualDisplay2.display.displayId.toString()
        )

        // Simulate a KeyEvent
        service.injectInputEvent(upEvent, InputEventInjectionSync.NONE)

        // Verify that the event went to Display2 not Display1
        verify(mockOnKeyListener).onKey(mockSurfaceView2, KeyEvent.KEYCODE_A, upEvent)
        verify(mockOnKeyListener, never()).onKey(mockSurfaceView1, KeyEvent.KEYCODE_A, upEvent)

        mockSurface1.unlockCanvasAndPost(mockCanvas1)
        mockSurface2.unlockCanvasAndPost(mockCanvas2)
    }
}
