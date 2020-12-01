/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server;

import static com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionSnapshot;
import static com.android.server.vcn.TelephonySubscriptionTracker.TelephonySubscriptionTrackerCallback;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.vcn.IVcnManagementService;
import android.net.vcn.VcnConfig;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;
import com.android.server.vcn.TelephonySubscriptionTracker;
import com.android.server.vcn.Vcn;
import com.android.server.vcn.VcnContext;
import com.android.server.vcn.util.PersistableBundleUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * VcnManagementService manages Virtual Carrier Network profiles and lifecycles.
 *
 * <pre>The internal structure of the VCN Management subsystem is as follows:
 *
 * +-------------------------+ 1:1                                +--------------------------------+
 * |  VcnManagementService   | ------------ Creates ------------> |  TelephonySubscriptionManager  |
 * |                         |                                    |                                |
 * |   Manages configs and   |                                    | Tracks subscriptions, carrier  |
 * | Vcn instance lifecycles | <--- Notifies of subscription & -- | privilege changes, caches maps |
 * +-------------------------+      carrier privilege changes     +--------------------------------+
 *      | 1:N          ^
 *      |              |
 *      |              +-------------------------------+
 *      +---------------+                              |
 *                      |                              |
 *         Creates when config present,                |
 *        subscription group active, and               |
 *      providing app is carrier privileged     Notifies of safe
 *                      |                      mode state changes
 *                      v                              |
 * +-----------------------------------------------------------------------+
 * |                                  Vcn                                  |
 * |                                                                       |
 * |       Manages GatewayConnection lifecycles based on fulfillable       |
 * |                NetworkRequest(s) and overall safe-mode                |
 * +-----------------------------------------------------------------------+
 *                      | 1:N                          ^
 *              Creates to fulfill                     |
 *           NetworkRequest(s), tears   Notifies of VcnGatewayConnection
 *          down when no longer needed   teardown (e.g. Network reaped)
 *                      |                 and safe-mode timer changes
 *                      v                              |
 * +-----------------------------------------------------------------------+
 * |                          VcnGatewayConnection                         |
 * |                                                                       |
 * |       Manages a single (IKEv2) tunnel session and NetworkAgent,       |
 * |  handles mobility events, (IPsec) Tunnel setup and safe-mode timers   |
 * +-----------------------------------------------------------------------+
 *                      | 1:1                          ^
 *                      |                              |
 *          Creates upon instantiation      Notifies of changes in
 *                      |                 selected underlying network
 *                      |                     or its properties
 *                      v                              |
 * +-----------------------------------------------------------------------+
 * |                       UnderlyingNetworkTracker                        |
 * |                                                                       |
 * | Manages lifecycle of underlying physical networks, filing requests to |
 * | bring them up, and releasing them as they become no longer necessary  |
 * +-----------------------------------------------------------------------+
 * </pre>
 *
 * @hide
 */
public class VcnManagementService extends IVcnManagementService.Stub
        implements TelephonySubscriptionTrackerCallback {
    @NonNull private static final String TAG = VcnManagementService.class.getSimpleName();

    public static final boolean VDBG = false; // STOPSHIP: if true

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final String VCN_CONFIG_FILE = "/data/system/vcn/configs.xml";

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    static final long CARRIER_PRIVILEGES_LOST_TEARDOWN_DELAY_MS = TimeUnit.SECONDS.toMillis(30);

    /* Binder context for this service */
    @NonNull private final Context mContext;
    @NonNull private final Dependencies mDeps;

    @NonNull private final Looper mLooper;
    @NonNull private final Handler mHandler;
    @NonNull private final VcnNetworkProvider mNetworkProvider;
    @NonNull private final TelephonySubscriptionTracker mTelephonySubscriptionTracker;
    @NonNull private final VcnContext mVcnContext;

    @GuardedBy("mVcnAndConfigRwLock")
    @NonNull
    private final Map<ParcelUuid, VcnConfig> mConfigs = new HashMap<>();

    @GuardedBy("mVcnAndConfigRwLock")
    @NonNull
    private final Map<ParcelUuid, Vcn> mVcns = new ArrayMap<>();

    @GuardedBy("mVcnAndConfigRwLock")
    @Nullable
    private TelephonySubscriptionSnapshot mLastSnapshot = null;

    @NonNull private final ReadWriteLock mVcnAndConfigRwLock = new ReentrantReadWriteLock();

    @NonNull private final PersistableBundleUtils.LockingReadWriteHelper mConfigDiskRwHelper;

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    VcnManagementService(@NonNull Context context, @NonNull Dependencies deps) {
        mContext = requireNonNull(context, "Missing context");
        mDeps = requireNonNull(deps, "Missing dependencies");

        mLooper = mDeps.getLooper();
        mHandler = new Handler(mLooper);
        mNetworkProvider = new VcnNetworkProvider(mContext, mLooper);
        mTelephonySubscriptionTracker =
                mDeps.newTelephonySubscriptionTracker(mContext, mLooper, this);

        mConfigDiskRwHelper = mDeps.newPersistableBundleLockingReadWriteHelper(VCN_CONFIG_FILE);
        mVcnContext = mDeps.newVcnContext(mContext, mLooper, mNetworkProvider);

        // Run on handler to ensure I/O does not block system server startup
        mHandler.post(() -> {
            try {
                final PersistableBundle configBundle = mConfigDiskRwHelper.readFromDisk();

                if (configBundle != null) {
                    final Map<ParcelUuid, VcnConfig> configs =
                            PersistableBundleUtils.toMap(
                                    configBundle,
                                    PersistableBundleUtils::toParcelUuid,
                                    VcnConfig::new);

                    mVcnAndConfigRwLock.writeLock().lock();
                    try {
                        for (Entry<ParcelUuid, VcnConfig> entry : configs.entrySet()) {
                            mConfigs.put(entry.getKey(), entry.getValue());
                        }

                        // If subscriptions have been loaded, re-evaluate, and start/stop VCNs.
                        if (mLastSnapshot != null) {
                            onNewSnapshot(mLastSnapshot);
                        }
                    } finally {
                        mVcnAndConfigRwLock.writeLock().unlock();
                    }
                }
            } catch (IOException e) {
                Slog.wtf(TAG, "Failed to read configs from disk", e);
            }
        });
    }

    // Package-visibility for SystemServer to create instances.
    static VcnManagementService create(@NonNull Context context) {
        return new VcnManagementService(context, new Dependencies());
    }

    /** External dependencies used by VcnManagementService, for injection in tests */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static class Dependencies {
        private HandlerThread mHandlerThread;

        /** Retrieves a looper for the VcnManagementService */
        public Looper getLooper() {
            if (mHandlerThread == null) {
                synchronized (this) {
                    if (mHandlerThread == null) {
                        mHandlerThread = new HandlerThread(TAG);
                        mHandlerThread.start();
                    }
                }
            }
            return mHandlerThread.getLooper();
        }

        /** Creates a new VcnInstance using the provided configuration */
        public TelephonySubscriptionTracker newTelephonySubscriptionTracker(
                @NonNull Context context,
                @NonNull Looper looper,
                @NonNull TelephonySubscriptionTrackerCallback callback) {
            return new TelephonySubscriptionTracker(context, new Handler(looper), callback);
        }

        /**
         * Retrieves the caller's UID
         *
         * <p>This call MUST be made before calling {@link Binder#clearCallingIdentity}, otherwise
         * this will not work properly.
         *
         * @return
         */
        public int getBinderCallingUid() {
            return Binder.getCallingUid();
        }

        /**
         * Creates and returns a new {@link PersistableBundle.LockingReadWriteHelper}
         *
         * @param path the file path to read/write from/to.
         * @return the {@link PersistableBundleUtils.LockingReadWriteHelper} instance
         */
        public PersistableBundleUtils.LockingReadWriteHelper
                newPersistableBundleLockingReadWriteHelper(@NonNull String path) {
            return new PersistableBundleUtils.LockingReadWriteHelper(path);
        }

        /** Creates a new VcnContext */
        public VcnContext newVcnContext(
                @NonNull Context context,
                @NonNull Looper looper,
                @NonNull VcnNetworkProvider vcnNetworkProvider) {
            return new VcnContext(context, looper, vcnNetworkProvider);
        }

        /** Creates a new Vcn instance using the provided configuration */
        public Vcn newVcn(
                @NonNull VcnContext vcnContext,
                @NonNull ParcelUuid subscriptionGroup,
                @NonNull VcnConfig config) {
            return new Vcn(vcnContext, subscriptionGroup, config);
        }
    }

    /** Notifies the VcnManagementService that external dependencies can be set up. */
    public void systemReady() {
        // TODO: Retrieve existing profiles from KeyStore

        mContext.getSystemService(ConnectivityManager.class)
                .registerNetworkProvider(mNetworkProvider);
        mTelephonySubscriptionTracker.register();
    }

    private void enforcePrimaryUser() {
        final int uid = mDeps.getBinderCallingUid();
        if (uid == Process.SYSTEM_UID) {
            Slog.wtf(TAG, "Caller was already system. Was Binder calling identity cleared?");
        }

        if (!UserHandle.getUserHandleForUid(uid).isSystem()) {
            throw new SecurityException(
                    "VcnManagementService can only be used by callers running as the primary user");
        }
    }

    private void enforceCallingUserAndCarrierPrivilege(ParcelUuid subscriptionGroup) {
        // Only apps running in the primary (system) user are allowed to configure the VCN. This is
        // in line with Telephony's behavior with regards to binding to a Carrier App provided
        // CarrierConfigService.
        enforcePrimaryUser();

        // TODO (b/172619301): Check based on events propagated from CarrierPrivilegesTracker
        final SubscriptionManager subMgr = mContext.getSystemService(SubscriptionManager.class);
        final List<SubscriptionInfo> subscriptionInfos = new ArrayList<>();
        Binder.withCleanCallingIdentity(
                () -> {
                    subscriptionInfos.addAll(subMgr.getSubscriptionsInGroup(subscriptionGroup));
                });

        final TelephonyManager telMgr = mContext.getSystemService(TelephonyManager.class);
        for (SubscriptionInfo info : subscriptionInfos) {
            // Check subscription is active first; much cheaper/faster check, and an app (currently)
            // cannot be carrier privileged for inactive subscriptions.
            if (subMgr.isValidSlotIndex(info.getSimSlotIndex())
                    && telMgr.hasCarrierPrivileges(info.getSubscriptionId())) {
                // TODO (b/173717728): Allow configuration for inactive, but manageable
                // subscriptions.
                // TODO (b/173718661): Check for whole subscription groups at a time.
                return;
            }
        }

        throw new SecurityException(
                "Carrier privilege required for subscription group to set VCN Config");
    }

    /**
     * Handles subscription group changes, as notified by {@link TelephonySubscriptionTracker}
     *
     * <p>Start any unstarted VCN instances
     *
     * @hide
     */
    public void onNewSnapshot(@NonNull TelephonySubscriptionSnapshot snapshot) {
        // Startup VCN instances
        mVcnAndConfigRwLock.writeLock().lock();
        try {
            mLastSnapshot = snapshot;

            // Start any VCN instances as necessary
            for (Entry<ParcelUuid, VcnConfig> entry : mConfigs.entrySet()) {
                if (isProfilePermissioned(
                        entry.getKey(), entry.getValue().getProvisioningPackageName(), snapshot)) {
                    maybeStartVcnWriteLocked(entry.getKey(), entry.getValue());

                    // Cancel any scheduled teardowns for active subscriptions
                    mHandler.removeCallbacksAndMessages(mVcns.get(entry.getKey()));
                }
            }

            // Schedule teardown of any VCN instances that have lost carrier privileges (after a
            // delay)
            for (Entry<ParcelUuid, Vcn> entry : mVcns.entrySet()) {
                final VcnConfig config = mConfigs.get(entry.getKey());
                if (config == null
                        || !isProfilePermissioned(
                                entry.getKey(), config.getProvisioningPackageName(), snapshot)) {
                    final ParcelUuid uuidToTeardown = entry.getKey();
                    final Vcn instanceToTeardown = entry.getValue();

                    mHandler.postDelayed(() -> {
                        mVcnAndConfigRwLock.writeLock().lock();
                        try {
                            // Guard against case where this is run after a old instance was torn
                            // down, and a new instance was started. Verify to ensure correct
                            // instance is torn down. This could happen as a result of a Carrier App
                            // manually removing/adding a VcnConfig.
                            if (mVcns.get(uuidToTeardown) == instanceToTeardown) {
                                mVcns.remove(uuidToTeardown).teardownAsynchronously();
                            }
                        } finally {
                            mVcnAndConfigRwLock.writeLock().unlock();
                        }
                    }, entry.getValue(), CARRIER_PRIVILEGES_LOST_TEARDOWN_DELAY_MS);
                }
            }
        } finally {
            mVcnAndConfigRwLock.writeLock().unlock();
        }
    }

    private boolean isProfilePermissioned(
            @NonNull ParcelUuid groupUuid,
            @NonNull String packageName,
            @NonNull TelephonySubscriptionSnapshot snapshot) {
        final Set<String> privilegedPackages =
                snapshot.getPrivilegedPackagesForSubscriptionGroup(groupUuid);

        return privilegedPackages != null && privilegedPackages.contains(packageName);
    }

    @GuardedBy("mVcnAndConfigRwLock.writeLock()")
    private void maybeStartVcnWriteLocked(
            @NonNull ParcelUuid subscriptionGroup, @NonNull VcnConfig config) {
        if (!mVcns.containsKey(subscriptionGroup)) {
            Slog.v(TAG, "Starting VCN config for subGrp: " + subscriptionGroup);

            final Vcn newInstance = mDeps.newVcn(mVcnContext, subscriptionGroup, config);
            mVcns.put(subscriptionGroup, newInstance);
        }
    }

    @GuardedBy("mVcnAndConfigRwLock.writeLock()")
    private void startOrUpdateVcnWriteLocked(
            @NonNull ParcelUuid subscriptionGroup, @NonNull VcnConfig config) {
        Slog.v(TAG, "Starting or updating VCN config for subGrp: " + subscriptionGroup);

        if (mVcns.containsKey(subscriptionGroup)) {
            mVcns.get(subscriptionGroup).updateConfig(config);
        } else {
            maybeStartVcnWriteLocked(subscriptionGroup, config);
        }
    }

    /**
     * Sets a VCN config for a given subscription group.
     *
     * <p>Implements the IVcnManagementService Binder interface.
     */
    @Override
    public void setVcnConfig(
            @NonNull ParcelUuid subscriptionGroup,
            @NonNull VcnConfig config,
            @NonNull String opPkgName) {
        requireNonNull(subscriptionGroup, "subscriptionGroup was null");
        requireNonNull(config, "config was null");
        requireNonNull(opPkgName, "opPkgName was null");
        if (!config.getProvisioningPackageName().equals(opPkgName)) {
            throw new IllegalArgumentException("Mismatched caller and VcnConfig creator");
        }
        Slog.v(TAG, "VCN config updated for subGrp: " + subscriptionGroup);

        mContext.getSystemService(AppOpsManager.class)
                .checkPackage(Binder.getCallingUid(), config.getProvisioningPackageName());
        enforceCallingUserAndCarrierPrivilege(subscriptionGroup);

        final long token = Binder.clearCallingIdentity();
        try {
            mVcnAndConfigRwLock.writeLock().lock();
            try {
                mConfigs.put(subscriptionGroup, config);

                startOrUpdateVcnWriteLocked(subscriptionGroup, config);

                // Downgrade lock to reduce critical section to non-IO-bound calls, while ensuring
                // race-free persistence to disk.
                mVcnAndConfigRwLock.readLock().lock();
            } finally {
                mVcnAndConfigRwLock.writeLock().unlock();
            }

            try {
                writeConfigsToDiskReadLocked();
            } finally {
                mVcnAndConfigRwLock.readLock().unlock();
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Clears the VcnManagementService for a given subscription group.
     *
     * <p>Implements the IVcnManagementService Binder interface.
     */
    @Override
    public void clearVcnConfig(@NonNull ParcelUuid subscriptionGroup) {
        requireNonNull(subscriptionGroup, "subscriptionGroup was null");
        Slog.v(TAG, "VCN config cleared for subGrp: " + subscriptionGroup);

        enforceCallingUserAndCarrierPrivilege(subscriptionGroup);

        final long token = Binder.clearCallingIdentity();
        try {
            mVcnAndConfigRwLock.writeLock().lock();
            try {
                mConfigs.remove(subscriptionGroup);

                if (mVcns.containsKey(subscriptionGroup)) {
                    mVcns.remove(subscriptionGroup).teardownAsynchronously();
                }

                // Downgrade lock to reduce critical section to non-IO-bound calls, while ensuring
                // race-free persistence to disk.
                mVcnAndConfigRwLock.readLock().lock();
            } finally {
                mVcnAndConfigRwLock.writeLock().unlock();
            }

            try {
                writeConfigsToDiskReadLocked();
            } finally {
                mVcnAndConfigRwLock.readLock().unlock();
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @GuardedBy("mVcnAndConfigRwLock.readLock()")
    private void writeConfigsToDiskReadLocked() {
        try {
            PersistableBundle bundle =
                    PersistableBundleUtils.fromMap(
                            mConfigs,
                            PersistableBundleUtils::fromParcelUuid,
                            VcnConfig::toPersistableBundle);
            mConfigDiskRwHelper.writeToDisk(bundle);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to save configs to disk", e);
            throw new ServiceSpecificException(0, "Failed to save configs");
        }
    }

    /** Get current configuration list for testing purposes */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    Map<ParcelUuid, VcnConfig> getConfigs() {
        mConfigsRwLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(mConfigs);
        } finally {
            mVcnAndConfigRwLock.readLock().unlock();
        }
    }

    /** Get current configuration list for testing purposes */
    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public Map<ParcelUuid, Vcn> getInstances() {
        mVcnAndConfigRwLock.readLock().lock();
        try {
            return Collections.unmodifiableMap(mVcns);
        } finally {
            mVcnAndConfigRwLock.readLock().unlock();
        }
    }

    /**
     * Network provider for VCN networks.
     *
     * @hide
     */
    public class VcnNetworkProvider extends NetworkProvider {
        VcnNetworkProvider(Context context, Looper looper) {
            super(context, looper, VcnNetworkProvider.class.getSimpleName());
        }

        @Override
        public void onNetworkRequested(@NonNull NetworkRequest request, int score, int providerId) {
            mVcnAndConfigRwLock.readLock().lock();
            try {
                for (Vcn instance : mVcns.values()) {
                    instance.onNetworkRequested(request, score, providerId);
                }
            } finally {
                mVcnAndConfigRwLock.readLock().unlock();
            }
        }
    }
}
