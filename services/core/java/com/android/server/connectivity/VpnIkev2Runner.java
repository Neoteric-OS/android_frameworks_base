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

package com.android.server.connectivity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Ikev2VpnProfile;
import android.net.IpSecManager;
import android.net.IpSecManager.IpSecTunnelInterface;
import android.net.IpSecTransform;
import android.net.LinkAddress;
import android.net.Network;
import android.net.RouteInfo;
import android.net.ipsec.ike.ChildSessionConfiguration;
import android.net.ipsec.ike.ChildSessionParams;
import android.net.ipsec.ike.IkeSession;
import android.net.ipsec.ike.IkeSessionParams;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.INetworkManagementService;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Internal class managing IKEv2/IPsec VPN connectivity
 *
 * <p>The IKEv2 VPN will listen to, and run based on the lifecycle of Android's default Network. As
 * a new default is selected, old IKE sessions will be torn down, and a new one will be started.
 *
 * <p>This class uses locking minimally - the Vpn instance lock is only ever held when fields of the
 * outer class are modified. As such, care must be taken to ensure that no calls are added that
 * might modify the outer class' state without acquiring a lock. All callbacks are run on the
 * mHandler, either by the IKE library (for IKEv2-related callbacks), or by the {@link
 * ConnectivityManager} (for network callbacks).
 *
 * <p>The overall structure of the VpnIkev2Runner is as follows:
 *
 * <ol>
 *   <li>Upon startup, a NetworkRequest is registered with ConnectivityManager. This is called any
 *       time a new default network is selected
 *   <li>When a new default is connected, an IKE session is started on that Network. If there were
 *       any existing IKE sessions on other Networks, they are torn down before starting the new IKE
 *       session
 *   <li>Upon establishment, the onChildTransformCreated() callback is called twice, one for each
 *       direction, and finally onChildOpened() is called
 *   <li>Upon the onChildOpened() call, the VPN is fully set up.
 *   <li>Subsequent Network changes result in new onDefaultNetworkChanged() callbacks. See (2).
 * </ol>
 *
 * @hide
 */
public class VpnIkev2Runner extends Vpn.VpnRunner implements VpnIkev2Utils.VpnIkev2RunnerCallback {
    @NonNull private static final String TAG = VpnIkev2Runner.class.getSimpleName();

    @NonNull private final Context mContext;
    @NonNull private final INetworkManagementService mNetd;
    @NonNull private final Vpn.Ikev2SessionCreator mIkev2SessionCreator;
    @NonNull private final IpSecManager mIpSecManager;
    @NonNull private final Ikev2VpnProfile mProfile;
    @NonNull private final ConnectivityManager.NetworkCallback mNetworkCallback;
    @NonNull private final Vpn.VpnRunnerCallback mVpnCallback;

    /**
     * Handler upon which ALL processing must be run.
     *
     * <p>This handler and associated handler thread help to ensure the consistency of the mutable
     * VpnIkev2Runner fields. The VpnIkev2Runner is built (mostly) lock-free by virtue of everything
     * being serialized on this handler. The exception is the accessing of fields on the outer Vpn
     * instance.
     */
    @NonNull private final Handler mHandler;
    @NonNull private final HandlerThread mHandlerThread;

    /** Signal to ensure shutdown is honored even if a new Network is connected. */
    private boolean mIsRunning = true;

    @Nullable private IpSecTunnelInterface mTunnelIface;
    @Nullable private IkeSession mSession;
    @Nullable private Network mActiveNetwork;

    VpnIkev2Runner(@NonNull Context context, @NonNull INetworkManagementService netService,
            @NonNull Vpn vpn, @NonNull Ikev2VpnProfile profile,
            @NonNull Vpn.VpnRunnerCallback vpnCallback,
            @NonNull Vpn.Ikev2SessionCreator ikev2SessionCreator) {
        super(vpn);
        mContext = context;
        mNetd = netService;
        mIkev2SessionCreator = ikev2SessionCreator;
        mProfile = profile;
        mIpSecManager = (IpSecManager) mContext.getSystemService(Context.IPSEC_SERVICE);
        mNetworkCallback = new VpnIkev2Utils.Ikev2VpnNetworkCallback(TAG, this);
        mVpnCallback = vpnCallback;

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void run() {
        // Explicitly use only the network that ConnectivityService thinks is the "best." In
        // other words, only ever use the currently selected default network. This does mean
        // that in both onLost() and onConnected(), any old sessions MUST be torn down. This
        // does NOT include VPNs.
        final ConnectivityManager cm = ConnectivityManager.from(mContext);
        cm.requestNetwork(cm.getDefaultRequest(), mNetworkCallback, mHandler);
    }

    private boolean isActiveNetwork(@Nullable Network network) {
        return Objects.equals(mActiveNetwork, network) && mIsRunning;
    }

    /**
     * Called when an IKE Child session has been opened, signalling completion of the startup.
     *
     * <p>This method is only ever called once per IkeSession. All IKE callbacks are run on the
     * mHandler by the IKE library, ensuring consistency of the VpnIkev2Runner fields.
     */
    public void onChildOpened(
            @NonNull Network network, @NonNull ChildSessionConfiguration childConfig) {
        if (!isActiveNetwork(network)) {
            Log.d(TAG, "onOpened called for obsolete network " + network);

            // Do nothing; this signals that either: (1) a new/better Network was found,
            // and the VpnIkev2Runner has switched to it in onDefaultNetworkChanged, or (2) this
            // IKE session was already shut down (exited, or an error was encountered somewhere
            // else). In both cases, all resources and sessions are torn down via
            // resetIkeState().
            return;
        }

        try {
            final String interfaceName = mTunnelIface.getInterfaceName();
            final int maxMtu = mProfile.getMaxMtu();
            final List<LinkAddress> internalAddresses = childConfig.getInternalAddresses();
            final List<String> dnsAddrStrings = new ArrayList<>();

            final Collection<RouteInfo> newRoutes = VpnIkev2Utils.getRoutesFromTrafficSelectors(
                    childConfig.getOutboundTrafficSelectors());
            for (final LinkAddress address : internalAddresses) {
                mTunnelIface.addAddress(address.getAddress(), address.getPrefixLength());
            }
            for (InetAddress addr : childConfig.getInternalDnsServers()) {
                dnsAddrStrings.add(addr.getHostAddress());
            }

            mVpnCallback.onConnected(
                    interfaceName, maxMtu, internalAddresses, newRoutes, dnsAddrStrings);
        } catch (Exception e) {
            Log.d(TAG, "Error in ChildOpened for network " + network, e);
            onSessionLost(network);
        }
    }

    /**
     * Called when an IPsec transform has been created, and should be applied.
     *
     * <p>This method is called multiple times over the lifetime of an IkeSession (or default
     * network). All IKE callbacks are run on the mHandler by the IKE library, ensuring consistency
     * of the VpnIkev2Runner fields.
     */
    public void onChildTransformCreated(
            @NonNull Network network, @NonNull IpSecTransform transform, int direction) {
        if (!isActiveNetwork(network)) {
            Log.d(TAG, "ChildTransformCreated for obsolete network " + network);

            // Do nothing; this signals that either: (1) a new/better Network was found,
            // and the VpnIkev2Runner has switched to it in onDefaultNetworkChanged, or (2) this
            // IKE session was already shut down (exited, or an error was encountered somewhere
            // else). In both cases, all resources and sessions are torn down via
            // resetIkeState().
            return;
        }

        try {
            // Transforms do not need to be persisted; the IkeSession will keep
            // them alive for us
            mIpSecManager.applyTunnelModeTransform(mTunnelIface, direction, transform);
        } catch (IOException e) {
            Log.d(TAG, "Transform application failed for network " + network, e);
            onSessionLost(network);
        }
    }

    /**
     * Called when a new default network is connected.
     *
     * <p>The VpnIkev2Runner will unconditionally switch to the new network, killing the old IKE
     * state in the process, and starting a new IkeSession instance.
     *
     * <p>This method is called multiple times over the lifetime of the VpnIkev2Runner, and will be
     * proxied to the mHandler by the Ikev2VpnNetworkCallback, ensuring consistency of the
     * VpnIkev2Runner fields.
     */
    public void onDefaultNetworkChanged(@NonNull Network network) {
        try {
            if (!mIsRunning) {
                Log.d(TAG, "onDefaultNetworkChanged after exit");
                return; // VPN has been shut down.
            }

            // Without MOBIKE, we have no way to seamlessly migrate. Close on old
            // (non-default) network, and start the new one.
            resetIkeState();
            mActiveNetwork = network;

            final IkeSessionParams ikeSessionParams =
                    VpnIkev2Utils.buildIkeSessionParams(mContext, mProfile, network);
            final ChildSessionParams childSessionParams =
                    VpnIkev2Utils.buildChildSessionParams();

            // TODO: Remove the need for adding two unused addresses with
            // IPsec tunnels.
            final InetAddress address = InetAddress.getLocalHost();
            mTunnelIface =
                    mIpSecManager.createIpSecTunnelInterface(
                            address /* unused */,
                            address /* unused */,
                            network);
            mNetd.setInterfaceUp(mTunnelIface.getInterfaceName());

            mSession = mIkev2SessionCreator.createIkeSession(
                    mContext,
                    ikeSessionParams,
                    childSessionParams,
                    new HandlerExecutor(mHandler),
                    new VpnIkev2Utils.IkeSessionCallbackImpl(
                            TAG, VpnIkev2Runner.this, network),
                    new VpnIkev2Utils.ChildSessionCallbackImpl(
                            TAG, VpnIkev2Runner.this, network));
            Log.d(TAG, "Ike Session started for network " + network);
        } catch (Exception e) {
            Log.i(TAG, "Setup failed for network " + network + ". Aborting", e);
            onSessionLost(network);
        }
    }

    /**
     * Handles loss of a session
     *
     * <p>The loss of a session might be due to an onLost() call, the IKE session getting torn down
     * for any reason, or an error in updating state (transform application, VPN setup)
     *
     * <p>This method MUST always be called on the mHandler in order to ensure consistency of the
     * VpnIkev2Runner fields.
     */
    public void onSessionLost(@NonNull Network network) {
        if (!isActiveNetwork(network)) {
            Log.d(TAG, "onSessionLost() called for obsolete network " + network);

            // Do nothing; this signals that either: (1) a new/better Network was found,
            // and the VpnIkev2Runner has switched to it in onDefaultNetworkChanged, or (2) this
            // IKE session was already shut down (exited, or an error was encountered somewhere
            // else). In both cases, all resources and sessions are torn down via
            // onSessionLost() and resetIkeState().
            return;
        }

        mActiveNetwork = null;

        // Close all obsolete state, but keep VPN alive incase a usable network comes up.
        // (Mirrors VpnService behavior)
        Log.d(TAG, "Resetting state for network: " + network);

        mVpnCallback.makeAllRoutesUnreachable();

        // Set as unroutable to prevent traffic leaking while the interface is down.
        resetIkeState();
    }

    /**
     * Cleans up all IKE state
     *
     * <p>This method MUST always be called on the mHandler in order to ensure consistency of the
     * VpnIkev2Runner fields.
     */
    private void resetIkeState() {
        if (mTunnelIface != null) {
            // No need to call setInterfaceDown(); the IpSecInterface is being fully torn down.
            mTunnelIface.close();
            mTunnelIface = null;
        }
        if (mSession != null) {
            mSession.kill(); // Kill here to make sure all resources are released immediately
            mSession = null;
        }
    }

    /**
     * Cleans up all VpnIkev2Runner internal state
     *
     * <p>This method MUST always be called on the mHandler in order to ensure consistency of the
     * VpnIkev2Runner fields.
     */
    private void shutdownVpnRunner() {
        mActiveNetwork = null;
        mIsRunning = false;

        resetIkeState();
        mHandlerThread.quit();

        final ConnectivityManager cm = ConnectivityManager.from(mContext);
        cm.unregisterNetworkCallback(mNetworkCallback);
    }

    @Override
    public void exitVpnRunner() {
        mHandler.post(() -> {
            shutdownVpnRunner();
        });
    }
}
