/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net;

import android.compat.annotation.UnsupportedAppUsage;
import android.net.sntp.NewSntpClient;
import android.net.sntp.OldSntpClient;
import android.net.sntp.SntpClientDelegate;
import android.util.Log;

/**
 * {@hide}
 *
 * Simple SNTP client class for retrieving network time.
 *
 * Sample usage:
 * <pre>SntpClient client = new SntpClient();
 * if (client.requestTime("time.foo.com", 123, 5000, network)) {
 *     long now = client.getNtpTime() + SystemClock.elapsedRealtime() - client.getNtpTimeReference();
 * }
 * </pre>
 */
public class SntpClient {
    private static final String TAG = "SntpClient";

    public static final int STANDARD_NTP_PORT = 123;
    public static final boolean USE_NEW_IMPL = true;

    private final SntpClientDelegate mSntpClientDelegate;

    @UnsupportedAppUsage
    public SntpClient() {
        mSntpClientDelegate = USE_NEW_IMPL ? new NewSntpClient() : new OldSntpClient();
    }

    /**
     * Sends an SNTP request to the given host and processes the response.
     *
     * @param host host name of the server.
     * @param port port of the server.
     * @param timeout network timeout in milliseconds. the timeout doesn't include the DNS lookup
     *                time, and it applies to each individual query to the resolved addresses of
     *                the NTP server.
     * @param network network over which to send the request.
     * @return true if the transaction was successful.
     */
    public boolean requestTime(String host, int port, int timeout, Network network) {
        return mSntpClientDelegate.requestTime(host, port, timeout, network);
    }

    @Deprecated
    @UnsupportedAppUsage
    public boolean requestTime(String host, int timeout) {
        Log.w(TAG, "Shame on you for calling the hidden API requestTime()!");
        return false;
    }

    /**
     * Returns the time computed from the NTP transaction.
     *
     * @return time value computed from NTP server response.
     */
    @UnsupportedAppUsage
    public long getNtpTime() {
        return mSntpClientDelegate.getNtpTime();
    }

    /**
     * Returns the reference clock value (value of SystemClock.elapsedRealtime())
     * corresponding to the NTP time.
     *
     * @return reference clock corresponding to the NTP time.
     */
    @UnsupportedAppUsage
    public long getNtpTimeReference() {
        return mSntpClientDelegate.getNtpTimeReference();
    }

    /**
     * Returns the round trip time of the NTP transaction
     *
     * @return round trip time in milliseconds.
     */
    @UnsupportedAppUsage
    public long getRoundTripTime() {
        return mSntpClientDelegate.getRoundTripTime();
    }
}
