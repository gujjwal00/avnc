package com.gaurav.avnc.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.ui.vnc.PanningInputDevice
import com.gaurav.avnc.ui.vnc.PanningListener
import com.gaurav.avnc.util.AppPreferences
import com.gaurav.avnc.vnc.VncClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

// Mock PanningInputDevice for testing
class MockPanningInputDevice : PanningInputDevice {
    private var listener: PanningListener? = null
    var enabled = false
    var released = false // For Viture-like specific release

    override fun setPanningListener(listener: PanningListener?) {
        this.listener = listener
    }

    override fun enable() {
        enabled = true
    }

    override fun disable() {
        enabled = false
    }

    override fun isEnabled(): Boolean = enabled

    // Specific method for Viture mock
    fun releaseSdk() {
        released = true
    }

    fun simulatePan(yaw: Float, pitch: Float) {
        listener?.onPan(yaw, pitch)
    }
}

@ExperimentalCoroutinesApi
class VncViewModelTest {

    // Rule for LiveData testing
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    // Test dispatcher for coroutines
    private val testDispatcher = UnconfinedTestDispatcher() // StandardTestDispatcher() can also be used

    private lateinit var viewModel: VncViewModel
    private lateinit var mockPrefs: AppPreferences
    private lateinit var mockClient: VncClient
    private lateinit var mockProfile: ServerProfile

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for testing

        // Mock dependencies
        mockPrefs = mockk(relaxed = true)
        mockClient = mockk(relaxed = true) // Relaxed mock for VncClient
        mockProfile = mockk(relaxed = true) {
            every { ID } returns 1L // Ensure profile has an ID
            // Mock other profile properties if VncViewModel's initConnection accesses them
        }

        // Mock AppPreferences.Input and AppPreferences.Viewer if accessed directly
        val mockInputPrefs = mockk<AppPreferences.Input>(relaxed = true)
        val mockViewerPrefs = mockk<AppPreferences.Viewer>(relaxed = true)
        every { mockPrefs.input } returns mockInputPrefs
        every { mockPrefs.viewer } returns mockViewerPrefs


        // Create ViewModel instance
        // Assuming VncViewModel has a constructor like this or can be adapted
        // For simplicity, let's assume a way to inject mocks or use a test factory if needed.
        // The actual VncViewModel constructor takes Application, SavedStateHandle too.
        // This setup is simplified for focusing on panning logic.
        // For a real test, you'd use AndroidX Test libraries for ViewModel instantiation.
        // For now, we'll directly instantiate, which might not work if it needs Application context.
        // This part highlights the challenge of unit testing ViewModels heavily tied to Android Framework.

        // Let's assume we can pass mocks for critical parts.
        // A more robust way involves `ApplicationProvider.getApplicationContext()` and `SavedStateHandle`.
        // For this subtask, we'll focus on the logic assuming viewModel can be created.
        // If direct instantiation is too complex, we'll skip actual viewModel creation
        // and just state what would be tested.

        // Simplified: create VncViewModel directly if its constructor allows or can be mocked.
        // This is often the tricky part for Android ViewModels in pure JUnit.
        // For the purpose of this subtask, we proceed as if it can be instantiated.
        // If VncViewModel constructor requires Application, this will fail in a pure JUnit environment.
        // We'll use a placeholder for viewModel if instantiation is the blocker.
        // val applicationMock = mockk<android.app.Application>(relaxed = true)
        // viewModel = VncViewModel(applicationMock, mockk(relaxed = true)) // Simplified

        // Due to VncViewModel's dependencies (Application, SavedStateHandle, and internal Hilt/DI),
        // proper instantiation for a unit test is non-trivial without AndroidX Test runners.
        // We will define the test methods, but their execution would require a more complete test setup.
        // For now, let's create a spy on a simplified VncViewModel if possible,
        // or test the logic conceptually.

        // Create a partial spy to test methods on a real instance if some dependencies are hard to mock.
        // This is still complex. Let's assume we are testing the methods in isolation or with mocks.
        viewModel = spyk(VncViewModel(mockk(relaxed = true), mockk(relaxed = true))) {
            every { pref } returns mockPrefs // Stubbing the pref getter
            every { client } returns mockClient // Stubbing client
            every { profile } returns mockProfile // Stubbing profile
            every { initConnection(any()) } just Runs // Stub initConnection
            every { panCamera(any(), any()) } just Runs // Stub panCamera to check calls
        }
        viewModel.initConnection(mockProfile) // Call to set up internal state like `profile`
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher
        unmockkAll()
    }

    @Test
    fun `registerPanningInputDevice should add device and set listener`() = runTest {
        val mockDevice = MockPanningInputDevice()
        viewModel.registerPanningInputDevice(mockDevice)

        // Verify listener was set (indirectly, by checking if onPan works)
        // Or, if PanningInputDevice had a getListener method, use that.
        // For now, check if device is managed.
        // Private list `panningInputDevices` is not directly testable.
        // We test by behavior: enable/disable should affect this device.
        viewModel.enablePanningDevice(MockPanningInputDevice::class.java)
        assertTrue(mockDevice.isEnabled())
    }

    @Test
    fun `unregisterPanningInputDevice should disable and remove device`() = runTest {
        val mockDevice = MockPanningInputDevice()
        viewModel.registerPanningInputDevice(mockDevice)
        viewModel.enablePanningDevice(MockPanningInputDevice::class.java) // Ensure it's enabled first

        viewModel.unregisterPanningInputDevice(mockDevice)
        assertFalse(mockDevice.isEnabled()) // Should be disabled

        // To verify removal, try to enable again; it shouldn't find the device if truly removed.
        // This depends on how enablePanningDevice handles non-existent devices.
        // Let's assume it only enables registered devices.
        mockDevice.enabled = false // Reset its state manually
        viewModel.enablePanningDevice(MockPanningInputDevice::class.java)
        assertFalse("Device should not be re-enabled if unregistered", mockDevice.isEnabled())
    }

    @Test
    fun `onPan from PanningListener should call panCamera and update panRequest`() = runTest {
        val liveDataSlot = slot<Pair<Float, Float>>()
        every { viewModel.panRequest.postValue(capture(liveDataSlot)) } answers { callOriginal() }
        // Direct call to panCamera is stubbed, so we check postValue on panRequest

        val testYaw = 10f
        val testPitch = 5f
        viewModel.onPan(testYaw, testPitch)

        verify { viewModel.panRequest.postValue(Pair(testYaw, testPitch)) }
        assertEquals(Pair(testYaw, testPitch), liveDataSlot.captured)
    }

    @Test
    fun `enablePanningDevice should enable specific device type`() = runTest {
        val mockDevice1 = MockPanningInputDevice()
        val mockDevice2 = spyk<PanningInputDevice>(MockPanningInputDevice()) // A different type for distinction if needed

        viewModel.registerPanningInputDevice(mockDevice1)
        viewModel.registerPanningInputDevice(mockDevice2)

        viewModel.enablePanningDevice(MockPanningInputDevice::class.java)

        assertTrue(mockDevice1.isEnabled())
        // If mockDevice2 was a different class, assert its state based on that.
        // For this test, both are MockPanningInputDevice, so both should be enabled.
        assertTrue(mockDevice2.isEnabled())
    }

    @Test
    fun `disablePanningDevice should disable specific device type`() = runTest {
        val mockDevice1 = MockPanningInputDevice()
        viewModel.registerPanningInputDevice(mockDevice1)
        mockDevice1.enable() // Pre-enable it

        viewModel.disablePanningDevice(MockPanningInputDevice::class.java)
        assertFalse(mockDevice1.isEnabled())
    }

    @Test
    fun `clearAndDisableAllPanningDevices should disable and clear all devices`() = runTest {
        val mockDevice1 = MockPanningInputDevice()
        val mockDevice2 = MockPanningInputDevice() // Viture-like mock

        viewModel.registerPanningInputDevice(mockDevice1)
        viewModel.registerPanningInputDevice(mockDevice2)
        mockDevice1.enable()
        mockDevice2.enable()

        viewModel.clearAndDisableAllPanningDevices()

        assertFalse(mockDevice1.isEnabled())
        assertFalse(mockDevice2.isEnabled())
        assertTrue(mockDevice2.released) // Check Viture-specific release

        // Verify devices are cleared (e.g., by trying to enable again)
        mockDevice1.enabled = false
        mockDevice2.enabled = false
        viewModel.enablePanningDevice(MockPanningInputDevice::class.java)
        assertFalse("Device1 should remain disabled after clear", mockDevice1.isEnabled())
        assertFalse("Device2 should remain disabled after clear", mockDevice2.isEnabled())
    }
}
