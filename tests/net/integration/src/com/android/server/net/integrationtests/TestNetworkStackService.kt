/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.net.integrationtests

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.DnsResolver
import android.net.DnsResolver.TYPE_A
import android.net.DnsResolver.TYPE_AAAA
import android.net.INetworkMonitorCallbacks
import android.net.InetAddresses
import android.net.Network
import android.net.metrics.IpConnectivityLog
import android.net.util.SharedLog
import android.networkstack.util.FakeDns
import android.os.IBinder
import androidx.annotation.GuardedBy
import com.android.networkstack.metrics.DataStallStatsUtils
import com.android.server.NetworkStackService
import com.android.server.NetworkStackService.NetworkMonitorBinder
import com.android.server.connectivity.NetworkMonitor
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import kotlin.collections.ArrayList
import kotlin.test.fail

private const val TEST_NETID = 42
private const val TEST_INITIAL_REEVALUATE_DELAY_MS = 10_000

class TestNetworkStackService : Service() {
    companion object {
        val INSTRUMENTATION_ACTION = TestNetworkStackService::class.qualifiedName +
                ".Instrumentation"
    }

    private val instrumentationConnector = NetworkStackInstrumentationConnector()

    override fun onBind(intent: Intent): IBinder = if (INSTRUMENTATION_ACTION == intent.action) {
        instrumentationConnector.asBinder()
    } else {
        TestNetworkStackConnector(makeTestContext())
    }

    private fun makeTestContext() = spy(applicationContext).also {
        doReturn(mock(IBinder::class.java)).`when`(it).getSystemService(Context.NETD_SERVICE)
    }

    private class ConnectorDeps : NetworkStackService.ConnectorDependencies() {
        override fun checkNetworkStackCallingPermission() = Unit
    }

    private class NetworkMonitorDeps(val privateDnsBypassNetwork: Network, val dnsr: DnsResolver) :
            NetworkMonitor.Dependencies() {
        override fun getPrivateDnsBypassNetwork(network: Network?) = privateDnsBypassNetwork
        override fun sendNetworkConditionsBroadcast(context: Context, broadcast: Intent) = Unit
        override fun getDnsResolver() = dnsr
        override fun getInitialReevaluateDelay() = TEST_INITIAL_REEVALUATE_DELAY_MS
        override fun getIgnoreReevaluateAttempts() = 0
    }

    private inner class TestNetworkStackConnector(context: Context) :
            NetworkStackService.NetworkStackConnector(context, ConnectorDeps()) {

        private val network = TestNetwork(TEST_NETID)
        private val mockDnsResolver = spy(DnsResolver::class.java)
        private val fakeDns = FakeDns(mockDnsResolver).also{ it.startMocking() }

        private inner class TestNetwork(netId: Int) : Network(netId) {
            override fun openConnection(url: URL): URLConnection {
                val response = instrumentationConnector.processRequest(url)

                val connection = mock(HttpURLConnection::class.java)
                doReturn(response.responseCode).`when`(connection).responseCode
                doReturn(response.contentLength).`when`(connection).contentLengthLong
                doReturn(response.redirectUrl).`when`(connection).getHeaderField("location")
                return connection
            }

            override fun getAllByName(host: String): Array<InetAddress> {
                return arrayOf(InetAddresses.parseNumericAddress("1.2.3.4"))
            }
        }

        private inner class TestNetworkMonitor(c: Context, cb: INetworkMonitorCallbacks, n: Network,
                logger: IpConnectivityLog, slog: SharedLog, deps: Dependencies,
                utils: DataStallStatsUtils) : NetworkMonitor(c, cb, n, logger, slog, deps, utils) {
            override fun sendDnsProbeWithTimeout(host: String, timeoutMs: Int): Array<InetAddress> {
                val response = instrumentationConnector.processDnsRequest(host)
                fakeDns.setAnswer(response.host, arrayOf(response.responseAddr), response.type)
                return super.sendDnsProbeWithTimeout(host, timeoutMs)
            }
        }

        override fun makeNetworkMonitor(
            network: Network,
            name: String?,
            cb: INetworkMonitorCallbacks
        ) {
            fakeDns.setAnswer(TEST_PRIVATE_DNS_HOST_NAME, arrayOf("8.8.8.8"), TYPE_A)
            fakeDns.setAnswer(TEST_PRIVATE_DNS_HOST_NAME, arrayOf("2001:db8::1"), TYPE_AAAA)

            val nm = TestNetworkMonitor(this@TestNetworkStackService, cb,
                    this.network,
                    mock(IpConnectivityLog::class.java), mock(SharedLog::class.java),
                    NetworkMonitorDeps(this.network, mockDnsResolver),
                    mock(DataStallStatsUtils::class.java))
            cb.onNetworkMonitorCreated(NetworkMonitorBinder(nm, ConnectorDeps()))
        }
    }
}