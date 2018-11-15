/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.INetd;
import android.net.ITestNetworkManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkMisc;
import android.net.RouteInfo;
import android.net.StringNetworkSpecifier;
import android.net.TestTunTapInterface;
import android.net.util.NetdService;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

/** @hide */
class TestNetworkService extends ITestNetworkManager.Stub {
    @NonNull private static final String TAG = TestNetworkService.class.getSimpleName();
    @NonNull private static final String TEST_NETWORK_TYPE = "TEST_NETWORK";
    @NonNull private static final String TEST_TUN_PREFIX = "testtun";
    @NonNull private static int sTestTunIndex = 1;

    @NonNull private final Context mContext;
    @NonNull private final INetworkManagementService mNMS;
    @NonNull private final INetd mNetd;

    @NonNull private final HandlerThread mHandlerThread;
    @NonNull private final Handler mHandler;

    // Native method stubs
    private static native int jniCreateTun(@NonNull String iface);

    private static native void jniTeardownTun(@NonNull String iface);

    @VisibleForTesting
    protected TestNetworkService(
            @NonNull Context context, @NonNull INetworkManagementService netManager) {
        mHandlerThread = new HandlerThread("TestNetworkServiceThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mContext = checkNotNull(context, "missing Context");
        mNMS = checkNotNull(netManager, "missing INetworkManagementService");
        mNetd = checkNotNull(NetdService.getInstance(), "could not get netd instance");
    }

    private boolean isTunTapName(@NonNull String iface) {
        return iface.startsWith("testTun") || iface.startsWith("testTap");
    }

    /**
     * Create a TUN interface with the given interface name and link addresses
     *
     * <p>This method will return the FileDescriptor to the TUN interface. Close it to tear down the
     * TUN interface.
     */
    @Override
    public TestTunTapInterface createTunInterface(@NonNull LinkAddress[] linkAddrs) {
        synchronized (TestNetworkService.this) {
            enforceTestNetworkPermissions(mContext);

            String iface = TEST_TUN_PREFIX + sTestTunIndex++;
            long token = Binder.clearCallingIdentity();
            try {
                ParcelFileDescriptor tunIntf = ParcelFileDescriptor.adoptFd(
                        jniCreateTun(iface));
                for (LinkAddress addr : linkAddrs) {
                    mNetd.interfaceAddAddress(
                            iface, addr.getAddress().getHostAddress(), addr.getPrefixLength());
                }

                return new TestTunTapInterface(tunIntf, iface);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    // Tracker for TestNetworkAgents
    @GuardedBy("TestNetworkService.this")
    @NonNull
    private final SparseArray<TestNetworkAgent> mTestNetworkTracker = new SparseArray<>();

    public class TestNetworkAgent extends NetworkAgent implements IBinder.DeathRecipient {
        private static final int NETWORK_SCORE = 1; // Use a low, non-zero score.

        private final int mUid;
        @NonNull private final NetworkInfo mNi;
        @NonNull private final NetworkCapabilities mNc;
        @NonNull private final LinkProperties mLp;

        @NonNull private IBinder mBinder;

        private TestNetworkAgent(
                @NonNull Looper looper,
                @NonNull Context context,
                @NonNull NetworkInfo ni,
                @NonNull NetworkCapabilities nc,
                @NonNull LinkProperties lp,
                @Nullable NetworkMisc networkMisc,
                int uid,
                @NonNull IBinder binder) {
            super(looper, context, TEST_NETWORK_TYPE, ni, nc, lp, NETWORK_SCORE, networkMisc);

            mUid = uid;
            mNi = ni;
            mNc = nc;
            mLp = lp;
            mBinder = binder; // Binder null-checks in create()

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        /**
         * If the Binder object dies, this function is called to free the resources of this
         * TestNetworkAgent
         */
        @Override
        public void binderDied() {
            teardown();
        }

        @Override
        protected void unwanted() {
            teardown();
        }

        private void teardown() {
            synchronized (TestNetworkService.this) {
                if (mBinder != null) {
                    mNi.setDetailedState(DetailedState.DISCONNECTED, null, null);
                    mNi.setIsAvailable(false);
                    sendNetworkInfo(mNi);

                    mBinder.unlinkToDeath(this, 0);
                    mBinder = null;
                }

                // Has to be in TestNetworkAgent to ensure all teardown codepaths properly clean up
                // resources, even for binder death or unwanted calls
                mTestNetworkTracker.remove(netId);
            }
        }
    }

    private TestNetworkAgent registerTestNetworkAgent(
            @NonNull Looper looper,
            @NonNull Context context,
            @NonNull String iface,
            int callingUid,
            @NonNull IBinder binder)
            throws SocketException {
        if (binder == null) {
            throw new IllegalArgumentException(
                    "Must pass a binder to ensure cleanup of TestNetworkAgent");
        }

        // Build network info with special testing type, and a network score of 0
        NetworkInfo ni = new NetworkInfo(ConnectivityManager.TYPE_TEST, 0, TEST_NETWORK_TYPE, "");
        ni.setDetailedState(DetailedState.CONNECTED, null, null);
        ni.setIsAvailable(true);

        // Build narrow set of NetworkCapabilities, useful only for testing
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll(); // Remove default capabilities.
        nc.addTransportType(NetworkCapabilities.TRANSPORT_TEST);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        nc.setSingleUid(callingUid);
        nc.setNetworkSpecifier(new StringNetworkSpecifier(iface));

        // Build LinkProperties
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(iface);

        // Find the currently assigned addresses, and add them to LinkProperties
        boolean allowIPv4 = false, allowIPv6 = false;
        NetworkInterface netIntf = NetworkInterface.getByName(iface);
        checkNotNull(netIntf, "No such network interface found: " + netIntf);

        for (InterfaceAddress intfAddr : netIntf.getInterfaceAddresses()) {
            lp.addLinkAddress(
                    new LinkAddress(intfAddr.getAddress(), intfAddr.getNetworkPrefixLength()));

            if (intfAddr.getAddress() instanceof Inet6Address) {
                allowIPv6 = true;
            } else {
                allowIPv4 = true;
            }
        }

        // Add global routes (but as non-default, non-internet providing, single-UID network)
        if (allowIPv4) {
            lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null, iface));
        }
        if (allowIPv6) {
            lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null, iface));
        }

        // Allow bypass so that traffic is routed correctly when multiple test networks are stacked.
        // This is useful for VPN and IPsec testing.
        NetworkMisc networkMisc = new NetworkMisc();
        networkMisc.allowBypass = true;

        return new TestNetworkAgent(looper, context, ni, nc, lp, networkMisc, callingUid, binder);
    }

    /**
     * Sets up a Network with extremely limited privileges, guarded by the MANAGE_TEST_NETWORKS
     * permission.
     *
     * <p>This method provides a Network that is useful only for testing.
     */
    @Override
    public void setupTestNetwork(@NonNull String iface, @NonNull IBinder binder) {
        if (!(iface.startsWith("ipsec") || isTunTapName(iface))) {
            throw new IllegalArgumentException(
                    "Cannot create network for non ipsec, non-testTun/testTap interface");
        }

        synchronized (TestNetworkService.this) {
            enforceTestNetworkPermissions(mContext);
            int callingUid = Binder.getCallingUid();

            // Most of this needs to be done with NETWORK_STACK privileges.
            long token = Binder.clearCallingIdentity();
            try {
                mNMS.setInterfaceUp(iface);
                TestNetworkAgent agent =
                        registerTestNetworkAgent(
                                mHandler.getLooper(), mContext, iface, callingUid, binder);

                mTestNetworkTracker.put(agent.netId, agent);
            } catch (SocketException e) {
                throw new UncheckedIOException(e);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    /** Teardown a test network */
    @Override
    public void teardownTestNetwork(int netId) {
        synchronized (TestNetworkService.this) {
            enforceTestNetworkPermissions(mContext);

            TestNetworkAgent agent = mTestNetworkTracker.get(netId);
            if (agent == null) {
                return; // Already torn down
            } else if (agent.mUid != Binder.getCallingUid()) {
                throw new SecurityException("Attempted to modify other user's test networks");
            }

            agent.teardown();
        }
    }

    // STOPSHIP: Change this back to android.Manifest.permission.MANAGE_TEST_NETWORKS
    private static final String PERMISSION_NAME = "dummy";

    public static void enforceTestNetworkPermissions(@NonNull Context context) {
        // STOPSHIP: Re-enable these checks. Disabled until adoptShellPermissionIdentity() can be
        //           called from CTS test code.
        if (false) {
            context.enforceCallingOrSelfPermission(PERMISSION_NAME, "TestNetworkService");
        }
    }
}
