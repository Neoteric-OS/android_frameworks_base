/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.connectivity;

import android.content.Context;
import android.net.ConnectivityMetricsEvent;
import android.net.IIpConnectivityMetrics;
import android.net.INetdEventCallback;
import android.net.metrics.IpConnectivityLog;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Event buffering service for core networking and connectivity metrics.
 *
 * {@hide}
 */
final public class IpConnectivityMetrics extends SystemService {
    private static final String TAG = IpConnectivityMetrics.class.getSimpleName();
    private static final boolean DBG = false;

    private static final String SERVICE_NAME = IpConnectivityLog.SERVICE_NAME;

    // Implementation instance of IIpConnectivityMetrics.aidl.
    @VisibleForTesting
    public final Impl impl = new Impl();
    // Subservice listening to Netd events via INetdEventListener.aidl.
    @VisibleForTesting
    NetdEventListenerService mNetdListener;

    public IpConnectivityMetrics(Context ctx) {
        super(ctx);
    }

    @Override
    public void onStart() {
        if (DBG) Log.d(TAG, "onStart");
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            if (DBG) Log.d(TAG, "onBootPhase");
            mNetdListener = new NetdEventListenerService(getContext());

            publishBinderService(SERVICE_NAME, impl);
            publishBinderService(mNetdListener.SERVICE_NAME, mNetdListener);

        }
    }

    public final class Impl extends IIpConnectivityMetrics.Stub {
        // Dump and flushes the metrics event buffer in base64 encoded serialized proto output.
        static final String CMD_FLUSH = "flush";
        // Dump the rolling buffer of metrics event in human readable proto text format.
        static final String CMD_PROTO = "proto";
        // Dump the rolling buffer of metrics event in proto wire format. See usage() of
        // frameworks/native/cmds/dumpsys/dumpsys.cpp for details.
        static final String CMD_PROTO_BIN = "--proto";
        // Dump the rolling buffer of metrics event and pretty print events using a human readable
        // format. Also print network dns/connect statistics and default network event time series.
        static final String CMD_LIST = "list";
        // By default any other argument will fall into the default case which is the equivalent
        // of calling both the "list" and "ipclient" commands. This includes most notably bug
        // reports collected by dumpsys.cpp with the "-a" argument.
        static final String CMD_DEFAULT = "";

        @Override
        public int logEvent(ConnectivityMetricsEvent event) {
            return 0;
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        }

        private void enforceDumpPermission() {
            enforcePermission(android.Manifest.permission.DUMP);
        }

        private void enforcePermission(String what) {
            getContext().enforceCallingOrSelfPermission(what, "IpConnectivityMetrics");
        }

        private void enforceNetdEventListeningPermission() {
            final int uid = Binder.getCallingUid();
            if (uid != Process.SYSTEM_UID) {
                throw new SecurityException(String.format("Uid %d has no permission to listen for"
                        + " netd events.", uid));
            }
        }

        @Override
        public boolean addNetdEventCallback(int callerType, INetdEventCallback callback) {
            enforceNetdEventListeningPermission();
            if (mNetdListener == null) {
                return false;
            }
            return mNetdListener.addNetdEventCallback(callerType, callback);
        }

        @Override
        public boolean removeNetdEventCallback(int callerType) {
            enforceNetdEventListeningPermission();
            if (mNetdListener == null) {
                // if the service is null, we aren't registered anyway
                return true;
            }
            return mNetdListener.removeNetdEventCallback(callerType);
        }
    };
}
