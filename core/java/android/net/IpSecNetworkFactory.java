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

package android.net;

import static android.net.ConnectivityManager.TYPE_IPSEC;

import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link android.net.NetworkFactory} that represents IpSec networks.
 *
 * <p>This class reports a static network score of 75 when it is tracking an interface and that
 * interface's link is up, and a score of 0 otherwise.
 *
 * @hide
 */
public class IpSecNetworkFactory extends NetworkFactory {
    private static final String TAG = IpSecNetworkFactory.class.getSimpleName();
    static final boolean DBG = true;

    private final INetworkManagementService mNMService;

    private static final int NETWORK_SCORE = 75;
    private static final String NETWORK_TYPE = "IPsec";

    private final ConcurrentHashMap<String, NetworkInterfaceState> mTrackingInterfaces =
            new ConcurrentHashMap<>();
    private final Handler mHandler;
    private final Context mContext;

    private static NetworkCapabilities getFilter() {
        NetworkCapabilities filter = new NetworkCapabilities();
        filter.clearAll(); // Remove default capabilities.
        filter.addTransportType(NetworkCapabilities.TRANSPORT_IPSEC);

        return filter;
    }

    public IpSecNetworkFactory(Handler handler, Context context) {
        super(handler.getLooper(), context, NETWORK_TYPE, getFilter());

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        mHandler = handler;
        mContext = context;

        setScoreFilter(NETWORK_SCORE);
    }

    @Override
    protected void needNetworkFor(NetworkRequest networkRequest, int score) {
        NetworkInterfaceState network = networkForRequest(networkRequest);

        if (network == null) {
            return;
        }

        if (++network.mRefCount == 1) {
            network.start();
        }
    }

    @Override
    protected void releaseNetworkFor(NetworkRequest networkRequest) {
        NetworkInterfaceState network = networkForRequest(networkRequest);

        if (network == null) {
            return;
        }

        if (--network.mRefCount == 1) {
            network.stop();
        }
    }

    /**
     * Returns an array of available interface names. The array is sorted: unrestricted interfaces
     * goes first, then sorted by name.
     */
    String[] getAvailableInterfaces(boolean includeRestricted) {
        return mTrackingInterfaces
                .values()
                .stream()
                .filter(iface -> !iface.isRestricted() || includeRestricted)
                .sorted(
                        (iface1, iface2) -> {
                            int r = Boolean.compare(iface1.isRestricted(), iface2.isRestricted());
                            return r == 0
                                    ? iface1.mInterfaceName.compareTo(iface2.mInterfaceName)
                                    : r;
                        })
                .map(iface -> iface.mInterfaceName)
                .toArray(String[]::new);
    }

    /** @hide */
    public void addInterface(String ifaceName) {
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.clearAll(); // Remove default capabilities.
        nc.addTransportType(NetworkCapabilities.TRANSPORT_IPSEC);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
        nc.setLinkUpstreamBandwidthKbps(1024 * 1024);
        nc.setLinkDownstreamBandwidthKbps(1024 * 1024);

        addInterface(ifaceName, "01:02:03:04:05:06", nc);
    }

    /** @hide */
    public void addInterface(String ifaceName, String hwAddress, NetworkCapabilities capabilities) {
        InterfaceConfiguration config = null;
        // Bring up the interface so we get link status indications.
        try {
            mNMService.setInterfaceUp(ifaceName);
            config = mNMService.getInterfaceConfig(ifaceName);
        } catch (RemoteException | IllegalStateException e) {
            // Either the system is crashing or the interface has disappeared. Just ignore the
            // error; we haven't modified any state because we only do that if our calls succeed.
            Log.e(TAG, "Error upping interface " + ifaceName, e);
        }

        if (mTrackingInterfaces.containsKey(ifaceName)) {
            Log.e(TAG, "Interface with name " + ifaceName + " already exists.");
            return;
        }

        if (DBG) {
            Log.d(TAG, "addInterface, iface: " + ifaceName + ", capabilities: " + capabilities);
        }

        NetworkInterfaceState iface =
                new NetworkInterfaceState(ifaceName, hwAddress, mHandler, mContext, capabilities);
        // iface.setIpConfig(ipConfiguration);
        mTrackingInterfaces.put(ifaceName, iface);

        updateCapabilityFilter();
    }

    private void updateCapabilityFilter() {
        NetworkCapabilities capabilitiesFilter = new NetworkCapabilities();
        capabilitiesFilter.clearAll();

        for (NetworkInterfaceState iface : mTrackingInterfaces.values()) {
            capabilitiesFilter.combineCapabilities(iface.mCapabilities);
        }

        if (DBG) Log.d(TAG, "updateCapabilityFilter: " + capabilitiesFilter);
        setCapabilityFilter(capabilitiesFilter);
    }

    /** @hide */
    public void removeInterface(String interfaceName) {
        NetworkInterfaceState iface = mTrackingInterfaces.remove(interfaceName);
        if (iface != null) {
            iface.stop();
        }

        updateCapabilityFilter();
    }

    /** Returns true if state has been modified */
    boolean updateInterfaceLinkState(String ifaceName, boolean up) {
        if (!mTrackingInterfaces.containsKey(ifaceName)) {
            return false;
        }

        if (DBG) {
            Log.d(TAG, "updateInterfaceLinkState, iface: " + ifaceName + ", up: " + up);
        }

        NetworkInterfaceState iface = mTrackingInterfaces.get(ifaceName);
        return iface.updateLinkState(up);
    }

    boolean hasInterface(String interfaceName) {
        return mTrackingInterfaces.containsKey(interfaceName);
    }

    // void updateIpConfiguration(String iface, IpConfiguration ipConfiguration) {
    //     NetworkInterfaceState network = mTrackingInterfaces.get(iface);
    //     if (network != null) {
    //         network.setIpConfig(ipConfiguration);
    //     }
    // }

    private NetworkInterfaceState networkForRequest(NetworkRequest request) {
        String requestedIface = null;

        NetworkSpecifier specifier = request.networkCapabilities.getNetworkSpecifier();
        if (specifier instanceof StringNetworkSpecifier) {
            requestedIface = ((StringNetworkSpecifier) specifier).specifier;
        }

        NetworkInterfaceState network = null;
        if (!TextUtils.isEmpty(requestedIface)) {
            NetworkInterfaceState n = mTrackingInterfaces.get(requestedIface);
            if (n != null && n.satisified(request.networkCapabilities)) {
                network = n;
            }
        } else {
            for (NetworkInterfaceState n : mTrackingInterfaces.values()) {
                if (n.satisified(request.networkCapabilities)) {
                    network = n;
                    break;
                }
            }
        }

        if (DBG) {
            Log.i(TAG, "networkForRequest, request: " + request + ", network: " + network);
        }

        return network;
    }

    private static class NetworkInterfaceState {
        final String mInterfaceName;

        private final String mHwAddress;
        private final NetworkCapabilities mCapabilities;
        private final Handler mHandler;
        private final Context mContext;
        private final NetworkInfo mNetworkInfo;

        private static String sTcpBufferSizes = "524288,1048576,3145728,524288,1048576,2097152";

        private boolean mLinkUp;
        private LinkProperties mLinkProperties = new LinkProperties();

        private NetworkAgent mNetworkAgent;

        long mRefCount = 0;

        NetworkInterfaceState(
                String ifaceName,
                String hwAddress,
                Handler handler,
                Context context,
                NetworkCapabilities capabilities) {
            mInterfaceName = ifaceName;
            mCapabilities = capabilities;
            mHandler = handler;
            mContext = context;

            mHwAddress = hwAddress;
            mNetworkInfo = new NetworkInfo(TYPE_IPSEC, 0, NETWORK_TYPE, "");
            mNetworkInfo.setExtraInfo(mHwAddress);
            mNetworkInfo.setIsAvailable(true);
        }

        boolean satisified(NetworkCapabilities requestedCapabilities) {
            return requestedCapabilities.satisfiedByNetworkCapabilities(mCapabilities);
        }

        boolean isRestricted() {
            return false;
        }

        private void start() {
            if (mNetworkAgent != null) {
                Log.e(TAG, "Already have a NetworkAgent - aborting new request");
                stop();
                return;
            }

            mLinkProperties = getDefaultIpSecLinkProperties();
            mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, mHwAddress);
            mNetworkInfo.setIsAvailable(true);

            // Create our NetworkAgent.
            mNetworkAgent =
                    new NetworkAgent(
                            mHandler.getLooper(),
                            mContext,
                            NETWORK_TYPE,
                            mNetworkInfo,
                            mCapabilities,
                            mLinkProperties,
                            NETWORK_SCORE) {
                        public void unwanted() {
                            if (this == mNetworkAgent) {
                                stop();
                            } else if (mNetworkAgent != null) {
                                Log.d(
                                        TAG,
                                        "Ignoring unwanted as we have a more modern " + "instance");
                            } // Otherwise, we've already called stop.
                        }
                    };
        }

        LinkProperties getDefaultIpSecLinkProperties() {
            LinkProperties lp = new LinkProperties();
            lp.setInterfaceName(mInterfaceName);

            // List<LinkAddress> linkAddresses = new ArrayList<>();
            // linkAddresses.add(new LinkAddress("192.168.255.1/32"));
            // lp.setLinkAddresses(linkAddresses);

            // RouteInfo route = new RouteInfo(new IpPrefix("192.168.255.0/24"), null,
            // mInterfaceName);
            // lp.addRoute(route);

            return lp;
        }

        void updateLinkProperties(LinkProperties linkProperties) {
            mLinkProperties = linkProperties;
            if (mNetworkAgent != null) {
                mNetworkAgent.sendLinkProperties(linkProperties);
            }
        }

        /** Returns true if state has been modified */
        boolean updateLinkState(boolean up) {
            if (mLinkUp == up) return false;
            mLinkUp = up;

            stop();
            if (up) {
                start();
            }

            return true;
        }

        void stop() {
            // ConnectivityService will only forget our NetworkAgent if we send it a NetworkInfo
            // object
            // with a state of DISCONNECTED or SUSPENDED. So we can't simply clear our NetworkInfo
            // here:
            // that sets the state to IDLE, and ConnectivityService will still think we're
            // connected.
            //
            mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, mHwAddress);
            mNetworkInfo.setIsAvailable(false);
            if (mNetworkAgent != null) {
                updateAgent();
                mNetworkAgent = null;
            }
            clear();
        }

        private void updateAgent() {
            if (mNetworkAgent == null) return;
            if (DBG) {
                Log.i(
                        TAG,
                        "Updating mNetworkAgent with: "
                                + mCapabilities
                                + ", "
                                + mNetworkInfo
                                + ", "
                                + mLinkProperties);
            }
            mNetworkAgent.sendNetworkCapabilities(mCapabilities);
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            mNetworkAgent.sendLinkProperties(mLinkProperties);
            // never set the network score below 0.
            mNetworkAgent.sendNetworkScore(mLinkUp ? NETWORK_SCORE : 0);
        }

        private void clear() {
            mLinkProperties.clear();
            mNetworkInfo.setDetailedState(DetailedState.IDLE, null, null);
            mNetworkInfo.setIsAvailable(false);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "{ "
                    + "iface: "
                    + mInterfaceName
                    + ", "
                    + "up: "
                    + mLinkUp
                    + ", "
                    + "hwAddress: "
                    + mHwAddress
                    + ", "
                    + "networkInfo: "
                    + mNetworkInfo
                    + ", "
                    + "networkAgent: "
                    + mNetworkAgent
                    + ", "
                    // + "ipClient: " + mIpClient + ","
                    + "linkProperties: "
                    + mLinkProperties
                    + "}";
        }
    }

    void dump(FileDescriptor fd, IndentingPrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println(getClass().getSimpleName());
        pw.println("Tracking interfaces:");
        pw.increaseIndent();
        for (String iface : mTrackingInterfaces.keySet()) {
            NetworkInterfaceState ifaceState = mTrackingInterfaces.get(iface);
            pw.println(iface + ":" + ifaceState);
        }
        pw.decreaseIndent();
    }
}
