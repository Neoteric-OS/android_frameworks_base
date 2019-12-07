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

package com.android.server.connectivity.tethering;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_NO_PRIVILEGED_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_UNSUPPORTED;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.IIntResultListener;
import android.net.INetworkStackConnector;
import android.net.ITetheringConnector;
import android.net.ITetheringEventCallback;
import android.net.NetworkRequest;
import android.net.dhcp.DhcpServerCallbacks;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.ip.IpServer;
import android.net.util.SharedLog;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemProperties;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Android service used to manage tethering.
 *
 * <p>The service returns a binder for the system server to communicate with the tethering.
 */
public class TetheringService extends Service {
    private static final String TAG = TetheringService.class.getSimpleName();

    private final SharedLog mLog = new SharedLog(TAG);
    private TetheringConnector mConnector;
    private Context mContext;
    private TetheringDependencies mDeps;
    private Tethering mTethering;
    private INetworkStackConnector mNetworkStackConnector;
    private UserManager mUserManager;

    @Override
    public void onCreate() {
        mLog.mark("onCreate");
        mDeps = getTetheringDependencies();
        mContext = mDeps.getContext();
        mTethering = makeTethering(mDeps);
        System.loadLibrary("tetheroffloadjni");
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
    }

    /**
     * Make a reference to Tethering object.
     */
    @VisibleForTesting
    public Tethering makeTethering(TetheringDependencies deps) {
        return new Tethering(deps);
    }

    /**
     * Create a binder connector for the system server to communicate with the tethering.
     */
    private synchronized IBinder makeConnector() {
        if (mConnector == null) {
            mConnector = new TetheringConnector(mTethering, TetheringService.this);
        }
        return mConnector;
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        mLog.mark("onBind");
        final Bundle bundle = intent.getExtras();
        // android.net.extra.NETWORKSTACK is defined in TetheringManager.EXTRA_NETWORKSTACK,
        // will use it when TetheringManager move to framwork. See aosp/1156906.
        final IBinder networkStack = bundle.getBinder("android.net.extra.NETWORKSTACK");
        mNetworkStackConnector = INetworkStackConnector.Stub.asInterface(networkStack);

        return makeConnector();
    }

    private static class TetheringConnector extends ITetheringConnector.Stub {
        private final TetheringService mService;
        private final Tethering mTethering;

        TetheringConnector(Tethering tether, TetheringService service) {
            mTethering = tether;
            mService = service;
        }

        @Override
        public void tether(String iface, String callerPkg, IIntResultListener listener) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                if (!mService.isTetheringSupported()) {
                    listener.onResult(TETHER_ERROR_UNSUPPORTED);
                    return;
                }
                listener.onResult(mTethering.tether(iface));

            } catch (RemoteException e) { }
        }

        @Override
        public void untether(String iface, String callerPkg, IIntResultListener listener) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                if (!mService.isTetheringSupported()) {
                    listener.onResult(TETHER_ERROR_UNSUPPORTED);
                    return;
                }
                listener.onResult(mTethering.untether(iface));
            } catch (RemoteException e) { }
        }

        @Override
        public void setUsbTethering(boolean enable, String callerPkg, IIntResultListener listener) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                if (!mService.isTetheringSupported()) {
                    listener.onResult(TETHER_ERROR_UNSUPPORTED);
                    return;
                }
                listener.onResult(mTethering.setUsbTethering(enable));
            } catch (RemoteException e) { }
        }

        @Override
        public void startTethering(int type, ResultReceiver receiver, boolean showProvisioningUi,
                String callerPkg) {
            if (!mService.hasTetherChangePermission()) {
                receiver.send(TETHER_ERROR_NO_PRIVILEGED_PERMISSION, null);
                return;
            }
            if (!mService.isTetheringSupported()) {
                receiver.send(TETHER_ERROR_UNSUPPORTED, null);
                return;
            }
            mTethering.startTethering(type, receiver, showProvisioningUi);
        }

        @Override
        public void stopTethering(int type, String callerPkg, IIntResultListener listener) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                if (!mService.isTetheringSupported()) {
                    listener.onResult(TETHER_ERROR_UNSUPPORTED);
                    return;
                }
                mTethering.stopTethering(type);
                listener.onResult(TETHER_ERROR_NO_ERROR);
            } catch (RemoteException e) { }
        }

        @Override
        public void requestLatestTetheringEntitlementResult(int type, ResultReceiver receiver,
                boolean showEntitlementUi, String callerPkg) {
            if (!mService.hasTetherChangePermission()) {
                receiver.send(TETHER_ERROR_NO_PRIVILEGED_PERMISSION, null);
                return;
            }
            if (!mService.isTetheringSupported()) {
                receiver.send(TETHER_ERROR_UNSUPPORTED, null);
                return;
            }
            mTethering.requestLatestTetheringEntitlementResult(type, receiver, showEntitlementUi);
        }

        @Override
        public void registerTetheringEventCallback(ITetheringEventCallback callback,
                String callerPkg) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    callback.onCallbackStopped(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                mTethering.registerTetheringEventCallback(callback);
            } catch (RemoteException e) { }
        }

        @Override
        public void unregisterTetheringEventCallback(ITetheringEventCallback callback,
                String callerPkg) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    callback.onCallbackStopped(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                mTethering.unregisterTetheringEventCallback(callback);
            } catch (RemoteException e) { }
        }

        @Override
        public void stopAllTethering(String callerPkg, IIntResultListener listener) {
            try {
                if (!mService.hasTetherChangePermission()) {
                    listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
                    return;
                }
                if (!mService.isTetheringSupported()) {
                    listener.onResult(TETHER_ERROR_UNSUPPORTED);
                    return;
                }
                mTethering.untetherAll();
            } catch (RemoteException e) { }
        }

        @Override
        public void isTetheringSupported(String callerPkg, IIntResultListener listener) {
            try {
                if (mService.hasTetherChangePermission()) {
                    final int ret = mService.isTetheringSupported() ? TETHER_ERROR_NO_ERROR
                            : TETHER_ERROR_UNSUPPORTED;
                    listener.onResult(ret);
                    return;
                }
                listener.onResult(TETHER_ERROR_NO_PRIVILEGED_PERMISSION);
            } catch (RemoteException e) { }
        }

        @Override
        protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter writer,
                    @Nullable String[] args) {
            mTethering.dump(fd, writer, args);
        }
    }

    // if ro.tether.denied = true we default to no tethering
    // gservices could set the secure setting to 1 though to enable it on a build where it
    // had previously been turned off.
    private boolean isTetheringSupported() {
        final int defaultVal =
                SystemProperties.get("ro.tether.denied").equals("true") ? 0 : 1;
        final boolean tetherSupported = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.TETHER_SUPPORTED, defaultVal) != 0;
        final boolean tetherEnabledInSettings = tetherSupported
                && !mUserManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_TETHERING);

        return tetherEnabledInSettings && mTethering.hasTetherableConfiguration();
    }

    private boolean hasTetherChangePermission() {
        return checkCallingOrSelfPermission(
                android.Manifest.permission.TETHER_PRIVILEGED) == PERMISSION_GRANTED;
    }

    /**
     * An injection method for testing.
     */
    @VisibleForTesting
    public TetheringDependencies getTetheringDependencies() {
        if (mDeps == null) {
            mDeps = new TetheringDependencies() {
                @Override
                public NetworkRequest getDefaultNetworkRequest() {
                    ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                            Context.CONNECTIVITY_SERVICE);
                    return cm.getDefaultRequest();
                }

                @Override
                public Looper getTetheringLooper() {
                    final HandlerThread tetherThread = new HandlerThread("android.tethering");
                    tetherThread.start();
                    return tetherThread.getLooper();
                }

                @Override
                public boolean isTetheringSupported() {
                    return TetheringService.this.isTetheringSupported();
                }

                @Override
                public Context getContext() {
                    return TetheringService.this;
                }

                @Override
                public IpServer.Dependencies getIpServerDependencies() {
                    return new IpServer.Dependencies() {
                        @Override
                        public void makeDhcpServer(String ifName, DhcpServingParamsParcel params,
                                DhcpServerCallbacks cb) {
                            try {
                                mNetworkStackConnector.makeDhcpServer(ifName, params, cb);
                            } catch (RemoteException e) {
                                e.rethrowFromSystemServer();
                            }
                        }
                    };
                }
            };
        }

        return mDeps;
    }
}
