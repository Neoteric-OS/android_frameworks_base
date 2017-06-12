/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.provider.Settings.Global.TETHER_OFFLOAD_DISABLED;

import android.content.ContentResolver;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.net.util.SharedLog;
import android.os.Handler;
import android.provider.Settings;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * A class to encapsulate the business logic of programming the tethering
 * hardware offload interface.
 *
 * @hide
 */
public class OffloadController {
    private static final String TAG = OffloadController.class.getSimpleName();

    private final Handler mHandler;
    private final OffloadHardwareInterface mHwInterface;
    private final ContentResolver mContentResolver;
    private final SharedLog mLog;
    private final HashMap<String, LinkProperties> mDownstreams;
    private final HashSet<String> mLocalAddresses;
    private boolean mConfigInitialized;
    private boolean mControlInitialized;
    private LinkProperties mUpstreamLinkProperties;

    public OffloadController(Handler h, OffloadHardwareInterface hwi,
            ContentResolver contentResolver, SharedLog log) {
        mHandler = h;
        mHwInterface = hwi;
        mContentResolver = contentResolver;
        mLog = log.forSubComponent(TAG);
        mDownstreams = new HashMap<>();
        mLocalAddresses = new HashSet<>();
    }

    public void start() {
        if (isOffloadDisabled() || started()) return;

        if (!mConfigInitialized) {
            mConfigInitialized = mHwInterface.initOffloadConfig();
            if (!mConfigInitialized) {
                mLog.i("tethering offload config not supported");
                stop();
                return;
            }
        }

        mControlInitialized = mHwInterface.initOffloadControl(
                new OffloadHardwareInterface.ControlCallback() {
                    @Override
                    public void onOffloadEvent(int event) {
                        mLog.log("got offload event: " + event);
                    }

                    @Override
                    public void onNatTimeoutUpdate(int proto,
                                                   String srcAddr, int srcPort,
                                                   String dstAddr, int dstPort) {
                        mLog.log(String.format("NAT timeout update: %s (%s,%s) -> (%s,%s)",
                                proto, srcAddr, srcPort, dstAddr, dstPort));
                    }
                });
        if (!mControlInitialized) {
            mLog.i("tethering offload control not supported");
            stop();
        }
        mLog.log("tethering offload started");
    }

    public void stop() {
        final boolean hadBeenStarted = started();
        mUpstreamLinkProperties = null;
        mHwInterface.stopOffloadControl();
        mControlInitialized = false;
        mConfigInitialized = false;
        if (hadBeenStarted) mLog.log("tethering offload stopped");
    }

    public void setUpstreamLinkProperties(LinkProperties lp) {
        if (!started()) return;

        mUpstreamLinkProperties = (lp != null) ? new LinkProperties(lp) : null;
        // TODO: examine return code and decide what to do if programming
        // upstream parameters fails (probably just wait for a subsequent
        // onOffloadEvent() callback to tell us offload is available again and
        // then reapply all state).
        pushLocalAddresses();
        pushUpstreamParameters();
    }

    public void notifyDownstreamLinkProperties(LinkProperties newLp) {
        if (!started()) return;

        final String ifname = newLp.getInterfaceName();
        final LinkProperties oldLp = mDownstreams.get(ifname);
        final List<RouteInfo> oldRoutes = (oldLp != null) ? oldLp.getRoutes() : new ArrayList<>();
        final List<RouteInfo> newRoutes = newLp.getRoutes();

        // Push new local addresses first, so there is no window in which the
        // offloading subsystem might mistake them as candidates for theft from
        // regular kernel processing.
        mDownstreams.put(ifname, new LinkProperties(newLp));
        pushLocalAddresses();

        // For each old route, if not in new routes: remove.
        for (RouteInfo oldRoute : oldRoutes) {
            if (!newRoutes.contains(oldRoute)) {
                mHwInterface.removeDownstreamPrefix(ifname, oldRoute.toString());
            }
        }

        // For each new route, if not in old routes: add.
        for (RouteInfo newRoute : newRoutes) {
            if (!oldRoutes.contains(newRoute)) {
                mHwInterface.addDownstreamPrefix(ifname, newRoute.toString());
            }
        }
    }

    public void removeDownstreamInterface(String ifname) {
        if (!started()) return;

        final LinkProperties lp = mDownstreams.remove(ifname);
        if (lp == null) return;

        for (RouteInfo route : lp.getRoutes()) {
            mHwInterface.removeDownstreamPrefix(ifname, route.toString());
        }

        pushLocalAddresses();
    }

    private boolean isOffloadDisabled() {
        // Defaults to |false| if not present.
        return (Settings.Global.getInt(mContentResolver, TETHER_OFFLOAD_DISABLED, 0) != 0);
    }

    private boolean started() {
        return mConfigInitialized && mControlInitialized;
    }

    private boolean pushUpstreamParameters() {
        if (mUpstreamLinkProperties == null) {
            return mHwInterface.setUpstreamParameters(null, null, null, null);
        }

        // A stacked interface cannot be an upstream for hardware offload.
        // Consequently, we examine only the primary interface name, look at
        // getAddresses() rather than getAllAddresses(), and check getRoutes()
        // rather than getAllRoutes().
        final String iface = mUpstreamLinkProperties.getInterfaceName();
        final ArrayList<String> v6gateways = new ArrayList<>();
        String v4addr = null;
        String v4gateway = null;

        for (InetAddress ip : mUpstreamLinkProperties.getAddresses()) {
            if (ip instanceof Inet4Address) {
                v4addr = ip.getHostAddress();
                break;
            }
        }

        // Find the gateway addresses of all default routes of either address family.
        for (RouteInfo ri : mUpstreamLinkProperties.getRoutes()) {
            if (!ri.hasGateway()) continue;

            final String gateway = ri.getGateway().getHostAddress();
            if (ri.isIPv4Default()) {
                v4gateway = gateway;
            } else if (ri.isIPv6Default()) {
                v6gateways.add(gateway);
            }
        }

        return mHwInterface.setUpstreamParameters(iface, v4addr, v4gateway, v6gateways);
    }

    private boolean pushLocalAddresses() {
        final HashSet<String> localAddrs = computeLocalAddresses(
                mUpstreamLinkProperties, mDownstreams);
        if (mLocalAddresses.equals(localAddrs)) return true;

        mLocalAddresses.clear();
        mLocalAddresses.addAll(localAddrs);

        // Yes, this is called "local prefixes" in the parlance of the HAL.
        final ArrayList<String> localPrefixes = new ArrayList<>();
        for (String s : mLocalAddresses) localPrefixes.add(s);
        return mHwInterface.setLocalPrefixes(localPrefixes);
    }

    private static HashSet<String> computeLocalAddresses(
            LinkProperties upstream, HashMap<String, LinkProperties> downstreams) {
        final HashSet<String> localAddrs = new HashSet<>();

        // Add IPv6 addresses from the upstream network (ideally, these will
        // also be assigned to one of the downstreams, but this is not yet
        // implemented). The upstream IPv4 address is excluded here because
        // it's also the source address for NAT'd communications.
        if (upstream != null) {
            for (LinkAddress linkAddr : upstream.getLinkAddresses()) {
                if (!linkAddr.isGlobalPreferred()) continue;
                if (!(linkAddr.getAddress() instanceof Inet6Address)) continue;
                localAddrs.add(linkAddr.toString());
            }
        }

        // Add all downstream configured addresses.
        for (LinkProperties lp : downstreams.values()) {
            for (LinkAddress linkAddr : lp.getLinkAddresses()) {
                if (!linkAddr.isGlobalPreferred()) continue;
                localAddrs.add(linkAddr.toString());
            }
        }

        return localAddrs;
    }
}
