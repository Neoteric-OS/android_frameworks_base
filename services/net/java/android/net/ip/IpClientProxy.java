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

package android.net.ip;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.IIpClientCallbacks;
import android.net.INetd;
import android.net.LinkProperties;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.util.InterfaceParams;
import android.net.util.NetdService;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.IState;
import com.android.internal.util.Preconditions;
import com.android.internal.util.State;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;


/**
 * IpClient
 *
 * This class provides the interface to IP-layer provisioning and maintenance
 * functionality that can be used by transport layers like Wi-Fi, Ethernet,
 * et cetera.
 *
 * [ Lifetime ]
 * IpClient is designed to be instantiated as soon as the interface name is
 * known and can be as long-lived as the class containing it (i.e. declaring
 * it "private final" is okay).
 *
 * @hide
 */
public class IpClientProxy {
    private static final int CMD_STOP                             = 2;
    private static final int CMD_START                            = 3;
    private static final int CMD_CONFIRM                          = 4;
    private static final int EVENT_PRE_DHCP_ACTION_COMPLETE       = 5;
    private static final int CMD_UPDATE_TCP_BUFFER_SIZES          = 7;
    private static final int CMD_UPDATE_HTTP_PROXY                = 8;
    private static final int CMD_SET_MULTICAST_FILTER             = 9;
    private static final int EVENT_READ_PACKET_FILTER_COMPLETE    = 12;

    private static final int CMD_SHUTDOWN = 13;

    private static final boolean DBG = false;
    private static final String TAG = IpClientProxy.class.getName();

    // If |args| is non-empty, assume it's a list of interface names for which
    // we should print IpClient logs (filter out all others).
    public static void dumpAllLogs(PrintWriter writer, String[] args) {
        // TODO: support this
        /*
        for (String ifname : sSmLogs.keySet()) {
            if (!ArrayUtils.isEmpty(args) && !ArrayUtils.contains(args, ifname)) continue;

            writer.println(String.format("--- BEGIN %s ---", ifname));

            final SharedLog smLog = sSmLogs.get(ifname);
            if (smLog != null) {
                writer.println("State machine log:");
                smLog.dump(null, writer, null);
            }

            writer.println("");

            final LocalLog pktLog = sPktLogs.get(ifname);
            if (pktLog != null) {
                writer.println("Connectivity packet log:");
                pktLog.readOnlyLocalLog().dump(null, writer, null);
            }

            writer.println(String.format("--- END %s ---", ifname));
        }
        */
    }

    public static class WaitForProvisioningCallback extends IpClientCallback {
        private final ConditionVariable mCV = new ConditionVariable();
        private LinkProperties mCallbackLinkProperties;

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

    public static final String DUMP_ARG = "ipclient";
    public static final String DUMP_ARG_CONFIRM = "confirm";

    private final CountDownLatch mShutdownLatch;

    public static class Dependencies {
        public INetworkManagementService getNMS() {
            return INetworkManagementService.Stub.asInterface(
                    ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE));
        }

        public INetd getNetd() {
            return NetdService.getInstance();
        }

        public InterfaceParams getInterfaceParams(String ifname) {
            return InterfaceParams.getByName(ifname);
        }
    }

    private IIpClient mIpClient;
    private final ArrayList<PendingRequest> mPendingMessages = new ArrayList<>();

    private static class PendingRequest {
        private final int mCode;
        private final Object mObj;
        public PendingRequest(int code, Object obj) {
            mCode = code;
            mObj = obj;
        }

        public PendingRequest(int code) {
            this(code, null);
        }
    }

    public IpClientProxy(Context context, final String ifName, IpClientCallback callback) {
        final ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        cm.requestConnectivityApp(app -> {
            try {
                app.makeIpClient(ifName, new IpClientCallbacks(callback));
            } catch (RemoteException e) {
                Log.e(TAG, "Error creating IpClient", e);
            }
        });

        mShutdownLatch = new CountDownLatch(1);
    }

    private void queueRequest(PendingRequest m) {
        synchronized (mPendingMessages) {
            if (mIpClient == null) {
                mPendingMessages.add(m);
                return;
            }
        }

        processMessage(m, mIpClient);
    }

    private static void processMessage(PendingRequest m, IIpClient client) {
        try {
            switch (m.mCode) {
                case EVENT_PRE_DHCP_ACTION_COMPLETE:
                    client.completedPreDhcpAction();
                    break;
                case CMD_CONFIRM:
                    client.confirmConfiguration();
                    break;
                case EVENT_READ_PACKET_FILTER_COMPLETE:
                    client.readPacketFilterComplete((byte[]) m.mObj);
                    break;
                case CMD_STOP:
                    client.stop();
                    break;
                case CMD_SHUTDOWN:
                    client.shutdown();
                    break;
                case CMD_START:
                    client.startProvisioning((ProvisioningConfiguration) m.mObj);
                    break;
                case CMD_UPDATE_TCP_BUFFER_SIZES:
                    client.setTcpBufferSizes((String) m.mObj);
                    break;
                case CMD_UPDATE_HTTP_PROXY:
                    client.setHttpProxy((ProxyInfo) m.mObj);
                    break;
                case CMD_SET_MULTICAST_FILTER:
                    client.setMulticastFilter((boolean) m.mObj);
                    break;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Error processing message " + m.mCode);
        }
    }

    private class IpClientCallbacks extends IIpClientCallbacks.Stub {
        private final IpClientCallback mCb;
        IpClientCallbacks(IpClientCallback cb) {
            mCb = cb;
        }

        @Override
        public void onIpClientCreated(IBinder ipClient) {
            mIpClient = IIpClient.Stub.asInterface(ipClient);
            final ArrayList<PendingRequest> messages;
            synchronized (mPendingMessages) {
                messages = new ArrayList<>(mPendingMessages);
                mPendingMessages.clear();
            }

            for (PendingRequest m : messages) {
                processMessage(m, mIpClient);
            }
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
        public void onNewDhcpResults(DhcpResults dhcpResults) {
            mCb.onNewDhcpResults(dhcpResults);
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
            mShutdownLatch.countDown();
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
    }

    // Shut down this IpClient instance altogether.
    public void shutdown() {
        queueRequest(new PendingRequest(CMD_SHUTDOWN));
    }

    // In order to avoid deadlock, this method MUST NOT be called on the
    // IpClient instance's thread. This prohibition includes code executed by
    // when methods on the passed-in IpClient.Callback instance are called.
    public void awaitShutdown() {
        try {
            mShutdownLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while awaiting shutdown", e);
        }
    }

    public static ProvisioningConfiguration.Builder buildProvisioningConfiguration() {
        return new ProvisioningConfiguration.Builder();
    }

    public void startProvisioning(ProvisioningConfiguration req) {
        queueRequest(new PendingRequest(CMD_START, req));
    }

    // TODO: Delete this.
    public void startProvisioning(StaticIpConfiguration staticIpConfig) {
        startProvisioning(buildProvisioningConfiguration()
                .withStaticConfiguration(staticIpConfig)
                .build());
    }

    public void startProvisioning() {
        startProvisioning(new ProvisioningConfiguration());
    }

    public void stop() {
        queueRequest(new PendingRequest(CMD_STOP));
    }

    public void confirmConfiguration() {
        queueRequest(new PendingRequest(CMD_CONFIRM));
    }

    public void completedPreDhcpAction() {
        queueRequest(new PendingRequest(EVENT_PRE_DHCP_ACTION_COMPLETE));
    }

    public void readPacketFilterComplete(byte[] data) {
        queueRequest(new PendingRequest(EVENT_READ_PACKET_FILTER_COMPLETE, data));
    }

    /**
     * Set the TCP buffer sizes to use.
     *
     * This may be called, repeatedly, at any time before or after a call to
     * #startProvisioning(). The setting is cleared upon calling #stop().
     */
    public void setTcpBufferSizes(String tcpBufferSizes) {
        queueRequest(new PendingRequest(CMD_UPDATE_TCP_BUFFER_SIZES, tcpBufferSizes));
    }

    /**
     * Set the HTTP Proxy configuration to use.
     *
     * This may be called, repeatedly, at any time before or after a call to
     * #startProvisioning(). The setting is cleared upon calling #stop().
     */
    public void setHttpProxy(ProxyInfo proxyInfo) {
        queueRequest(new PendingRequest(CMD_UPDATE_HTTP_PROXY, proxyInfo));
    }

    /**
     * Enable or disable the multicast filter.  Attempts to use APF to accomplish the filtering,
     * if not, Callback.setFallbackMulticastFilter() is called.
     */
    public void setMulticastFilter(boolean enabled) {
        queueRequest(new PendingRequest(CMD_SET_MULTICAST_FILTER, enabled));
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        // TODO: support this
        /*
        if (args != null && args.length > 0 && DUMP_ARG_CONFIRM.equals(args[0])) {
            // Execute confirmConfiguration() and take no further action.
            confirmConfiguration();
            return;
        }

        // Thread-unsafe access to mApfFilter but just used for debugging.
        final ApfFilter apfFilter = mApfFilter;
        final ProvisioningConfiguration provisioningConfig = mConfiguration;
        final ApfCapabilities apfCapabilities = (provisioningConfig != null)
                ? provisioningConfig.mApfCapabilities : null;

        IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println(mTag + " APF dump:");
        pw.increaseIndent();
        if (apfFilter != null) {
            if (apfCapabilities.hasDataAccess()) {
                // Request a new snapshot, then wait for it.
                mApfDataSnapshotComplete.close();
                mCallback.startReadPacketFilter();
                if (!mApfDataSnapshotComplete.block(1000)) {
                    pw.print("TIMEOUT: DUMPING STALE APF SNAPSHOT");
                }
            }
            apfFilter.dump(pw);

        } else {
            pw.print("No active ApfFilter; ");
            if (provisioningConfig == null) {
                pw.println("IpClient not yet started.");
            } else if (apfCapabilities == null || apfCapabilities.apfVersionSupported == 0) {
                pw.println("Hardware does not support APF.");
            } else {
                pw.println("ApfFilter not yet started, APF capabilities: " + apfCapabilities);
            }
        }
        pw.decreaseIndent();
        pw.println();
        pw.println(mTag + " current ProvisioningConfiguration:");
        pw.increaseIndent();
        pw.println(Objects.toString(provisioningConfig, "N/A"));
        pw.decreaseIndent();

        final IpReachabilityMonitor iprm = mIpReachabilityMonitor;
        if (iprm != null) {
            pw.println();
            pw.println(mTag + " current IpReachabilityMonitor state:");
            pw.increaseIndent();
            iprm.dump(pw);
            pw.decreaseIndent();
        }

        pw.println();
        pw.println(mTag + " StateMachine dump:");
        pw.increaseIndent();
        mLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println();
        pw.println(mTag + " connectivity packet log:");
        pw.println();
        pw.println("Debug with python and scapy via:");
        pw.println("shell$ python");
        pw.println(">>> from scapy import all as scapy");
        pw.println(">>> scapy.Ether(\"<paste_hex_string>\".decode(\"hex\")).show2()");
        pw.println();

        pw.increaseIndent();
        mConnectivityPacketLog.readOnlyLocalLog().dump(fd, pw, args);
        pw.decreaseIndent();
        */
    }

    private static class MessageHandlingLogger {
        public String processedInState;
        public String receivedInState;

        public void reset() {
            processedInState = null;
            receivedInState = null;
        }

        public void handled(State processedIn, IState receivedIn) {
            processedInState = processedIn.getClass().getSimpleName();
            receivedInState = receivedIn.getName();
        }

        public String toString() {
            return String.format("rcvd_in=%s, proc_in=%s",
                                 receivedInState, processedInState);
        }
    }

    private static void setNeighborParameters(
            INetd netd, String ifName, int num_solicits, int inter_solicit_interval_ms)
            throws RemoteException, IllegalArgumentException {
        Preconditions.checkNotNull(netd);
        Preconditions.checkArgument(!TextUtils.isEmpty(ifName));
        Preconditions.checkArgument(num_solicits > 0);
        Preconditions.checkArgument(inter_solicit_interval_ms > 0);

        for (int family : new Integer[]{INetd.IPV4, INetd.IPV6}) {
            netd.setProcSysNet(family, INetd.NEIGH, ifName, "retrans_time_ms", Integer.toString(inter_solicit_interval_ms));
            netd.setProcSysNet(family, INetd.NEIGH, ifName, "ucast_solicit", Integer.toString(num_solicits));
        }
    }

}
