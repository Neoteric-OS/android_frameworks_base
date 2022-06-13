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

package android.net.sntp;

import android.net.EventLogTags;
import android.net.TrafficStats;
import android.os.SystemClock;
import android.util.Log;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.TrafficStatsConstants;
import com.android.time.client.base.Duration;
import com.android.time.client.base.InstantSource;
import com.android.time.client.base.Logger;
import com.android.time.client.base.Network;
import com.android.time.client.base.PlatformInstantSource;
import com.android.time.client.base.PlatformTicker;
import com.android.time.client.base.ServerAddress;
import com.android.time.client.base.Ticker;
import com.android.time.client.base.Ticks;
import com.android.time.client.sntp.BasicSntpClient;
import com.android.time.client.sntp.NtpServerNotReachableException;
import com.android.time.client.sntp.SntpClient;
import com.android.time.client.sntp.SntpNetworkListener;
import com.android.time.client.sntp.SntpResult;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * {@hide}
 *
 * An SNTP client class that uses the {@code com.android.time.client} library. This can be inlined
 * into {@link SntpClient} when {@link OldSntpClient} has been deleted.
 *
 * Sample usage:
 * <pre>NewSntpClient client = new NewSntpClient();
 * if (client.requestTime("time.foo.com", 123, 5000, network)) {
 *     long now = client.getNtpTime() + SystemClock.elapsedRealtime() - client.getNtpTimeReference();
 * }
 * </pre>
 */
public class NewSntpClient implements SntpClientDelegate {
    private static final String TAG = "NewSntpClient";
    private static final boolean DBG = true;

    private final InstantSource mInstantSource;
    private final Random mRandom;

    private SntpResult mLastSntpResult;

    public NewSntpClient() {
        this(PlatformInstantSource.instance(), defaultRandom());
    }

    @VisibleForTesting
    public NewSntpClient(InstantSource instantSource, Random random) {
        mInstantSource = Objects.requireNonNull(instantSource);
        mRandom = Objects.requireNonNull(random);
    }

    @Override
    public boolean requestTime(String host, int port, int timeout, android.net.Network network) {
        ServerAddress serverAddress = new ServerAddress(host, port);
        Duration responseTimeout = Duration.ofMillis(timeout);
        BasicSntpClient.ClientConfig clientConfig = new BasicSntpClient.ClientConfig() {
            @Override
            public ServerAddress serverAddress() {
                return serverAddress;
            }

            @Override
            public Duration responseTimeout() {
                return responseTimeout;
            }
        };

        SntpClient sntpClient = new BasicSntpClient.Builder()
                .setLogger(AndroidLogger.sInstance)
                .setClientTicker(AndroidTicker.sInstance)
                .setClientInstantSource(mInstantSource)
                .setClientDataMinimizationEnabled(false)
                .setRandom(mRandom)
                .setNetwork(new AndroidNetwork(network))
                .setNetworkListener(AndroidNetworkListener.sInstance)
                .setClientConfig(clientConfig)
                .build();

        final int oldTag = TrafficStats.getAndSetThreadStatsTag(
                TrafficStatsConstants.TAG_SYSTEM_NTP);
        try {
            SntpResult sntpResult = sntpClient.requestInstant();

            long roundTripTimeMillis = sntpResult.getRoundTripDuration().toMillis();
            long clockOffsetMillis = sntpResult.getClientOffset().toMillis();
            EventLogTags.writeNtpSuccess(
                    sntpResult.getServerInetAddress().toString(),
                    roundTripTimeMillis,
                    clockOffsetMillis);
            if (DBG) {
                Log.d(TAG, "round trip: " + roundTripTimeMillis + "ms, "
                        + "clock offset: " + clockOffsetMillis + "ms");
            }

            mLastSntpResult = sntpResult;
            return true;
        } catch (NtpServerNotReachableException e) {
            if (DBG) Log.d(TAG, "request time failed");
            return false;
        } finally {
            TrafficStats.setThreadStatsTag(oldTag);
        }
    }

    /**
     * Returns the offset calculated to apply to the client clock to arrive at {@link #getNtpTime()}
     */
    @VisibleForTesting
    public long getClockOffset() {
        return mLastSntpResult == null ? 0 : mLastSntpResult.getClientOffset().toMillis();
    }

    @Override
    public long getNtpTime() {
        return mLastSntpResult == null ? 0 : mLastSntpResult.getResultInstant().toEpochMilli();
    }

    @Override
    public long getNtpTimeReference() {
        if (mLastSntpResult == null) {
            return 0;
        }
        // This is a slightly convoluted way of getting an absolute value from the ticker, but
        // it ensures milliseconds without needing to care about the ticker precision.
        AndroidTicker ticker = AndroidTicker.sInstance;
        return ticker.durationBetween(ticker.ZERO_TICKS, mLastSntpResult.getResultTicks()).toMillis();
    }

    @Override
    public long getRoundTripTime() {
        return mLastSntpResult == null ? 0 : mLastSntpResult.getRoundTripDuration().toMillis();
    }

    private static class AndroidLogger implements Logger {
        final static AndroidLogger sInstance = new AndroidLogger();

        @Override
        public boolean isLoggingFine() {
            return Log.isLoggable(TAG, Log.DEBUG);
        }

        @Override
        public void fine(String msg) {
            Log.d(TAG, msg);
        }

        @Override
        public void fine(String msg, Throwable e) {
            Log.d(TAG, msg, e);
        }

        @Override
        public void warning(String msg) {
            Log.w(TAG, msg);
        }

        @Override
        public void warning(String msg, Throwable e) {
            Log.w(TAG, msg, e);
        }
    }

    private static class AndroidNetwork implements Network {

        private final android.net.Network mNetwork;

        public AndroidNetwork(android.net.Network network) {
            mNetwork = Objects.requireNonNull(network);
        }

        @Override
        public InetAddress[] getAllByName(String hostString) throws UnknownHostException {
            final android.net.Network networkForResolv = mNetwork.getPrivateDnsBypassingCopy();
            try {
                return networkForResolv.getAllByName(hostString);
            } catch (UnknownHostException e) {
                Log.w(TAG, "Unknown host: " + hostString);
                throw e;
            }
        }

        @Override
        public UdpSocket createUdpSocket() throws IOException {
            UdpSocketImpl udpSocket = new UdpSocketImpl();
            mNetwork.bindSocket(udpSocket.delegate);
            return udpSocket;
        }

        private static class UdpSocketImpl implements Network.UdpSocket {

            final DatagramSocket delegate;

            UdpSocketImpl() throws SocketException {
                this(new DatagramSocket());
            }

            UdpSocketImpl(DatagramSocket delegate) {
                this.delegate = delegate;
            }

            @Override
            public SocketAddress getLocalSocketAddress() {
                return delegate.getLocalSocketAddress();
            }

            @Override
            public void setSoTimeout(Duration timeout) throws SocketException {
                long timeoutMillis = timeout.toMillis();
                if (timeoutMillis < 0 || timeoutMillis > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid timeout: " + timeout);
                }
                delegate.setSoTimeout((int) timeoutMillis);
            }

            @Override
            public void send(DatagramPacket packet) throws IOException {
                delegate.send(packet);
            }

            @Override
            public void receive(DatagramPacket packet) throws IOException {
                delegate.receive(packet);
            }

            @Override
            public void close() {
                delegate.close();
            }

            @Override
            public boolean isClosed() {
                return delegate.isClosed();
            }
        }
    }

    private static class AndroidNetworkListener implements SntpNetworkListener {
        final static AndroidNetworkListener sInstance = new AndroidNetworkListener();

        @Override
        public void serverLookupFailure(String serverName, Throwable e) {
            Log.w(TAG, "Unknown host: " + serverName);
            EventLogTags.writeNtpFailure(serverName, e.toString());
        }

        @Override
        public void success(InetAddress inetAddress, int port) {
        }

        @Override
        public void failure(InetAddress address, int port, Exception e) {
            EventLogTags.writeNtpFailure(address.toString(), e.toString());
            if (DBG) Log.d(TAG, "request time failed: " + e);
        }
    }

    /**
     * A millisecond-precision Ticker for Android.
     * java-time-client's Android PlatformTicker uses nanos.
     * TODO Switch to nanos.
     */
    private static class AndroidTicker extends Ticker {

        public final Ticks ZERO_TICKS = Ticks.fromTickerValue(this, 0);

        final static AndroidTicker sInstance = new AndroidTicker();

        @Override
        public Ticks ticks() {
            return Ticks.fromTickerValue(this, SystemClock.elapsedRealtime());
        }

        @Override
        public Duration durationBetween(Ticks start, Ticks end) throws IllegalArgumentException {
            return Duration.ofMillis(Ticker.incrementsBetween(start, end));
        }
    }

    private static Random defaultRandom() {
        Random random;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            // This should never happen.
            Slog.wtf(TAG, "Unable to access SecureRandom", e);
            random = new Random(System.currentTimeMillis());
        }
        return random;
    }
}
