/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net

import android.content.Context
import android.net.TetheringManager.TETHER_ERROR_DHCPSERVER_ERROR
import android.net.TetheringManager.TetheringEventCallback
import android.net.TetheringManager.TetheringInterfaceRegexps
import android.os.IBinder
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.util.LinkedList
import java.util.concurrent.Executor
import kotlin.test.assertTrue

private val TEST_NETWORK = Network(42)
private val TEST_OTHER_NETWORK = Network(43)

@RunWith(AndroidJUnit4::class)
@SmallTest
class TetheringManagerTest {
    private val connector = mock(ITetheringConnector::class.java)
    private val connectorBinder = mock(IBinder::class.java)
    private val context = mock(Context::class.java)

    private lateinit var manager: TetheringManager
    private lateinit var serviceCb: ITetheringEventCallback

    @Before
    fun setUp() {
        val pkgName = "test.package.name"
        doReturn(pkgName).`when`(context).opPackageName
        doReturn(connector).`when`(connectorBinder).queryLocalInterface(any())
        manager = TetheringManager(context, connectorBinder)

        val cbCaptor = ArgumentCaptor.forClass(ITetheringEventCallback::class.java)
        verify(connector).registerTetheringEventCallback(cbCaptor.capture(), eq(pkgName))
        serviceCb = cbCaptor.value
    }

    @Test
    fun testCallbacks() {
        // Register a first callback and send an initial configuration
        val eventCb1 = mock(TetheringEventCallback::class.java)
        val executor1 = TestExecutor()
        manager.registerTetheringEventCallback(executor1, eventCb1)
        executor1.assertIdle()

        val bluetoothRegexs = arrayOf("btregex1", "btregex2")
        val usbRegexs = arrayOf("usbregex1", "usbregex2")
        val wifiRegexs = emptyArray<String>()

        val config = TetheringConfigurationParcel()
        config.tetherableBluetoothRegexs = bluetoothRegexs
        config.tetherableUsbRegexs = usbRegexs
        config.tetherableWifiRegexs = wifiRegexs

        val tetherableIfaces = arrayOf("iface1", "iface2")

        val states = TetherStatesParcel()
        states.availableList = tetherableIfaces
        states.tetheredList = emptyArray()
        states.localOnlyList = emptyArray()
        states.erroredIfaceList = emptyArray()
        states.lastErrorList = intArrayOf()

        val startedParcel = TetheringCallbackStartedParcel()
        startedParcel.tetheringSupported = true
        startedParcel.upstreamNetwork = TEST_NETWORK
        startedParcel.config = config
        startedParcel.states = states

        serviceCb.onCallbackStarted(startedParcel)
        executor1.runAll()

        val expectedRegexps = TetheringInterfaceRegexps(bluetoothRegexs, usbRegexs, wifiRegexs)

        // Verify that initial callbacks are called in order
        val ordered1 = inOrder(eventCb1)
        ordered1.verify(eventCb1).onTetheringSupported(true)
        ordered1.verify(eventCb1).onUpstreamChanged(TEST_NETWORK)
        ordered1.verify(eventCb1).onTetherableInterfaceRegexpsChanged(expectedRegexps)
        ordered1.verify(eventCb1).onTetherableInterfacesChanged(tetherableIfaces.toList())
        ordered1.verify(eventCb1).onTetheredInterfacesChanged(emptyList())
        verifyNoMoreInteractions(eventCb1)

        // Verify upstream changed callback
        serviceCb.onUpstreamChanged(TEST_OTHER_NETWORK)
        executor1.runAll()
        verify(eventCb1).onUpstreamChanged(TEST_OTHER_NETWORK)
        verifyNoMoreInteractions(eventCb1)

        // Verify regexps changed callback
        val newWifiRegexs = arrayOf("wifiRegex")
        config.tetherableWifiRegexs = newWifiRegexs
        val newExpectedRegexps = TetheringInterfaceRegexps(
                bluetoothRegexs, usbRegexs, newWifiRegexs)
        serviceCb.onConfigurationChanged(config)
        executor1.runAll()
        verify(eventCb1).onTetherableInterfaceRegexpsChanged(newExpectedRegexps)
        verifyNoMoreInteractions(eventCb1)

        // Verify tetherable ifaces changed callback
        val newTetherableIfaces = arrayOf("iface1", "iface2", "iface3")
        states.availableList = newTetherableIfaces
        serviceCb.onTetherStatesChanged(states)
        executor1.runAll()
        verify(eventCb1).onTetherableInterfacesChanged(newTetherableIfaces.asList())
        verifyNoMoreInteractions(eventCb1)

        // Verify error callback
        states.erroredIfaceList = arrayOf("iface2")
        states.lastErrorList = intArrayOf(TETHER_ERROR_DHCPSERVER_ERROR)
        executor1.runAll()
        verify(eventCb1).onError("iface2", TETHER_ERROR_DHCPSERVER_ERROR)
        verifyNoMoreInteractions(eventCb1)

        // Verify tethered interfaces changed callback
        val newTetheredIfaces = arrayOf("tetheredIface0")
        states.tetheredList = newTetheredIfaces
        serviceCb.onTetherStatesChanged(states)
        executor1.runAll()
        verify(eventCb1).onTetheredInterfacesChanged(newTetheredIfaces.toList())
        verifyNoMoreInteractions(eventCb1)

        // Verify that callbacks registered later get called immediately with updated values
        val eventCb2 = mock(TetheringEventCallback::class.java)
        val executor2 = TestExecutor()
        manager.registerTetheringEventCallback(executor2, eventCb2)
        executor2.runAll()

        val ordered2 = inOrder(eventCb2)
        ordered2.verify(eventCb2).onTetheringSupported(true)
        ordered2.verify(eventCb2).onUpstreamChanged(TEST_OTHER_NETWORK)
        ordered2.verify(eventCb2).onError("iface2", TETHER_ERROR_DHCPSERVER_ERROR)
        ordered2.verify(eventCb2).onTetherableInterfaceRegexpsChanged(newExpectedRegexps)
        ordered2.verify(eventCb2).onTetherableInterfacesChanged(newTetherableIfaces.toList())
        ordered2.verify(eventCb2).onTetheredInterfacesChanged(newTetheredIfaces.toList())
        verifyNoMoreInteractions(eventCb2)
    }

    /**
     * A test {@link Executor} that runs tasks sequentially and synchronously when requested.
     */
    private class TestExecutor : Executor {
        private val queue = LinkedList<Runnable>()
        override fun execute(command: Runnable) {
            queue.add(command)
        }

        fun runAll() {
            while (!queue.isEmpty()) {
                queue.pop().run()
            }
        }

        fun assertIdle() {
            assertTrue(queue.isEmpty(), "Executor should be idle")
        }
    }
}