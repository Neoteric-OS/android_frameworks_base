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

package android.net.ip;

import static android.net.shared.IpConfigurationParcelableUtil.toStableParcelable;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.DhcpResults;
import android.net.DhcpResultsParcelable;
import android.net.InetAddresses;
import android.net.Layer2PacketParcelable;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkStackClient;
import android.net.StaticIpConfiguration;
import android.net.shared.Inet4AddressUtils;
import android.os.ConditionVariable;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

/**
 * Utilities and wrappers to simplify communication with IpClient, which lives in the NetworkStack
 * process.
 *
 * @hide
 */
public class IpClientUtil {
    // TODO: remove with its callers
    public static final String DUMP_ARG = "ipclient";

    /**
     * Subclass of {@link IpClientCallbacks} allowing clients to block until provisioning is
     * complete with {@link WaitForProvisioningCallbacks#waitForProvisioning()}.
     */
    public static class WaitForProvisioningCallbacks extends IpClientCallbacks {
        private final ConditionVariable mCV = new ConditionVariable();
        private LinkProperties mCallbackLinkProperties;

        /**
         * Block until either {@link #onProvisioningSuccess(LinkProperties)} or
         * {@link #onProvisioningFailure(LinkProperties)} is called.
         */
        public LinkProperties waitForProvisioning() {
            mCV.block();
            return mCallbackLinkProperties;
        }

        @Override
        public void onProvisioningSuccess(LinkProperties newLp) {
            mCallbackLinkProperties = newLp;
            mCV.open();
        }

        @Override
        public void onProvisioningFailure(LinkProperties newLp) {
            mCallbackLinkProperties = null;
            mCV.open();
        }
    }

    /**
     * Create a new IpClient.
     *
     * <p>This is a convenience method to allow clients to use {@link IpClientCallbacks} instead of
     * {@link IIpClientCallbacks}.
     * @see {@link NetworkStackClient#makeIpClient(String, IIpClientCallbacks)}
     */
    public static void makeIpClient(Context context, String ifName, IpClientCallbacks callback) {
        // TODO: migrate clients and remove context argument
        NetworkStackClient.getInstance().makeIpClient(ifName, new IpClientCallbacksProxy(callback));
    }

    /**
     * Wrapper to relay calls from {@link IIpClientCallbacks} to {@link IpClientCallbacks}.
     */
    private static class IpClientCallbacksProxy extends IIpClientCallbacks.Stub {
        protected final IpClientCallbacks mCb;

        /**
         * Create a new IpClientCallbacksProxy.
         */
        public IpClientCallbacksProxy(IpClientCallbacks cb) {
            mCb = cb;
        }

        @Override
        public void onIpClientCreated(IIpClient ipClient) {
            mCb.onIpClientCreated(ipClient);
        }

        @Override
        public void onPreDhcpAction() {
            mCb.onPreDhcpAction();
        }

        @Override
        public void onPostDhcpAction() {
            mCb.onPostDhcpAction();
        }

        // This is purely advisory and not an indication of provisioning
        // success or failure.  This is only here for callers that want to
        // expose DHCPv4 results to other APIs (e.g., WifiInfo#setInetAddress).
        // DHCPv4 or static IPv4 configuration failure or success can be
        // determined by whether or not the passed-in DhcpResults object is
        // null or not.
        @Override
        public void onNewDhcpResults(DhcpResultsParcelable dhcpResults) {
            mCb.onNewDhcpInfo(fromStableParcelable(dhcpResults));
        }

        @Override
        public void onProvisioningSuccess(LinkProperties newLp) {
            mCb.onProvisioningSuccess(newLp);
        }
        @Override
        public void onProvisioningFailure(LinkProperties newLp) {
            mCb.onProvisioningFailure(newLp);
        }

        // Invoked on LinkProperties changes.
        @Override
        public void onLinkPropertiesChange(LinkProperties newLp) {
            mCb.onLinkPropertiesChange(newLp);
        }

        // Called when the internal IpReachabilityMonitor (if enabled) has
        // detected the loss of a critical number of required neighbors.
        @Override
        public void onReachabilityLost(String logMsg) {
            mCb.onReachabilityLost(logMsg);
        }

        // Called when the IpClient state machine terminates.
        @Override
        public void onQuit() {
            mCb.onQuit();
        }

        // Install an APF program to filter incoming packets.
        @Override
        public void installPacketFilter(byte[] filter) {
            mCb.installPacketFilter(filter);
        }

        // Asynchronously read back the APF program & data buffer from the wifi driver.
        // Due to Wifi HAL limitations, the current implementation only supports dumping the entire
        // buffer. In response to this request, the driver returns the data buffer asynchronously
        // by sending an IpClient#EVENT_READ_PACKET_FILTER_COMPLETE message.
        @Override
        public void startReadPacketFilter() {
            mCb.startReadPacketFilter();
        }

        // If multicast filtering cannot be accomplished with APF, this function will be called to
        // actuate multicast filtering using another means.
        @Override
        public void setFallbackMulticastFilter(boolean enabled) {
            mCb.setFallbackMulticastFilter(enabled);
        }

        // Enabled/disable Neighbor Discover offload functionality. This is
        // called, for example, whenever 464xlat is being started or stopped.
        @Override
        public void setNeighborDiscoveryOffload(boolean enable) {
            mCb.setNeighborDiscoveryOffload(enable);
        }

        // Invoked on starting preconnection process.
        @Override
        public void onPreconnectionStart(List<Layer2PacketParcelable> packets) {
            mCb.onPreconnectionStart(packets);
        }

        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }
    }

    // TODO: Find a more proper place to put this method.
    /**
     * Convert a DhcpResultsParcelable to DhcpInfo.
     */
    @Nullable
    public static DhcpInfo fromStableParcelable(@NonNull DhcpResultsParcelable p) {
        if (p == null) return null;

        final DhcpInfo info = new DhcpInfo();
        final StaticIpConfiguration cfg = new StaticIpConfiguration(p.baseConfiguration);
        final LinkAddress linkAddr = cfg.getIpAddress();
        final InetAddress addr = linkAddr.getAddress();

        if (linkAddr != null && addr instanceof Inet4Address) {
            info.ipAddress = addrInet4ToInt(
                    (Inet4Address) cfg.getIpAddress().getAddress());
        }
        if (cfg.getGateway() != null) {
            info.gateway = addrInet4ToInt(
                    (Inet4Address) cfg.getGateway());
        }

        int dnsFound = 0;
        for (InetAddress dns : cfg.getDnsServers()) {
            if (dns instanceof Inet4Address) {
                if (dnsFound == 0) {
                    info.dns1 = addrInet4ToInt((Inet4Address) dns);
                } else {
                    info.dns2 = addrInet4ToInt((Inet4Address) dns);
                }
                if (++dnsFound > 1) break;
            }
        }

        if (p.serverAddress == null) {
            info.serverAddress = 0;
        } else {
            final Inet4Address serverAddress =
                    (Inet4Address) InetAddresses.parseNumericAddress(p.serverAddress);
            info.serverAddress = addrInet4ToInt(serverAddress);
        }

        info.domains = cfg.getDomains();
        info.leaseDuration = p.leaseDuration;
        info.mtu = p.mtu;
        info.vendorInfo = p.vendorInfo;
        info.serverHostName = p.serverHostName;

        return info;
    }

    private static int addrInet4ToInt(Inet4Address addr) {
        return Inet4AddressUtils.inet4AddressToIntHTL(addr);
    }

    /**
     * Converts a DhcpResults instance into a DhcpInfo instance.
     */
    @VisibleForTesting
    public static DhcpInfo convertDhcpResultsToDhcpInfo(DhcpResults results) {
        if (results == null) return null;

        return fromStableParcelable(toStableParcelable(results));
    }

    /**
     * Dump logs for the specified IpClient.
     * TODO: remove callers and delete
     */
    public static void dumpIpClient(
            IIpClient connector, FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IpClient logs have moved to dumpsys network_stack");
    }
}
