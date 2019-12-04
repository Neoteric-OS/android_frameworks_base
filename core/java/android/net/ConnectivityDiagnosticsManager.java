/**
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
 * limitations under the License.
 */

package android.net;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Bundle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Class that provides utilities for collecting network connectivity diagnostics information.
 * Connectivity information is made available through triggerable diagnostics tools and by listening
 * to System validations. Some diagnostics information may be permissions-restricted.
 *
 * <p>ConnectivityDiagnosticsManager is intended for use by applications offering network
 * connectivity on a user device. These tools will provide several mechanisms for these applications
 * to be alerted to network conditions as well as diagnose potential network issues themselves.
 *
 * <p>The primary responsibilities of this class are to:
 *
 * <ul>
 *   <li>Perform traceroute diagnostics to given IP addresses
 *   <li>Allow permissioned applications to register and unregister callbacks for network event
 *       notifications
 *   <li>Invoke callbacks for network event notifications, including:
 *       <ul>
 *         <li>Network validations
 *         <li>Data stalls
 *         <li>Connectivity reports from applications
 *       </ul>
 * </ul>
 */
public class ConnectivityDiagnosticsManager {
    public static final int DNS_EVENTS = 1;
    public static final int TCP_METRICS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DNS_EVENTS, TCP_METRICS})
    public @interface DetectionMethod {}

    /**
     * {@hide}
     */
    public ConnectivityDiagnosticsManager() {}

    /** Class that includes connectivity information for a specific Network at a specific time. */
    public static class ConnectivityReport {
        public final long reportTimestampMillis;
        @NonNull public final LinkProperties linkProperties;
        @NonNull public final NetworkCapabilities networkCapabilities;
        @NonNull public final Bundle additionalInfo;

        /**
         * @param reportTimestampMillis The System timestamp for when this report was generated
         * @param linkProperties The LinkProperties for the network
         * @param networkCapabilities The NetworkCapabilities for the network
         * @param additionalInfo A Bundle containing additional information for the report
         *
         * @hide
         */
        public ConnectivityReport(
                long reportTimestampMillis,
                @NonNull LinkProperties linkProperties,
                @NonNull NetworkCapabilities networkCapabilities,
                @NonNull Bundle additionalInfo) {
            this.reportTimestampMillis = reportTimestampMillis;
            this.linkProperties = linkProperties;
            this.networkCapabilities = networkCapabilities;
            this.additionalInfo = additionalInfo;
        }
    }

    /**
     * Abstract base class for Connectivity Diagnostics callbacks. Used for notifications about
     * network connectivity events. Must be extended by applications wanting notifications.
     */
    public abstract static class ConnectivityDiagnosticsCallback {
        /**
         * Called when the platform performs data connectivity checks. This will also be invoked
         * upon registration with the latest report.
         *
         * @param network The Network for which the ConnectivityReport was generated
         * @param report The ConnectivityReport containing information for the specified network
         */
        void onConnectivityReport(Network network, ConnectivityReport report) {}

        /**
         * Called when the platform detects a data stall.
         *
         * @param network The Network for which the data stall is being reported
         * @param timestampMillis The System timestamp in milliseconds when the data stall was
         *     detected
         * @param detectionMethod The {@link DetectionMethod} used to identify the data stall
         * @param stallDetail A Bundle containing additional information on the data stall
         */
        void onDataStalled(
                Network network,
                long timestampMillis,
                @DetectionMethod int detectionMethod,
                Bundle stallDetail) {}

        /**
         * Called when any app reports connectivity to the System.
         *
         * @param network The Network for which connectivity has been reported
         * @param hasConnectivity The connectivity reported to the System
         */
        void onNetworkConnectivityReported(Network network, boolean hasConnectivity) {}
    }

    /**
     * Registers a ConnectivityDiagnosticsCallback with the System.
     *
     * <p>In order to register or receive callbacks, the caller must either:
     *
     * <ul>
     *   <li>have carrier privileges (on any active subscription), or
     *   <li>be the currently-running (active) VPN, or
     *   <li>have the NETWORK_STACK permission
     * </ul>
     *
     * <p>Each register() call must use a unique ConnectivityDiagnosticsCallback instance.
     *
     * @param request The NetworkRequest that will be used to match with Networks for which
     *     callbacks will be fired
     * @param callback The ConnectivityDiagnosticsCallback that the caller wants registered with the
     *     System
     * @param e The Executor to be used for running the callback method invocations
     * @throws IllegalArgumentException if the same callback instance is registered with multiple
     *     NetworkRequests
     * @throws SecurityException if the caller does not have appropriate permissions.
     */
    public void registerConnectivityDiagnosticsCallback(
            @NonNull NetworkRequest request,
            @NonNull ConnectivityDiagnosticsCallback callback,
            @NonNull Executor e) {
        // TODO(b/143187964): implement ConnectivityDiagnostics functionality
        throw new UnsupportedOperationException("registerCallback() not supported yet");
    }

    /**
     * Unregisters a ConnectivityDiagnosticsCallback with the System.
     *
     * <p>If the given callback is not currently registered with the System, this operation will be
     * a no-op.
     *
     * @param callback The ConnectivityDiagnosticsCallback to be unregistered from the System.
     */
    public void unregisterConnectivityDiagnosticsCallback(
            @NonNull ConnectivityDiagnosticsCallback callback) {
        // TODO(b/143187964): implement ConnectivityDiagnostics functionality
        throw new UnsupportedOperationException("registerCallback() not supported yet");
    }

    /**
     * Base class for Route Diagnostics callbacks.
     *
     * <p>Used to provide information about a {@code traceroute} request to a specific IP address.
     * Must be extended by applications wanting route diagnostics information.
     */
    public abstract static class RouteDiagnosticsCallback {
        /** HopInfo represents the route diagnostics information collected for some TTL. */
        public static class HopInfo {
            @NonNull public final List<HopResponse> responses;
            public final int numProbesSent;
            public final int ttl;

            /**
             * @param responses The List<HopResponse> for this TTL
             * @param numProbesSent The number of probes sent out for this TTL
             * @param ttl The TTL (time to live) represented by this HopInfo
             *
             * @hide
             */
            public HopInfo(@NonNull List<HopResponse> responses, int numProbesSent, int ttl) {
                this.responses = responses;
                this.numProbesSent = numProbesSent;
                this.ttl = ttl;
            }
        }

        /**
         * HopResponse represents a single node in the Network.
         *
         * <p>The metrics for average RTT and number of probes received are for a specific TTL
         * value.
         */
        public static class HopResponse {
            @NonNull public final InetAddress address;
            public final double averageRttMillis;
            public final int numProbesReceived;

            /**
             * @param address The InetAddress for this HopResponse
             * @param averageRttMillis The average RTT (round trip time) in milliseconds
             * @param numProbesReceived The number of probes responded to by this node
             *
             * @hide
             */
            public HopResponse(
                    @NonNull InetAddress address, double averageRttMillis, int numProbesReceived) {
                this.address = address;
                this.averageRttMillis = averageRttMillis;
                this.numProbesReceived = numProbesReceived;
            }
        }

        /**
         * Called when route diagnostics have been completed to the specified host.
         *
         * @param network The Network on which the route diagnostics were collected
         * @param host The InetAddress to which route diagnostics were collected
         * @param route The List<HopInfo> route that was determined to the host
         */
        public void onRouteDiagnosticsComplete(
                @NonNull Network network,
                @NonNull InetAddress host,
                @NonNull List<HopInfo> route) {}

        /**
         * Called when a critical error is encountered while computing route diagnostics.
         *
         * @param network The Network on which the route diagnostics were attempted
         * @param host The InetAddress to which route diagnostics were attempted
         * @param cause The Exception that caused the route diagnostics to fail
         */
        public void onError(
                @NonNull Network network, @NonNull InetAddress host, @NonNull Exception cause) {}
    }

    /**
     * Class used to configure the route diagnostics performed by the system.
     *
     * <p>The default values are those used by {@code traceroute}:
     *
     * <ul>
     *   <li>Max TTL: 30
     *   <li>Probes per Hop: 3
     *   <li>Packet Size: 80 Bytes
     *   <li>Timeout Seconds: 5 s
     * </ul>
     */
    public static class RouteDiagnosticsOptions {
        private static final int DEFAULT_MAX_TTL = 30;
        private static final int DEFAULT_PROBES_PER_HOP = 3;

        // IPv4: 60B, IPv6: 80B
        private static final int DEFAULT_PACKET_SIZE_BYTES = 80;
        private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

        /** ICMP Header is 8 Bytes */
        public static final int MINIMUM_PACKET_SIZE_BYTES = 8;

        /**
         * @hide
         */
        static final RouteDiagnosticsOptions DEFAULT = new Builder().build();

        public final int maxTtl;
        public final int probesPerHop;
        public final int packetSizeBytes;
        public final int timeoutMillis;

        private RouteDiagnosticsOptions(
                int maxTtl, int probesPerHop, int packetSizeBytes, int timeoutMillis) {
            this.maxTtl = maxTtl;
            this.probesPerHop = probesPerHop;
            this.packetSizeBytes = packetSizeBytes;
            this.timeoutMillis = timeoutMillis;
        }

        /**
         * Class to be used for configuring a RouteDiagnosticsOptions instance.
         *
         * <p>Each configurable value will use {@code traceroute}'s default value unless overridden.
         */
        public static class Builder {
            private int mMaxTtl = DEFAULT_MAX_TTL;
            private int mProbesPerHop = DEFAULT_PROBES_PER_HOP;
            private int mPacketSizeBytes = DEFAULT_PACKET_SIZE_BYTES;
            private int mTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;

            /** Constructs a new RouteDiagnosticsOptions.Builder. */
            public Builder() {}

            /**
             * Sets the maximum TTL (inclusive).
             *
             * @param maxTtl The maximum TTL to be used
             * @return Builder this, to facilitate chaining
             * @throws IllegalArgumentException iff maxTtl < 1
             */
            @NonNull
            public Builder setMaxTtl(int maxTtl) {
                if (maxTtl < 1) {
                    throw new IllegalArgumentException("Max TTL must be at least 1");
                }
                this.mMaxTtl = maxTtl;
                return this;
            }

            /**
             * Sets the probes per hop.
             *
             * @param probesPerHop The number of probes to be sent for each TTL
             * @return Builder this, to facilitate chaining
             * @throws IllegalArgumentException iff probesPerHop < 1
             */
            @NonNull
            public Builder setProbesPerHop(int probesPerHop) {
                if (probesPerHop < 1) {
                    throw new IllegalArgumentException("Probes per Hop must be at least 1");
                }
                this.mProbesPerHop = probesPerHop;
                return this;
            }

            /**
             * Sets the packet size for each outgoing probe.
             *
             * @param packetSizeBytes The packet size (in Bytes) to be used for each outgoing probe
             * @return Builder this, to facilitate chaining.
             * @throws IllegalArgumentException iff packetSizeBytes < {@link
             *     RouteDiagnosticsOptions#MINIMUM_PACKET_SIZE_BYTES}
             */
            @NonNull
            public Builder setPacketSizeBytes(int packetSizeBytes) {
                if (packetSizeBytes < MINIMUM_PACKET_SIZE_BYTES) {
                    throw new IllegalArgumentException(
                            "Packet size (in bytes) must be at least "
                                    + MINIMUM_PACKET_SIZE_BYTES
                                    + " Bytes");
                }
                this.mPacketSizeBytes = packetSizeBytes;
                return this;
            }

            /**
             * Sets the timeout to be used for each outgoing probe.
             *
             * <p>The timeout will be the maximum wait time allowed for a probe response. That is,
             * the network node's response message must be received by at most timeoutMillis ms
             * after the probe is sent.
             *
             * @param timeoutMillis The timeout (in milliseconds) to be used for each outgoing probe
             * @return Builder this, to facilitate chaining.
             * @throws IllegalArgumentException iff timeoutMillis < 500
             */
            @NonNull
            public Builder setTimeoutMillis(int timeoutMillis) {
                if (timeoutMillis < 500) {
                    throw new IllegalArgumentException(
                            "Timeout (in milliseconds) must be at least 500");
                }
                this.mTimeoutMillis = timeoutMillis;
                return this;
            }

            /**
             * Constructs and returns a RouteDiagnosticsOptions with the configurations applied to
             * this Builder.
             *
             * @return the RouteDiagnosticsOptions constructed by this Builder
             */
            @NonNull
            public RouteDiagnosticsOptions build() {
                return new RouteDiagnosticsOptions(
                        mMaxTtl, mProbesPerHop, mPacketSizeBytes, mTimeoutMillis);
            }
        }
    }

    /**
     * Request diagnostic information be collected for a particular IP address or domain.
     *
     * <p>Every call to {@link ConnectivityDiagnosticsManager#requestRouteDiagnostics} will result
     * in either {@link RouteDiagnosticsCallback#receiveRouteDiagnostics} or {@link
     * RouteDiagnosticsCallback#onError} being invoked.
     *
     * @param network The Network to be used for route diagnostics
     * @param host The InetAddress to be targeted for route diagnostics
     * @param e The Executor on which the route diagnostics will be computed
     * @param callback The RouteDiagnosticsCallback to be invoked
     */
    public void requestRouteDiagnostics(
            @NonNull Network network,
            @NonNull InetAddress host,
            @NonNull Executor e,
            @NonNull RouteDiagnosticsCallback callback) {
        requestRouteDiagnostics(network, host, RouteDiagnosticsOptions.DEFAULT, e, callback);
    }

    /**
     * Request diagnostic information be collected for a particular IP address or domain.
     *
     * <p>Every call to {@link ConnectivityDiagnosticsManager#requestRouteDiagnostics} will result
     * in either {@link RouteDiagnosticsCallback#receiveRouteDiagnostics} or {@link
     * RouteDiagnosticsCallback#onError} being invoked.
     *
     * @param network The Network to be used for route diagnostics
     * @param host The InetAddress to be targeted for route diagnostics
     * @param routeDiagnosticsOptions The options to be used for collecting the route diagnostics
     * @param e The Executor on which the route diagnostics will be computed
     * @param callback The RouteDiagnosticsCallback to be invoked
     */
    public void requestRouteDiagnostics(
            @NonNull Network network,
            @NonNull InetAddress host,
            @NonNull RouteDiagnosticsOptions routeDiagnosticsOptions,
            @NonNull Executor e,
            @NonNull RouteDiagnosticsCallback callback) {
        // TODO(b/143189134): implement Route Diagnostics functionality
        throw new UnsupportedOperationException("requestRouteDiagnostics() not supported yet");
    }
}
