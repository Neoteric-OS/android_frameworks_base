package android.net.ip;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.RouteInfo;
import android.net.util.NetworkConstants;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** @hide */
public class InitialConfiguration {
    public final Set<LinkAddress> ipAddresses = new HashSet<>();
    public final Set<IpPrefix> directlyConnectedRoutes = new HashSet<>();
    public final Set<InetAddress> dnsServers = new HashSet<>();
    public Inet4Address gateway; // WiFi legacy behavior with static ipv4 config

    public static InitialConfiguration copy(InitialConfiguration config) {
        if (config == null) {
            return null;
        }
        InitialConfiguration configCopy = new InitialConfiguration();
        configCopy.ipAddresses.addAll(config.ipAddresses);
        configCopy.directlyConnectedRoutes.addAll(config.directlyConnectedRoutes);
        configCopy.dnsServers.addAll(config.dnsServers);
        return configCopy;
    }

    @Override
    public String toString() {
        return String.format(
                "InitialConfiguration(IPs: {%s}, prefixes: {%s}, DNS: {%s}, v4 gateway: %s)",
                join(", ", ipAddresses), join(", ", directlyConnectedRoutes),
                join(", ", dnsServers), gateway);
    }

    public boolean isValid() {
        if (ipAddresses.isEmpty()) {
            return false;
        }

        // For every IP address, there must be at least one prefix containing that address.
        for (LinkAddress addr : ipAddresses) {
            if (!any(directlyConnectedRoutes, (p) -> p.contains(addr.getAddress()))) {
                return false;
            }
        }
        // For every dns server, there must be at least one prefix containing that address.
        for (InetAddress addr : dnsServers) {
            if (!any(directlyConnectedRoutes, (p) -> p.contains(addr))) {
                return false;
            }
        }
        // All IPv6 LinkAddresses have an RFC7421-suitable prefix length
        // (read: compliant with RFC4291#section2.5.4).
        if (any(ipAddresses, not(InitialConfiguration::isPrefixLengthCompliant))) {
            return false;
        }
        // If directlyConnectedRoutes contains an IPv6 default route
        // then ipAddresses MUST contain at least one non-ULA GUA.
        if (any(directlyConnectedRoutes, InitialConfiguration::isIPv6DefaultRoute)
                && all(ipAddresses, not(InitialConfiguration::isIPv6GUA))) {
            return false;
        }
        // The prefix length of routes in directlyConnectedRoutes be within reasonable
        // bounds for IPv6: /48-/64 just as we’d accept in RIOs.
        if (any(directlyConnectedRoutes, not(InitialConfiguration::isPrefixLengthCompliant))) {
            return false;
        }
        // There no more than one IPv4 address
        if (ipAddresses.stream().filter(LinkAddress::isIPv4).count() > 1) {
            return false;
        }

        return true;
    }

    /**
     * @return true if the given list of addressess and routes satisfies provisioning for this
     * InitialConfiguration. LinkAddresses and RouteInfo objects are not compared with equality
     * because addresses and routes seen by Netlink will contain additional fields like flags,
     * interfaces, and so on. If this InitialConfiguration has no IP address specified, the
     * provisioning check always fails.
     *
     * If the given list of routes is null, only addresses are taken into considerations.
     */
    public boolean isProvisionedBy(List<LinkAddress> addresses, List<RouteInfo> routes) {
        if (ipAddresses.isEmpty()) {
            return false;
        }

        for (LinkAddress addr : ipAddresses) {
            if (!any(addresses, (addrSeen) -> addr.isSameAddressAs(addrSeen))) {
                return false;
            }
        }

        if (routes != null) {
            for (IpPrefix prefix : directlyConnectedRoutes) {
                if (!any(routes, (routeSeen) -> isDirectlyConnectedRoute(routeSeen, prefix))) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isDirectlyConnectedRoute(RouteInfo route, IpPrefix prefix) {
        return !route.hasGateway() && prefix.equals(route.getDestination());
    }

    private static boolean isPrefixLengthCompliant(LinkAddress addr) {
        return addr.isIPv4() || isCompliantIPv6PrefixLength(addr.getPrefixLength());
    }

    private static boolean isPrefixLengthCompliant(IpPrefix prefix) {
        return prefix.isIPv4() || isCompliantIPv6PrefixLength(prefix.getPrefixLength());
    }

    private static boolean isCompliantIPv6PrefixLength(int prefixLength) {
        return (NetworkConstants.RFC6177_MIN_PREFIX_LENGTH <= prefixLength)
                && (prefixLength <= NetworkConstants.RFC7421_PREFIX_LENGTH);
    }

    private static boolean isIPv6DefaultRoute(IpPrefix prefix) {
        return prefix.getAddress().equals(Inet6Address.ANY);
    }

    private static boolean isIPv6GUA(LinkAddress addr) {
        return addr.isIPv6() && addr.isGlobalPreferred();
    }

    // TODO: extract out into CollectionUtils.
    public static <T> boolean any(Iterable<T> coll, Predicate<T> fn) {
        for (T t : coll) {
            if (fn.test(t)) {
                return true;
            }
        }
        return false;
    }

    public static <T> boolean all(Iterable<T> coll, Predicate<T> fn) {
        return !any(coll, not(fn));
    }

    public static <T> Predicate<T> not(Predicate<T> fn) {
        return (t) -> !fn.test(t);
    }

    public static <T> String join(String delimiter, Collection<T> coll) {
        return coll.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }
}
