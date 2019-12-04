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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Bundle;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Class that provides network connectivity diagnostics information. Some diagnostics information
 * may be permissions-restricted.
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

    @IntDef({DNS_EVENTS, TCP_METRICS})
    public @interface DetectionMethod {}

    /**
     * {@hide}
     */
    public ConnectivityDiagnosticsManager() {}

    /** Class that includes connectivity information for a network Connectivity Report. */
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
     * <p>If the given callback is not currently with the System, this operation will be a no-op.
     *
     * @param callback The ConnectivityDiagnosticsCallback to be unregistered from the System.
     */
    public void unregisterConnectivityDiagnosticsCallback(
            @NonNull ConnectivityDiagnosticsCallback callback) {
        // TODO(b/143187964): implement ConnectivityDiagnostics functionality
        throw new UnsupportedOperationException("registerCallback() not supported yet");
    }

    /**
     * Base class for Route Diagnostics callbacks. Used to provide information about a {@code
     * traceroute} request to a specific IP address. Must be extended by applications wanting route
     * diagnostics information.
     */
    public abstract static class RouteDiagnosticsCallback {
        /** HopInfo represents the route diagnostics information collected for some TTL. */
        public static class HopInfo {
            // The HopResponses received for this HopInfo.
            @NonNull public final List<HopResponse> responses;

            // The number of probes sent for this HopInfo.
            public final int numProbesSent;

            // The TTL for this HopInfo.
            public final int ttl;

            /**
             * @param responses The List<HopResponse> for this TTL
             * @param numProbesSent The number of probes sent out for this TTL
             * @param ttl The TTL (time to live) represented by this HopInfo
             */
            public HopInfo(@NonNull List<HopResponse> responses, int numProbesSent, int ttl) {
                this.responses = responses;
                this.numProbesSent = numProbesSent;
                this.ttl = ttl;
            }
        }

        /**
         * HopResponse represents the route diagnostics information collected to a specific IP
         * address for a specific TTL.
         */
        public static class HopResponse {
            // The address for which the diagnostics apply.
            @NonNull public final InetAddress address;

            // The average RTT in milliseconds to reach this address.
            public final double averageRttMillis;

            // The number of probe responses received from this address.
            public final int numProbesReceived;

            /**
             * @param address The InetAddress for this HopResponse
             * @param averageRttMillis The average RTT (round trip time) in milliseconds
             * @param numProbesReceived The number of probes responded to by this
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
        protected void receiveRouteDiagnostics(
                Network network, InetAddress host, List<HopInfo> route) {}

        /**
         * Called when a critical error is encountered while computing route diagnostics.
         *
         * @param network The Network on which the route diagnostics were attempted
         * @param host The InetAddress to which route diagnostics were attempted
         * @param cause The Exception that caused the route diagnostics to fail
         */
        protected void onError(Network network, InetAddress host, Exception cause) {}
    }

    /**
     * Request diagnostic information be collected for a particular IP address or domain.
     *
     * <p>Every call to {@link ConnectivityDiagnosticsManager#requestRouteDiagnostics} will result
     * in either {@link RouteDiagnosticsCallback#receiveRouteDiagnostics} or {@link
     * RouteDiagnosticsCallback#onError} being invoked.
     *
     * @param network The {@link Network} to be used for route diagnostics
     * @param host The {@link InetAddress} to be targeted for route diagnostics
     * @param callback The {@link RouteDiagnosticsCallback} to be invoked
     * @param e The {@link Executor} on which the route diagnostics will be computed
     */
    public void requestRouteDiagnostics(
            @NonNull Network network,
            @NonNull InetAddress host,
            @NonNull RouteDiagnosticsCallback callback,
            @NonNull Executor e) {
        // TODO(b/143189134): implement Route Diagnostics functionality
        throw new UnsupportedOperationException("requestRouteDiagnostics() not supported yet");
    }
}
