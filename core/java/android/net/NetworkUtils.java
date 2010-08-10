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

package android.net;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;

import android.util.Log;

/**
 * Native methods for managing network interfaces.
 *
 * {@hide}
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /** Bring the named network interface up. */
    public native static int enableInterface(String interfaceName);

    /** Bring the named network interface down. */
    public native static int disableInterface(String interfaceName);

    /** Add a route to the specified host or gateway via the named interface. */
    public native static int addRoute(String interfaceName, String dst,
          int prefixLength, String gw);

    /** Return the gateway address for the default route for the named interface. */
    public native static int getDefaultRoute(String interfaceName);

    /** Remove host routes that uses the named interface. */
    public native static int removeHostRoutes(String interfaceName);

    /** Remove the default route for the named interface. */
    public native static int removeDefaultRoute(String interfaceName);

    /** Reset any sockets that are connected via the named interface. */
    public native static int resetConnections(String interfaceName);

    /**
     * Start the DHCP client daemon, in order to have it request addresses
     * for the named interface, and then configure the interface with those
     * addresses. This call blocks until it obtains a result (either success
     * or failure) from the daemon.
     * @param interfaceName the name of the interface to configure
     * @param ipInfo if the request succeeds, this object is filled in with
     * the IP address information.
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean runDhcp(String interfaceName, DhcpInfo ipInfo);

    /**
     * Shut down the DHCP client daemon.
     * @param interfaceName the name of the interface for which the daemon
     * should be stopped
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean stopDhcp(String interfaceName);

    /**
     * Release the current DHCP lease.
     * @param interfaceName the name of the interface for which the lease should
     * be released
     * @return {@code true} for success, {@code false} for failure
     */
    public native static boolean releaseDhcpLease(String interfaceName);

    /**
     * Return the last DHCP-related error message that was recorded.
     * <p/>NOTE: This string is not localized, but currently it is only
     * used in logging.
     * @return the most recent error message, if any
     */
    public native static String getDhcpError();

    /**
     * When static IP configuration has been specified, configure the network
     * interface according to the values supplied.
     * @param interfaceName the name of the interface to configure
     * @param ipInfo the IP address, default gateway, and DNS server addresses
     * with which to configure the interface.
     * @return {@code true} for success, {@code false} for failure
     */
    public static boolean configureInterface(String interfaceName, DhcpInfo ipInfo) {
        return configureNative(interfaceName,
            ipInfo.ipAddress,
            ipInfo.netmask,
            ipInfo.gateway,
            ipInfo.dns1,
            ipInfo.dns2);
    }

    private native static boolean configureNative(
        String interfaceName, int ipAddress, int netmask, int gateway, int dns1, int dns2);

    /**
     * Convert a IPv4 address from an integer to an InetAddress.
     * @param hostAddr is an Int corresponding to the IPv4 address in network byte order
     * @return the IP address as an {@code InetAddress}, returns null if
     * unable to convert or if the int is an invalid address.
     */
    public static InetAddress intToInetAddress(int hostAddress) {
        InetAddress inetAddress;
        String hostName;

        hostName = "" + (0xff & hostAddress) + "." + (0xff & (hostAddress >> 8)) + "." +
            (0xff & (hostAddress >> 16)) + "." + (0xff & (hostAddress >> 24));

        try {
            inetAddress = InetAddress.getByName(hostName);
        } catch (UnknownHostException e) {
            return null;
        }

        return inetAddress;
    }

    /**
     * Add a route to the specified gateway.
     * @param interfaceName interface on which the route should be added
     * @param gw the IP address of the gateway to which the route is desired,
     * @param prefixLength specifies default or host route, value=32/128 for IPv4/IPv6
     * host route respectively and value=0 for default IPv4/IPv6 route to a gateway.
     * @return {@code true} on success, {@code false} on failure
     */
    public static boolean addRoute(String interfaceName, InetAddress gw,
          int prefixLength) {
        String dstStr;
        String gwStr = gw.getHostAddress();

        if (gw instanceof Inet4Address) {
            dstStr = "0.0.0.0";
        } else if (gw instanceof Inet6Address) {
            dstStr = "::";
        } else {
            Log.w(TAG, "addRoute failure: address is neither IPv4 nor IPv6" +
                       "(" + gwStr + ")");
            return false;
        }
        return addRoute(interfaceName, dstStr, prefixLength, gwStr) == 0;
    }

    /**
     * Add a route to the specified host/gateway.
     * @param interfaceName interface on which the route should be added
     * @param dst the IP address of the host to which the route is desired,
     * @param prefixLength specifies default or host route, value=32/128 for IPv4/IPv6
     * host route respectively and value=0 for default IPv4/IPv6 route to a gateway.
     * @param gw the IP address of the gateway to which the route is desired,
     * @return {@code true} on success, {@code false} on failure
     */
    public static boolean addRoute(String interfaceName, InetAddress dst,
          int prefixLength, InetAddress gw) {
        String dstStr = dst.getHostAddress();
        String gwStr = gw.getHostAddress();
        return addRoute(interfaceName, dstStr, prefixLength, gwStr) == 0;
    }

    /**
     * Add a route to the specified host via the named interface.
     * @param interfaceName interface on which the route should be added
     * @param hostAddress the IP address to which the route is desired,
     * @return {@code true} on success, {@code false} on failure
     */
    public static boolean addHostRoute(String interfaceName, InetAddress hostAddress) {
        int prefixLength;
        String dst = hostAddress.getHostAddress();

        if (hostAddress instanceof Inet4Address) {
            prefixLength = 32;
        } else if (hostAddress instanceof Inet6Address) {
            prefixLength = 128;
        } else {
            Log.w(TAG, "addHostRoute failure: address is neither IPv4 nor IPv6" +
                       "(" + dst + ")");
            return false;
        }

        return addRoute(interfaceName, dst, prefixLength, null) == 0;
    }

}
