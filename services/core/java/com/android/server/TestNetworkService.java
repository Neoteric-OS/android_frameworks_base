/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AppOpsManager;
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
import android.net.util.NetdService;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;

/** @hide */
class TestNetworkService extends ITestNetworkManager.Stub {
    private static final String TAG = TestNetworkService.class.getSimpleName();
    private static final String TEST_NETWORK_TYPE = "TEST_NETWORK";

    private final Context mContext;
    private INetworkManagementService mNMS;
    private INetd mNetd;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private static native int jniCreateTun(String intfName);

    private static native void jniTeardownTun(String intfName);

    @VisibleForTesting
    protected TestNetworkService(Context context, INetworkManagementService netManager) {
        Log.d(TAG, "TestNetworkService starting up");

        mHandlerThread = new HandlerThread("TestNetworkServiceThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mContext = checkNotNull(context, "missing Context");
        mNMS = checkNotNull(netManager, "missing INetworkManagementService");
        mNetd = NetdService.getInstance();
    }

    /**
     * Build a TUN interface with the given interface name and link addresses
     *
     * <p>This method will return the FileDescriptor to the TUN interface. Close it to teardown the
     * TUN interface.
     */
    @Override
    public synchronized ParcelFileDescriptor buildTun(
            String intfName, LinkAddress[] linkAddrs, String callingPackage) {
        enforceTestNetworkPermissions(mContext, callingPackage);

        int callingUid = Binder.getCallingUid();
        long token = Binder.clearCallingIdentity();
        try {
            ParcelFileDescriptor tunIntf = ParcelFileDescriptor.adoptFd(jniCreateTun(intfName));
            for (LinkAddress addr : linkAddrs) {
                mNetd.interfaceAddAddress(
                        intfName, addr.getAddress().getHostAddress(), addr.getPrefixLength());
            }

            return tunIntf;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // Tracker for TestNetworkAgents
    private static SparseArray<TestNetworkAgent> sTestNetworkTracker = new SparseArray<>();

    public static class TestNetworkAgent extends NetworkAgent implements IBinder.DeathRecipient {
        private static final int NETWORK_SCORE = 1; // Use a low, non-zero score.

        private final int mUid;
        private final NetworkInfo mNi;
        private final NetworkCapabilities mNc;
        private final LinkProperties mLp;

        private IBinder mBinder;

        private TestNetworkAgent(
                Looper looper,
                Context context,
                NetworkInfo ni,
                NetworkCapabilities nc,
                LinkProperties lp,
                NetworkMisc networkMisc,
                int uid,
                IBinder binder) {
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

            sTestNetworkTracker.put(this.netId, this);
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

        private synchronized void teardown() {
            if (mBinder != null) {
                mNi.setDetailedState(DetailedState.DISCONNECTED, null, null);
                mNi.setIsAvailable(false);
                sendNetworkInfo(mNi);

                mBinder.unlinkToDeath(this, 0);
                mBinder = null;

                sTestNetworkTracker.remove(this.netId);
            }
        }
    }

    private static TestNetworkAgent registerTestNetworkAgent(
            Looper looper, Context context, String intfName, int callingUid, IBinder binder)
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
        nc.setLinkUpstreamBandwidthKbps(1);
        nc.setLinkDownstreamBandwidthKbps(1);
        nc.setNetworkSpecifier(new StringNetworkSpecifier(intfName));

        // Build LinkProperties
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(intfName);

        // Find the currently assigned addresses, and add them to LinkProperties
        boolean allowIPv4 = false, allowIPv6 = false;
        NetworkInterface netIntf = NetworkInterface.getByName(intfName);
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
            lp.addRoute(new RouteInfo(new IpPrefix(Inet4Address.ANY, 0), null, intfName));
        }
        if (allowIPv6) {
            lp.addRoute(new RouteInfo(new IpPrefix(Inet6Address.ANY, 0), null, intfName));
        }

        // Allow bypass so that traffic is routed correctly when multiple test networks are stacked.
        // This is useful for VPN and IPsec testing.
        NetworkMisc networkMisc = new NetworkMisc();
        networkMisc.allowBypass = true;

        return new TestNetworkAgent(looper, context, ni, nc, lp, networkMisc, callingUid, binder);
    }

    /**
     * Build a Network with extremely limited privileges, guarded by the OP_MANAGE_TEST_NETWORKS.
     *
     * <p>This method provides a Network that is useful only for testing.
     */
    @Override
    public synchronized void buildTestNetwork(
            String intfName, IBinder binder, String callingPackage) {
        enforceTestNetworkPermissions(mContext, callingPackage);
        int callingUid = Binder.getCallingUid();

        // Most of this needs to be done with NETWORK_STACK privileges.
        long token = Binder.clearCallingIdentity();
        try {
            mNMS.setInterfaceUp(intfName);
            registerTestNetworkAgent(mHandler.getLooper(), mContext, intfName, callingUid, binder);
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Teardown a test network */
    @Override
    public synchronized void teardownTestNetwork(int netId, String callingPackage) {
        enforceTestNetworkPermissions(mContext, callingPackage);

        TestNetworkAgent agent = sTestNetworkTracker.get(netId);
        if (agent == null) {
            return;
        } else if (agent.mUid != Binder.getCallingUid()) {
            throw new SecurityException("Attempted to modify other user's test networks");
        }

        agent.teardown();
    }

    /** Enforce that the caller has AppOp OP_MANAGE_TEST_NETWORKS. */
    public static void enforceTestNetworkPermissions(Context context, String callingPackage) {
        checkNotNull(callingPackage, "Null calling package cannot create Test Networks");

        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        switch (appOps.noteOp(
                AppOpsManager.OPSTR_MANAGE_TEST_NETWORKS, Binder.getCallingUid(), callingPackage)) {
            case AppOpsManager.MODE_ALLOWED:
                return;
            default:
                throw new SecurityException("AppOp required for test network creation");
        }
    }
}
