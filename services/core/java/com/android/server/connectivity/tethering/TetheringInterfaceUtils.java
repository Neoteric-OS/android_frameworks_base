package com.android.server.connectivity.tethering;

import android.annotation.Nullable;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkState;
import android.net.RouteInfo;
import android.net.util.InterfaceSet;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * @hide
 */
public final class TetheringInterfaceUtils {
    /**
     * Get upstream interfaces for tethering based on default routes for IPv4/IPv6.
     * @return null if there is no usable interface, or a set of at least one interface otherwise.
     */
    public static @Nullable InterfaceSet getTetheringInterfaces(NetworkState ns) {
        if (ns == null) {
            return null;
        }

        final LinkProperties lp = ns.linkProperties;
        final String if4 = getInterfaceForDestination(lp, Inet4Address.ANY);
        final String if6 = getIPv6Interface(ns);

        return (if4 == null && if6 == null) ? null : new InterfaceSet(if4, if6);
    }

    /**
     * Get the upstream interface for IPv6 tethering.
     * @return null if there is no usable interface, or the interface name otherwise.
     */
    public static @Nullable String getIPv6Interface(NetworkState ns) {
        // Broadly speaking:
        //
        //     [1] does the upstream have an IPv6 default route?
        //
        // and
        //
        //     [2] does the upstream have one or more global IPv6 /64s
        //         dedicated to this device?
        //
        // In lieu of Prefix Delegation and other evaluation of whether a
        // prefix may or may not be dedicated to this device, for now just
        // check whether the upstream is TRANSPORT_CELLULAR. This works
        // because "[t]he 3GPP network allocates each default bearer a unique
        // /64 prefix", per RFC 6459, Section 5.2.
        final boolean canTether =
                (ns != null) && (ns.network != null) &&
                        (ns.linkProperties != null) && (ns.networkCapabilities != null) &&
                        // At least one upstream DNS server:
                        ns.linkProperties.hasIPv6DnsServer() &&
                        // Minimal amount of IPv6 provisioning:
                        ns.linkProperties.hasGlobalIPv6Address() &&
                        // Temporary approximation of "dedicated prefix":
                        ns.networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

        return canTether
                ? getInterfaceForDestination(ns.linkProperties, Inet6Address.ANY)
                : null;
    }

    private static String getInterfaceForDestination(LinkProperties lp, InetAddress dst) {
        final RouteInfo ri = (lp != null)
                ? RouteInfo.selectBestRoute(lp.getAllRoutes(), dst)
                : null;
        return (ri != null) ? ri.getInterface() : null;
    }
}
