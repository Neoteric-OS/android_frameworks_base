/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.net;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.IIpConnectivityMetrics;
import android.net.INetdEventCallback;
import android.net.INetworkTransparencyService;
import android.net.metrics.IpConnectivityLog;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import com.android.server.SystemService;
import com.android.server.net.BaseNetdEventCallback;

/** @hide */
public class NetworkTransparencyService extends INetworkTransparencyService.Stub {
  private static final String TAG = NetworkTransparencyService.class.getSimpleName();

  public static class Lifecycle extends SystemService {
    private NetworkTransparencyService mService;

    public Lifecycle(Context context) {
      super(context);
    }

    @Override
    public void onStart() {
      Slog.d(TAG, "onStart");
      mService = new NetworkTransparencyService(getContext());
      publishBinderService(Context.NETWORK_TRANSPARENCY_SERVICE, mService);
    }

    @Override
    public void onBootPhase(@BootPhase int phase) {
      Slog.d(TAG, "onBootPhase: " + phase);
      if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY) {
        mService.start();
      }
    }
  }

  @NonNull private final PackageManager mPackageManager;

  @NonNull
  private final INetdEventCallback mNetdEventCallback =
      new BaseNetdEventCallback() {
        @Override
        public void onDnsEvent(
            int netId,
            int eventType,
            int returnCode,
            String hostname,
            String[] ipAddresses,
            int ipAddressesCount,
            long timestamp,
            int uid) {
          Slog.i(TAG, "onDnsEvent: " + mPackageManager.getNameForUid(uid) + " queried " + hostname);
        }
      };

  public NetworkTransparencyService(Context context) {
    mPackageManager = context.getPackageManager();
  }

  @Override
  public void start() {
    Slog.d(TAG, "start");
    IIpConnectivityMetrics ipConnectivityMetrics =
        IIpConnectivityMetrics.Stub.asInterface(
            ServiceManager.getService(IpConnectivityLog.SERVICE_NAME));
    try {
      if (!ipConnectivityMetrics.addNetdEventCallback(
          INetdEventCallback.CALLBACK_CALLER_NETWORK_TRANSPARENCY, mNetdEventCallback)) {
        Slog.e(TAG, "Failed to register netd callbacks");
      }
    } catch (RemoteException e) {
      Slog.e(TAG, "Failed to register netd callbacks", e);
    }
  }
}
