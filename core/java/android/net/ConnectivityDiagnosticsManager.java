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
 * limitations under the License.
 */

package android.net;

import android.annotation.NonNull;

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
    /** @hide */
    public ConnectivityDiagnosticsManager() {}

    /**
     * Base class for Route Diagnostics callbacks.
     *
     * <p>Used to provide information about a {@code traceroute} request to a specific IP address.
     * Must be extended by applications wanting route diagnostics information.
     */
    public abstract static class RouteDiagnosticsCallback {
        /** HopInfo represents the route diagnostics information collected for some TTL. */
        public static class HopInfo {
            /** HopResponses received for this TTL */
            @NonNull public final List<HopResponse> responses;

            /** The number of probes sent out for this TTL */
            public final int numProbesSent;

            /** The TTL (time to live) represented by this HopInfo */
            public final int ttl;

            /** @hide */
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
            /** The InetAddress for this HopResponse */
            @NonNull public final InetAddress address;

            /** The Average RTT (round trip time) in milliseconds */
            public final double averageRttMillis;

            /** The number of probes responded to by this node */
            public final int numProbesReceived;

            /** @hide */
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
    public static class RouteDiagnosticsParams {
        private static final int DEFAULT_MAX_TTL = 30;
        private static final int DEFAULT_PROBES_PER_HOP = 3;

        // 80B is the default for traceroute to an IPv6 address. IPv4 uses 60B packets. To
        // accommodate both protocols, the IPv6 packet size is used.
        private static final int DEFAULT_PACKET_SIZE_BYTES = 80;
        private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

        /**
         * IPv4 headers must be at least 20B. IPv6 headers are 40B. ICMP headers are always 8B. To
         * simplify this check, the minimum allowed packet size must accommodate both IPv4 and IPv6,
         * so 48B is used.
         */
        public static final int MINIMUM_PACKET_SIZE_BYTES = 48;

        /** @hide */
        static final RouteDiagnosticsParams DEFAULT = new Builder().build();

        public final int maxTtl;
        public final int probesPerHop;
        public final int packetSizeBytes;
        public final int timeoutMillis;

        private RouteDiagnosticsParams(
                int maxTtl, int probesPerHop, int packetSizeBytes, int timeoutMillis) {
            this.maxTtl = maxTtl;
            this.probesPerHop = probesPerHop;
            this.packetSizeBytes = packetSizeBytes;
            this.timeoutMillis = timeoutMillis;
        }

        /**
         * Class to be used for configuring a RouteDiagnosticsParams instance.
         *
         * <p>Each configurable value will use {@code traceroute}'s default value unless overridden.
         */
        public static class Builder {
            private int mMaxTtl = DEFAULT_MAX_TTL;
            private int mProbesPerHop = DEFAULT_PROBES_PER_HOP;
            private int mPacketSizeBytes = DEFAULT_PACKET_SIZE_BYTES;
            private int mTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;

            /** Constructs a new RouteDiagnosticsParams.Builder. */
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
             * <p>The minimum allowed packet size is {@link
             * RouteDiagnosticsParams#MINIMUM_PACKET_SIZE_BYTES}. This is the minimum number of
             * bytes necessary to send an ICMP header over IPv6.
             *
             * @param packetSizeBytes The packet size (in Bytes) to be used for each outgoing probe
             * @return Builder this, to facilitate chaining.
             * @throws IllegalArgumentException iff packetSizeBytes < {@link
             *     RouteDiagnosticsParams#MINIMUM_PACKET_SIZE_BYTES}
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
             * @throws IllegalArgumentException iff timeoutMillis < 1
             */
            @NonNull
            public Builder setTimeoutMillis(int timeoutMillis) {
                if (timeoutMillis < 1) {
                    throw new IllegalArgumentException(
                            "Timeout (in milliseconds) must be at least 1 ms");
                }
                this.mTimeoutMillis = timeoutMillis;
                return this;
            }

            /**
             * Constructs and returns a RouteDiagnosticsParams with the configurations applied to
             * this Builder.
             *
             * @return the RouteDiagnosticsParams constructed by this Builder
             */
            @NonNull
            public RouteDiagnosticsParams build() {
                return new RouteDiagnosticsParams(
                        mMaxTtl, mProbesPerHop, mPacketSizeBytes, mTimeoutMillis);
            }
        }
    }

    /**
     * Request diagnostic information be collected for a particular IP address or domain.
     *
     * <p>Every call to {@link ConnectivityDiagnosticsManager#requestRouteDiagnostics} will result
     * in either {@link RouteDiagnosticsCallback#onRouteDiagnosticsComplete} or {@link
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
        requestRouteDiagnostics(network, host, RouteDiagnosticsParams.DEFAULT, e, callback);
    }

    /**
     * Request diagnostic information be collected for a particular IP address or domain.
     *
     * <p>Every call to {@link ConnectivityDiagnosticsManager#requestRouteDiagnostics} will result
     * in either {@link RouteDiagnosticsCallback#onRouteDiagnosticsComplete} or {@link
     * RouteDiagnosticsCallback#onError} being invoked.
     *
     * @param network The Network to be used for route diagnostics
     * @param host The InetAddress to be targeted for route diagnostics
     * @param routeDiagnosticsParams The configs to be used for collecting the route diagnostics
     * @param e The Executor on which the route diagnostics will be computed
     * @param callback The RouteDiagnosticsCallback to be invoked
     */
    public void requestRouteDiagnostics(
            @NonNull Network network,
            @NonNull InetAddress host,
            @NonNull RouteDiagnosticsParams routeDiagnosticsParams,
            @NonNull Executor e,
            @NonNull RouteDiagnosticsCallback callback) {
        // TODO(b/143189134): implement Route Diagnostics functionality
        throw new UnsupportedOperationException("requestRouteDiagnostics() not supported yet");
    }
}
