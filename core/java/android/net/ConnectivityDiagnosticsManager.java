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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
     * @hide
     */
    public ConnectivityDiagnosticsManager() {}

    /** Class that includes connectivity information for a specific Network at a specific time. */
    public static class ConnectivityReport {
        /** Epoch timestamp for the report in milliseconds */
        public final long reportEpochTimestampMillis;

        /** LinkProperties avilable on the Network at the reported timestamp */
        @NonNull public final LinkProperties linkProperties;

        /** NetworkCapabilities avilable on the Network at the reported timestamp */
        @NonNull public final NetworkCapabilities networkCapabilities;

        /** Bundle containing addtional info about the report */
        @NonNull public final Bundle additionalInfo;

        /**
         * @hide
         */
        public ConnectivityReport(
                long reportEpochTimestampMillis,
                @NonNull LinkProperties linkProperties,
                @NonNull NetworkCapabilities networkCapabilities,
                @NonNull Bundle additionalInfo) {
            this.reportEpochTimestampMillis = reportEpochTimestampMillis;
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
         * Called when the platform completes a data connectivity check. This will also be invoked
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
         * @param epochTimestampMillis The Epoch timestamp in milliseconds when the data stall was
         *     detected
         * @param detectionMethod The {@link DetectionMethod} used to identify the data stall
         * @param stallDetail A Bundle containing additional information on the data stall
         */
        void onDataStalled(
                Network network,
                long epochTimestampMillis,
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
     * <p>Each register() call <b>MUST</b> use a unique ConnectivityDiagnosticsCallback instance.
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
}
