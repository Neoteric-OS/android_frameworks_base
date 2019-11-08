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

import static android.net.ConnectivityManager.TETHER_ERROR_NO_ERROR;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.content.Context;
import android.net.ConnectivityManager.OnTetheringEventCallback;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * This class provides the APIs to control the tethering service.
 * <p> The primary responsibilities of this class are to provide the APIs for applications to
 * start tethering, stop tethering, query configuration and query status.
 *
 * @hide
 */
@SystemApi
public class TetheringManager {
    private static final String TAG = TetheringManager.class.getSimpleName();

    private static TetheringManager sInstance;

    private final ITetheringConnector mConnector;
    private final TetheringCallbackInternal mCallback;
    private final Context mContext;
    private final ArrayMap<OnTetheringEventCallback, ITetheringEventCallback>
            mTetheringEventCallbacks = new ArrayMap<>();
    private final WaitingUtil mWaitForCallback = new WaitingUtil();

    private Network mTetherUpstream;
    private TetheringConfigurationParcel mTetheringConfiguration;
    private TetherStatesParcel mTetherStatesParcel;
    private volatile boolean mTetherRestricted;

    public static final String EXTRA_NETWORKSTACK = "android.net.extra.NETWORKSTACK";

    public static final int TETHER_ERROR_NO_ERROR           = 0;
    public static final int TETHER_ERROR_UNKNOWN_IFACE      = 1;
    public static final int TETHER_ERROR_SERVICE_UNAVAIL    = 2;
    public static final int TETHER_ERROR_UNSUPPORTED        = 3;
    public static final int TETHER_ERROR_UNAVAIL_IFACE      = 4;
    public static final int TETHER_ERROR_MASTER_ERROR       = 5;
    public static final int TETHER_ERROR_TETHER_IFACE_ERROR = 6;
    public static final int TETHER_ERROR_UNTETHER_IFACE_ERROR = 7;
    public static final int TETHER_ERROR_ENABLE_NAT_ERROR     = 8;
    public static final int TETHER_ERROR_DISABLE_NAT_ERROR    = 9;
    public static final int TETHER_ERROR_IFACE_CFG_ERROR      = 10;
    public static final int TETHER_ERROR_PROVISION_FAILED     = 11;
    public static final int TETHER_ERROR_DHCPSERVER_ERROR     = 12;
    public static final int TETHER_ERROR_ENTITLEMENT_UNKONWN  = 13;
    public static final int TETHER_ERROR_NO_PRIVILEGED_PERMISSION = 14;
    public static final int TETHER_ERROR_NO_ACCESS_PERMISSION = 15;

    /**
     * Create a TetheringManager object for interacting with the tethering service.
     */
    public TetheringManager(@NonNull Context context, @NonNull IBinder service) {
        mContext = context;
        mConnector = ITetheringConnector.Stub.asInterface(service);
        mCallback = new TetheringCallbackInternal();

        String pkgName = mContext.getOpPackageName();
        Log.i(TAG, "registerTetheringEventCallback:" + pkgName);

        mWaitForCallback.waitForResult(listener -> {
            try {
                mConnector.registerTetheringEventCallback(mCallback, pkgName);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
        throwIfPermissionFailure(mCallback.mPermissionFailure);
    }

    private class ResultListener extends IResultListener.Stub {
        public int remoteResult;

        @Override
        public void onResult(int resultCode) { }
    }

    private interface RequestHelper {
        void runRequest(ResultListener listener);
    }

    private class WaitingUtil {
        private static final int DEFAULT_TIMEOUT_MS = 60_000;
        private final ConditionVariable mWaiting;
        private final int mTimeout;

        private final ResultListener mListener = new ResultListener() {
                @Override
                public void onResult(final int resultCode) {
                    remoteResult = resultCode;
                    open();
                }
        };

        WaitingUtil() {
            this(DEFAULT_TIMEOUT_MS);
        }

        WaitingUtil(int timeout) {
            mTimeout = timeout;
            mWaiting = new ConditionVariable(false);
        }

        public void open() {
            mWaiting.open();
        }

        public boolean waitOrTimeout() {
            return mWaiting.block(mTimeout);
        }

        int waitForResult(RequestHelper request) {
            request.runRequest(mListener);
            if (!waitOrTimeout()) throw new IllegalStateException("Callback timeout");

            throwIfPermissionFailure(mListener.remoteResult);

            return mListener.remoteResult;
        }
    }

    private void throwIfPermissionFailure(int errorCode) {
        switch (errorCode) {
            case TETHER_ERROR_NO_PRIVILEGED_PERMISSION:
                throw new SecurityException("No android.permission.TETHER_PRIVILEGED permission");
            case TETHER_ERROR_NO_ACCESS_PERMISSION:
                throw new SecurityException(
                        "No android.permission.ACCESS_NETWORK_STATE permission");
        }
    }

    private class TetheringCallbackInternal extends ITetheringEventCallback.Stub {
        private int mPermissionFailure = TETHER_ERROR_NO_ERROR;

        @Override
        public void onCallbackCreated(Network network, TetheringConfigurationParcel config,
                TetherStatesParcel states, boolean isRestricted) {
            mTetherUpstream = network;
            mTetheringConfiguration = config;
            mTetherStatesParcel = states;
            mTetherRestricted = isRestricted;
            mWaitForCallback.open();
        }

        @Override
        public void onCallbackFailed(int errorCode) {
            mPermissionFailure = errorCode;
        }

        @Override
        public void onUpstreamChanged(Network network) {
            mTetherUpstream = network;
        }

        @Override
        public void onConfigurationChanged(TetheringConfigurationParcel config) {
            mTetheringConfiguration = config;
        }

        @Override
        public void onTetherStatesChanged(TetherStatesParcel states) {
            mTetherStatesParcel = states;
        }

        @Override
        public void onTetheringRestricted(boolean isRestricted) {
            mTetherRestricted = isRestricted;
        }
    }

    /**
     * Attempt to tether the named interface.  This will setup a dhcp server
     * on the interface, forward and NAT IP packets and forward DNS requests
     * to the best active upstream network interface.  Note that if no upstream
     * IP network interface is available, dhcp will still run and traffic will be
     * allowed between the tethered devices and this device, though upstream net
     * access will of course fail until an upstream network interface becomes
     * active.
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to tether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     */
    public int tether(@NonNull String iface) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "tether caller:" + callerPkg);
        WaitingUtil waitForCallback = new WaitingUtil();

        return waitForCallback.waitForResult(listener -> {
            try {
                mConnector.tether(iface, callerPkg, listener);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Stop tethering the named interface.
     *
     * <p>This method requires the caller to hold either the
     * {@link android.Manifest.permission#CHANGE_NETWORK_STATE} permission
     * or the ability to modify system settings as determined by
     * {@link android.provider.Settings.System#canWrite}.</p>
     *
     * <p>WARNING: New clients should not use this function. The only usages should be in PanService
     * which need direct access. All other clients should use
     * {@link #startTethering} and {@link #stopTethering} which encapsulate proper provisioning
     * logic.</p>
     *
     * @param iface the interface name to untether.
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     */
    public int untether(@NonNull String iface) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "untether caller:" + callerPkg);

        WaitingUtil waitForCallback = new WaitingUtil();

        return waitForCallback.waitForResult(listener -> {
            try {
                mConnector.untether(iface, callerPkg, listener);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Attempt to both alter the mode of USB and Tethering of USB.  A
     * utility method to deal with some of the complexity of USB - will
     * attempt to switch to Rndis and subsequently tether the resulting
     * interface on {@code true} or turn off tethering and switch off
     * Rndis on {@code false}.
     *
     * @param enable a boolean - {@code true} to enable tethering
     * @return error a {@code TETHER_ERROR} value indicating success or failure type
     */
    public int setUsbTethering(boolean enable) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "setUsbTethering caller:" + callerPkg);

        WaitingUtil waitForCallback = new WaitingUtil();

        return waitForCallback.waitForResult(listener -> {
            try {
                mConnector.setUsbTethering(enable, callerPkg, listener);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Starts tethering and runs tether provisioning for the given type if needed. If provisioning
     * fail, tethering would be closed automatically.
     */
    public void startTethering(int type, @NonNull ResultReceiver receiver,
            boolean showProvisioningUi) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "startTethering caller:" + callerPkg);

        try {
            mConnector.startTethering(type, receiver, showProvisioningUi, callerPkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Stops tethering for the given type. Also cancels any provisioning rechecks for that type if
     * applicable.
     */
    public void stopTethering(int type) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "stopTethering caller:" + callerPkg);

        WaitingUtil waitForCallback = new WaitingUtil();

        waitForCallback.waitForResult(listener -> {
            try {
                mConnector.stopTethering(type, callerPkg, listener);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Get the latest value of the tethering entitlement check.
     *
     * Note: Allow privileged apps who have TETHER_PRIVILEGED permission to access. If it turns
     * out some such apps are observed to abuse this API, change to per-UID limits on this API
     * if it's really needed.
     */
    public void getLatestTetheringEntitlementResult(int type, @NonNull ResultReceiver receiver,
            boolean showEntitlementUi) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "getLatestTetheringEntitlementResult caller:" + callerPkg);

        try {
            mConnector.getLatestTetheringEntitlementResult(type, receiver, showEntitlementUi,
                    callerPkg);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Start listening to tethering change events. Any new added callback will receive the last
     * tethering status right away. If callback is registered when tethering has no upstream or
     * disabled, {@link OnTetheringEventCallback#onUpstreamChanged} will immediately be called
     * with a null argument. The same callback object cannot be registered twice.
     *
     * @param executor the executor on which callback will be invoked.
     * @param callback the callback to be called when tethering has change events.
     */
    public void registerTetheringEventCallback(@NonNull Executor executor,
            @NonNull OnTetheringEventCallback callback) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "registerTetheringEventCallback caller:" + callerPkg);

        synchronized (mTetheringEventCallbacks) {
            if (!mTetheringEventCallbacks.containsKey(callback)) {
                throw new IllegalArgumentException("callback was already registered.");
            }
            ITetheringEventCallback remoteCallback = new ITetheringEventCallback.Stub() {
                @Override
                public void onUpstreamChanged(Network network) throws RemoteException {
                    executor.execute(() -> {
                        callback.onUpstreamChanged(network);
                    });
                }

                @Override
                public void onCallbackCreated(Network network, TetheringConfigurationParcel config,
                        TetherStatesParcel states, boolean isRestricted) {
                    executor.execute(() -> {
                        callback.onUpstreamChanged(network);
                    });
                }

                @Override
                public void onCallbackFailed(int errorCode) {
                    executor.execute(() -> {
                        throwIfPermissionFailure(errorCode);
                    });
                }

                @Override
                public void onConfigurationChanged(TetheringConfigurationParcel config) { }

                @Override
                public void onTetherStatesChanged(TetherStatesParcel states) { }

                @Override
                public void onTetheringRestricted(boolean isRestricted) { }
            };
            try {
                mConnector.registerTetheringEventCallback(remoteCallback, callerPkg);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            mTetheringEventCallbacks.put(callback, remoteCallback);
        }
    }

    /**
     * Remove tethering event callback previously registered with
     * {@link #registerTetheringEventCallback}.
     *
     * @param callback previously registered callback.
     */
    public void unregisterTetheringEventCallback(@NonNull OnTetheringEventCallback callback) {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "unregisterTetheringEventCallback caller:" + callerPkg);

        synchronized (mTetheringEventCallbacks) {
            ITetheringEventCallback remoteCallback = mTetheringEventCallbacks.remove(callback);
            if (remoteCallback == null) {
                throw new IllegalArgumentException("callback was not registered.");
            }
            try {
                mConnector.unregisterTetheringEventCallback(remoteCallback, callerPkg);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            mTetheringEventCallbacks.put(callback, remoteCallback);
        }
    }

    /**
     * Get a more detailed error code after a Tethering or Untethering
     * request asynchronously failed.
     *
     * @param iface The name of the interface of interest
     * @return error The error code of the last error tethering or untethering the named
     *               interface
     */
    public int getLastTetherError(@NonNull String iface) {
        if (mTetherStatesParcel == null) return TETHER_ERROR_NO_ERROR;

        int i = 0;
        for (String errored : mTetherStatesParcel.erroredIfaceList) {
            if (iface.equals(errored)) return mTetherStatesParcel.lastErrorList[i];

            i++;
        }
        return TETHER_ERROR_NO_ERROR;
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * USB network interfaces.  If USB tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable usb interfaces.
     */
    public @NonNull String[] getTetherableUsbRegexs() {
        return mTetheringConfiguration.tetherableUsbRegexs;
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Wifi network interfaces.  If Wifi tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable wifi interfaces.
     */
    public @NonNull String[] getTetherableWifiRegexs() {
        return mTetheringConfiguration.tetherableWifiRegexs;
    }

    /**
     * Get the list of regular expressions that define any tetherable
     * Bluetooth network interfaces.  If Bluetooth tethering is not supported by the
     * device, this list should be empty.
     *
     * @return an array of 0 or more regular expression Strings defining
     *        what interfaces are considered tetherable bluetooth interfaces.
     */
    public @NonNull String[] getTetherableBluetoothRegexs() {
        return mTetheringConfiguration.tetherableBluetoothRegexs;
    }

    /**
     * Get the set of tetherable, available interfaces.  This list is limited by
     * device configuration and current interface existence.
     *
     * @return an array of 0 or more Strings of tetherable interface names.
     */
    public @NonNull String[] getTetherableIfaces() {
        if (mTetherStatesParcel == null) return new String[0];

        return mTetherStatesParcel.availableList;
    }

    /**
     * Get the set of tethered interfaces.
     *
     * @return an array of 0 or more String of currently tethered interface names.
     */
    public @NonNull String[] getTetheredIfaces() {
        if (mTetherStatesParcel == null) return new String[0];

        return mTetherStatesParcel.tetheredList;
    }

    /**
     * Get the set of interface names which attempted to tether but
     * failed.  Re-attempting to tether may cause them to reset to the Tethered
     * state.  Alternatively, causing the interface to be destroyed and recreated
     * may cause them to reset to the available state.
     * {@link ConnectivityManager#getLastTetherError} can be used to get more
     * information on the cause of the errors.
     *
     * @return an array of 0 or more String indicating the interface names
     *        which failed to tether.
     */
    public @NonNull String[] getTetheringErroredIfaces() {
        if (mTetherStatesParcel == null) return new String[0];

        return mTetherStatesParcel.erroredIfaceList;
    }

    /**
     * Get the set of tethered dhcp ranges.
     *
     * @return an array of 0 or more {@code String} of tethered dhcp ranges.
     */
    public @NonNull String[] getTetheredDhcpRanges() {
        return mTetheringConfiguration.legacyDhcpRanges;
    }

    private boolean hasTetherableConfiguration() {
        final boolean hasDownstreamConfiguration =
                (mTetheringConfiguration.tetherableUsbRegexs.length != 0)
                || (mTetheringConfiguration.tetherableWifiRegexs.length != 0)
                || (mTetheringConfiguration.tetherableBluetoothRegexs.length != 0);
        final boolean hasUpstreamConfiguration =
                (mTetheringConfiguration.preferredUpstreamIfaceTypes.length != 0)
                || mTetheringConfiguration.chooseUpstreamAutomatically;

        return hasDownstreamConfiguration && hasUpstreamConfiguration;
    }

    /**
     * Check if the device allows for tethering.  It may be disabled via
     * {@code ro.tether.denied} system property, Settings.TETHER_SUPPORTED or
     * due to device configuration.
     *
     * @return a boolean - {@code true} indicating Tethering is supported.
     */
    public boolean isTetheringSupported() {
        // Keep the same as TetheringService.java
        int defaultVal =
                SystemProperties.get("ro.tether.denied").equals("true") ? 0 : 1;
        boolean tetherSupported = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.TETHER_SUPPORTED, defaultVal) != 0;

        return tetherSupported && hasTetherableConfiguration() && !mTetherRestricted;
    }

    /**
     * Close any active tethering back to factory defaults.
     */
    public void factoryReset() {
        String callerPkg = mContext.getOpPackageName();
        Log.i(TAG, "factoryReset caller:" + callerPkg);

        if (!mTetherRestricted) {
            WaitingUtil waitForCallback = new WaitingUtil();

            waitForCallback.waitForResult(listener -> {
                try {
                    mConnector.stopTetheringAll(callerPkg, listener);
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
