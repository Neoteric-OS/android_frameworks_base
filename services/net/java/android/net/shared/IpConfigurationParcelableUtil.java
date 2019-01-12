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

package android.net.shared;

import android.annotation.Nullable;
import android.net.DhcpResults;
import android.net.DhcpResultsParcelable;
import android.net.InetAddresses;
import android.net.StaticIpConfiguration;
import android.net.StaticIpConfigurationParcelable;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Collection of utility methods to convert to and from stable AIDL parcelables for IpClient
 * configuration classes.
 * @hide
 */
public final class IpConfigurationParcelableUtil {
    /**
     * Convert a StaticIpConfiguration to a StaticIpConfigurationParcelable.
     */
    public static StaticIpConfigurationParcelable toStableParcelable(
            @Nullable StaticIpConfiguration config) {
        if (config == null) {
            return null;
        }
        final StaticIpConfigurationParcelable p = new StaticIpConfigurationParcelable();
        p.ipAddress = LinkPropertiesParcelableUtil.toStableParcelable(config.ipAddress);
        p.gateway = config.gateway.getHostAddress();
        p.dnsServers = ParcelableUtil.toParcelableArray(
                config.dnsServers, InetAddress::getHostAddress, String.class);
        p.domains = config.domains;
        return p;
    }

    /**
     * Convert a StaticIpConfigurationParcelable to a StaticIpConfiguration.
     */
    public static StaticIpConfiguration fromStableParcelable(
            @Nullable StaticIpConfigurationParcelable p) {
        if (p == null) {
            return null;
        }
        final StaticIpConfiguration config = new StaticIpConfiguration();
        config.ipAddress = LinkPropertiesParcelableUtil.fromStableParcelable(p.ipAddress);
        config.gateway = InetAddresses.parseNumericAddress(p.gateway);
        config.dnsServers.addAll(ParcelableUtil.fromParcelableArray(
                p.dnsServers, InetAddresses::parseNumericAddress));
        config.domains = p.domains;
        return config;
    }

    /**
     * Convert DhcpResults to a DhcpResultsParcelable.
     */
    public static DhcpResultsParcelable toStableParcelable(@Nullable DhcpResults results) {
        if (results == null) {
            return null;
        }
        final DhcpResultsParcelable p = new DhcpResultsParcelable();
        p.baseConfiguration = toStableParcelable((StaticIpConfiguration) results);
        p.leaseDuration = results.leaseDuration;
        p.mtu = results.mtu;
        p.serverAddress = results.serverAddress.getHostAddress();
        p.vendorInfo = results.vendorInfo;
        return p;
    }

    /**
     * Convert a DhcpResultsParcelable to DhcpResults.
     */
    public static DhcpResults fromStableParcelable(@Nullable DhcpResultsParcelable p) {
        if (p == null) {
            return null;
        }
        final DhcpResults results = new DhcpResults(fromStableParcelable(p.baseConfiguration));
        results.leaseDuration = p.leaseDuration;
        results.mtu = p.mtu;
        results.serverAddress = (Inet4Address) InetAddresses.parseNumericAddress(p.serverAddress);
        results.vendorInfo = p.vendorInfo;
        return results;
    }
}
