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

import androidx.annotation.GuardedBy
import java.net.URL
import kotlin.test.fail

/**
 * An instrumentation interface for the NetworkStack that allows controlling behavior to
 * facilitate integration tests.
 */
class NetworkStackInstrumentationConnector : INetworkStackInstrumentation.Stub() {
    @GuardedBy("httpResponses")
    private val httpResponses = ArrayList<HttpResponse>()
    @GuardedBy("httpResponses")
    private val httpRequestUrls = ArrayList<String>()

    /**
     * Called when an HTTP request being processed by NetworkMonitor. Returns the response that
     * should be simulated.
     */
    fun processRequest(url: URL): HttpResponse {
        return synchronized(httpResponses) {
            val strUrl = url.toString()
            httpRequestUrls.add(strUrl)
            httpResponses.firstOrNull { it.requestUrl == strUrl }
                    ?: fail("No mocked response for request: $strUrl. " +
                            "Mocked responses are: $httpResponses")
        }
    }

    /**
     * Clear all state of this connector. This is intended for use between two tests, so all state
     * should be reset as if the connector was just created.
     */
    override fun clearAllState() {
        synchronized(httpResponses) {
            httpRequestUrls.clear()
            httpResponses.clear()
        }
    }

    /**
     * Add a response to a future HTTP request.
     *
     * <p>For any subsequent HTTP/HTTPS query, the first matching response (as per
     * [RequestInfo.equals]) will be used to mock the query response.
     */
    override fun addHttpResponse(response: HttpResponse) {
        synchronized(httpResponses) {
            httpResponses.add(response)
        }
    }

    /**
     * Get the ordered list of request URLs that have been sent by NetworkMonitor, and were answered
     * based on mock responses.
     */
    override fun getRequestUrls(): List<String> {
        synchronized(httpResponses) {
            return ArrayList(httpRequestUrls)
        }
    }
}