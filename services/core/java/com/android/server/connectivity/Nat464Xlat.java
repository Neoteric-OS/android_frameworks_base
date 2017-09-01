/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.net.InterfaceConfiguration;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.util.ArrayUtils;
import com.android.server.net.BaseNetworkObserver;

import java.net.Inet4Address;
import java.util.Objects;

/**
 * Class to manage a 464xlat CLAT daemon. Nat464Xlat is not thread safe and should be manipulated
 * from a consistent and unique thread context. It is the responsibility of ConnectivityService to
 * call into this class from its own Handler thread.
 *
 * @hide
 */
public class Nat464Xlat extends BaseNetworkObserver {
    private static final String TAG = Nat464Xlat.class.getSimpleName();

    // This must match the interface prefix in clatd.c.
    private static final String CLAT_PREFIX = "v4-";

    // The network types we will start clatd on,
    // allowing clat only on networks for which we can support IPv6-only.
    private static final int[] NETWORK_TYPES = {
            ConnectivityManager.TYPE_MOBILE,
            ConnectivityManager.TYPE_WIFI,
            ConnectivityManager.TYPE_ETHERNET,
    };

    private final INetworkManagementService mNMService;

    // The network we're running on, and its type.
    private final NetworkAgentInfo mNetwork;

    private enum State {
        IDLE,       // start() not called. Base iface and stacked iface names are null.
        STARTING,   // start() called. Base iface and stacked iface names are known.
        RUNNING;    // start() called, and the stacked iface is known to be up.
    }

    private String mBaseIface;
    private String mIface;
    private State mState = State.IDLE;

    public Nat464Xlat(INetworkManagementService nmService, NetworkAgentInfo nai) {
        mNMService = nmService;
        mNetwork = nai;
    }

    /**
     * Determines whether a network requires clat.
     * @param network the NetworkAgentInfo corresponding to the network.
     * @return true if the network requires clat, false otherwise.
     */
    public static boolean requiresClat(NetworkAgentInfo nai) {
        // TODO: migrate to NetworkCapabilities.TRANSPORT_*.
        final int netType = nai.networkInfo.getType();
        final boolean supported = ArrayUtils.contains(NETWORK_TYPES, nai.networkInfo.getType());
        // TODO: this should also consider if the network is in SUSPENDED state to avoid stopping
        // clatd in SUSPENDED state.
        final boolean connected = nai.networkInfo.isConnected();
        // We only run clat on networks that don't have a native IPv4 address.
        final boolean hasIPv4Address =
                (nai.linkProperties != null) && nai.linkProperties.hasIPv4Address();
        return supported && connected && !hasIPv4Address;
    }

    /**
     * @return true if clatd has been started and has not yet stopped.
     * A true result corresponds to internal states STARTING and RUNNING.
     */
    public boolean isStarted() {
        return mState != State.IDLE;
    }

    /**
     * @return true if clatd has been started but the stacked interface is not yet up.
     */
    public boolean isStarting() {
        return mState == State.STARTING;
    }

    /**
     * @return true if clatd has been started and the stacked interface is up.
     */
    public boolean isRunning() {
        return mState == State.RUNNING;
    }

    /**
     * Start clatd, register this Nat464Xlat as a network observer for the stacked interface,
     * and set internal state.
     */
    private void enterStartingState(String baseIface) {
        try {
            mNMService.registerObserver(this);
        } catch(RemoteException e) {
            Slog.e(TAG,
                    "startClat: Can't register interface observer for clat on " + mNetwork.name());
            return;
        }
        try {
            mNMService.startClatd(baseIface);
        } catch(RemoteException|IllegalStateException e) {
            Slog.e(TAG, "Error starting clatd on " + baseIface, e);
        }
        mIface = CLAT_PREFIX + baseIface;
        mBaseIface = baseIface;
        mState = State.STARTING;
    }

    /**
     * Enter running state just after getting confirmation that the stacked interface is up, and
     * turn ND offload off if on WiFi.
     */
    private void enterRunningState() {
        maybeSetIpv6NdOffload(mBaseIface, false);
        mState = State.RUNNING;
    }

    /**
     * Unregister this Nat464Xlat as an observer for the stacked interface, stop clatd, turn ND
     * offload on if it had been turned off, and clears internal state.
     */
    private void enterIdleState() {
        try {
            mNMService.unregisterObserver(this);
        } catch(RemoteException|IllegalStateException e) {
            Slog.e(TAG, "Error unregistering clatd observer on " + mBaseIface, e);
        }
        try {
            mNMService.stopClatd(mBaseIface);
        } catch(RemoteException|IllegalStateException e) {
            Slog.e(TAG, "Error stopping clatd on " + mBaseIface, e);
        }
        if (isRunning()) {
            maybeSetIpv6NdOffload(mBaseIface, true);
        }
        mIface = null;
        mBaseIface = null;
        mState = State.IDLE;
    }

    /**
     * Starts the clat daemon.
     */
    public void start() {
        if (isStarted()) {
            Slog.e(TAG, "startClat: already started");
            return;
        }

        if (mNetwork.linkProperties == null) {
            Slog.e(TAG, "startClat: Can't start clat with null LinkProperties");
            return;
        }

        String baseIface = mNetwork.linkProperties.getInterfaceName();
        if (baseIface == null) {
            Slog.e(TAG, "startClat: Can't start clat on null interface");
            return;
        }
        // TODO: should we only do this if mNMService.startClatd() succeeds?
        Slog.i(TAG, "Starting clatd on " + baseIface);
        enterStartingState(baseIface);
    }

    /**
     * Stops the clat daemon.
     * @param shouldNotify if true, ConnectivityService#handleUpdateLinkProperties will be called
     * with a new LinkProperties object updated to not have the stacked interface. The update is
     * not synchronous but is scheduled for later on the handler.
     */
    public void stop(boolean shouldNotify) {
        if (!isStarted()) {
            return;
        }
        Slog.i(TAG, "Stopping clatd on " + mBaseIface);
        if (shouldNotify && isRunning()) {
            // Only update ConnectivityService if it had been previously notified when the stacked
            // interface went up.
            mNetwork.handler().post(() -> { updateConnectivityService(mIface); });
        }
        enterIdleState();
    }

    private void updateConnectivityService(String iface) {
        LinkProperties lp = new LinkProperties(mNetwork.linkProperties);
        lp.removeStackedLink(iface);
        mNetwork.connService().handleUpdateLinkProperties(mNetwork, lp);
    }

    /**
     * Copies the stacked clat link in oldLp, if any, to the LinkProperties in mNetwork.
     * This is necessary because the LinkProperties in mNetwork come from the transport layer, which
     * has no idea that 464xlat is running on top of it.
     */
    public void fixupLinkProperties(LinkProperties oldLp) {
        if (!isRunning()) {
            return;
        }
        LinkProperties lp = mNetwork.linkProperties;
        if (lp == null || lp.getAllInterfaceNames().contains(mIface)) {
            return;
        }

        Slog.d(TAG, "clatd running, updating NAI for " + mIface);
        for (LinkProperties stacked: oldLp.getStackedLinks()) {
            if (Objects.equals(mIface, stacked.getInterfaceName())) {
                lp.addStackedLink(stacked);
                return;
            }
        }
    }

    private LinkProperties makeLinkProperties(LinkAddress clatAddress) {
        LinkProperties stacked = new LinkProperties();
        stacked.setInterfaceName(mIface);

        // Although the clat interface is a point-to-point tunnel, we don't
        // point the route directly at the interface because some apps don't
        // understand routes without gateways (see, e.g., http://b/9597256
        // http://b/9597516). Instead, set the next hop of the route to the
        // clat IPv4 address itself (for those apps, it doesn't matter what
        // the IP of the gateway is, only that there is one).
        RouteInfo ipv4Default = new RouteInfo(
                new LinkAddress(Inet4Address.ANY, 0),
                clatAddress.getAddress(), mIface);
        stacked.addRoute(ipv4Default);
        stacked.addLinkAddress(clatAddress);
        return stacked;
    }

    private LinkAddress getLinkAddress(String iface) {
        try {
            InterfaceConfiguration config = mNMService.getInterfaceConfig(iface);
            return config.getLinkAddress();
        } catch(RemoteException|IllegalStateException e) {
            Slog.e(TAG, "Error getting link properties: " + e);
            return null;
        }
    }

    private void maybeSetIpv6NdOffload(String iface, boolean on) {
        // TODO: migrate to NetworkCapabilities.TRANSPORT_*.
        if (mNetwork.networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
            return;
        }
        try {
            Slog.d(TAG, (on ? "En" : "Dis") + "abling ND offload on " + iface);
            mNMService.setInterfaceIpv6NdOffload(iface, on);
        } catch(RemoteException|IllegalStateException e) {
            Slog.w(TAG, "Changing IPv6 ND offload on " + iface + "failed: " + e);
        }
    }

    /**
     * Adds stacked link on base link and transitions to RUNNING state.
     */
    private void handleInterfaceLinkStateChanged(String iface, boolean up) {
        if (!isStarting() || !up || !Objects.equals(mIface, iface)) {
            return;
        }
        LinkAddress clatAddress = getLinkAddress(iface);
        if (clatAddress == null) {
            Slog.e(TAG, "clatAddress was null for stacked iface " + iface);
            return;
        }

        Slog.i(TAG, String.format("interface %s is up, adding stacked link %s on top of %s",
                mIface, mIface, mBaseIface));
        enterRunningState();
        LinkProperties lp = new LinkProperties(mNetwork.linkProperties);
        lp.addStackedLink(makeLinkProperties(clatAddress));
        mNetwork.connService().handleUpdateLinkProperties(mNetwork, lp);
    }

    /**
     * Removes stacked link on base link and transitions to IDLE state.
     */
    private void handleInterfaceRemoved(String iface) {
        if (!isRunning() || !Objects.equals(mIface, iface)) {
            return;
        }

        // The interface going away before stop() was called likely means clatd has crashed.
        // Ask netd to stop clatd, so that netd can restart it on the same interface later,
        // and notify ConnectivityService about the stacked interface removal.
        Slog.i(TAG, "interface " + iface + " removed");
        enterIdleState();
        updateConnectivityService(iface);
    }

    @Override
    public void interfaceLinkStateChanged(String iface, boolean up) {
        mNetwork.handler().post(() -> { handleInterfaceLinkStateChanged(iface, up); });
    }

    @Override
    public void interfaceRemoved(String iface) {
        mNetwork.handler().post(() -> { handleInterfaceRemoved(iface); });
    }

    @Override
    public String toString() {
        return "mBaseIface: " + mBaseIface + ", mIface: " + mIface + ", mState: " + mState;
    }
}
