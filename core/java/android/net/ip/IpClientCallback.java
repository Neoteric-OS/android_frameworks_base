package android.net.ip;

import android.net.DhcpResults;
import android.net.LinkProperties;

/**
 * Callbacks for handling IpClient events.
 *
 * These methods are called by IpClient on its own thread. Implementations
 * of this class MUST NOT carry out long-running computations or hold locks
 * for which there might be contention with other code calling public
 * methods of the same IpClient instance.
 * @hide
 */
public class IpClientCallback {
    // In order to receive onPreDhcpAction(), call #withPreDhcpAction()
    // when constructing a ProvisioningConfiguration.
    //
    // Implementations of onPreDhcpAction() must call
    // IpClient#completedPreDhcpAction() to indicate that DHCP is clear
    // to proceed.
    public void onPreDhcpAction() {}
    public void onPostDhcpAction() {}

    // This is purely advisory and not an indication of provisioning
    // success or failure.  This is only here for callers that want to
    // expose DHCPv4 results to other APIs (e.g., WifiInfo#setInetAddress).
    // DHCPv4 or static IPv4 configuration failure or success can be
    // determined by whether or not the passed-in DhcpResults object is
    // null or not.
    public void onNewDhcpResults(DhcpResults dhcpResults) {}

    public void onProvisioningSuccess(LinkProperties newLp) {}
    public void onProvisioningFailure(LinkProperties newLp) {}

    // Invoked on LinkProperties changes.
    public void onLinkPropertiesChange(LinkProperties newLp) {}

    // Called when the internal IpReachabilityMonitor (if enabled) has
    // detected the loss of a critical number of required neighbors.
    public void onReachabilityLost(String logMsg) {}

    // Called when the IpClient state machine terminates.
    public void onQuit() {}

    // Install an APF program to filter incoming packets.
    public void installPacketFilter(byte[] filter) {}

    // Asynchronously read back the APF program & data buffer from the wifi driver.
    // Due to Wifi HAL limitations, the current implementation only supports dumping the entire
    // buffer. In response to this request, the driver returns the data buffer asynchronously
    // by sending an IpClient#EVENT_READ_PACKET_FILTER_COMPLETE message.
    public void startReadPacketFilter() {}

    // If multicast filtering cannot be accomplished with APF, this function will be called to
    // actuate multicast filtering using another means.
    public void setFallbackMulticastFilter(boolean enabled) {}

    // Enabled/disable Neighbor Discover offload functionality. This is
    // called, for example, whenever 464xlat is being started or stopped.
    public void setNeighborDiscoveryOffload(boolean enable) {}
}
