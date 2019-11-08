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

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * A simple object for retrieving the results of a DHCP request.
 */
public class DhcpInfo implements Parcelable {
    public int ipAddress;
    public int gateway;
    public int netmask;
    public int dns1;
    public int dns2;
    public int serverAddress;
    public int leaseDuration;
    /**
     * Link MTU option. 0 means unset.
     * @hide
     */
    public int mtu;

    /** @hide */
    @Nullable
    public String domains;
    /**
     * Vendor specific information (from RFC 2132).
     * @hide
     */
    @Nullable
    public String vendorInfo;
    /** @hide */
    @Nullable
    public String serverHostName;

    /** @hide */
    public static final String VENDOR_INFO_ANDROID_METERED = "ANDROID_METERED";

    public DhcpInfo() {
        super();
    }

    /** copy constructor {@hide} */
    public DhcpInfo(DhcpInfo source) {
        if (source != null) {
            ipAddress = source.ipAddress;
            gateway = source.gateway;
            netmask = source.netmask;
            dns1 = source.dns1;
            dns2 = source.dns2;
            serverAddress = source.serverAddress;
            leaseDuration = source.leaseDuration;
            mtu = source.mtu;
            domains = source.domains;
            vendorInfo = source.vendorInfo;
            serverHostName = source.serverHostName;
        }
    }

    /**
     * Test if this DHCP lease includes vendor hint that network link is
     * metered, and sensitive to heavy data transfers.
     */
    public boolean hasMeteredHint() {
        if (vendorInfo != null) {
            return vendorInfo.contains(VENDOR_INFO_ANDROID_METERED);
        } else {
            return false;
        }
    }

    /**
     * Clear the contents of this object.
     */
    public void clear() {
        ipAddress = 0;
        gateway = 0;
        netmask = 0;
        dns1 = 0;
        dns2 = 0;
        serverAddress = 0;
        leaseDuration = 0;
        mtu = 0;
        domains = null;
        vendorInfo = null;
        serverHostName = null;
    }

    /**
     *  Return true if all contents are identical, otherwise return false.
     */
    public boolean equals(Object o) {
        if (!(o instanceof DhcpInfo)) return false;

        final DhcpInfo target = (DhcpInfo) o;

        return  ipAddress == target.ipAddress && gateway == target.gateway
                && netmask == target.netmask && dns1 == target.dns1
                && dns2 == target.dns2 && serverAddress == target.serverAddress
                && leaseDuration == target.leaseDuration && mtu == target.mtu
                && Objects.equals(domains, target.domains)
                && Objects.equals(vendorInfo, target.vendorInfo)
                && Objects.equals(serverHostName, target.serverHostName);
    }

    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("ipaddr "); putAddress(str, ipAddress);
        str.append(" gateway "); putAddress(str, gateway);
        str.append(" netmask "); putAddress(str, netmask);
        str.append(" dns1 "); putAddress(str, dns1);
        str.append(" dns2 "); putAddress(str, dns2);
        str.append(" DHCP server "); putAddress(str, serverAddress);
        str.append(" lease ").append(leaseDuration).append(" seconds");
        str.append(" MTU ").append(mtu);
        if (domains != null) str.append(" domains ").append(domains);
        if (vendorInfo != null) str.append(" vendorInfo ").append(vendorInfo);
        if (serverHostName != null) str.append(" Server name ").append(serverHostName);

        return str.toString();
    }

    private static void putAddress(StringBuffer buf, int addr) {
        buf.append(NetworkUtils.intToInetAddress(addr).getHostAddress());
    }

    public void setIpAddress(int addr) {
        ipAddress = addr;
    }

    public int getIpAddress() {
        return ipAddress;
    }

    public void setGateway(int addr) {
        gateway = addr;
    }

    public int getGateway() {
        return gateway;
    }

    public void setNetmask(int mask) {
        netmask = mask;
    }

    public int getNetmask() {
        return netmask;
    }

    public void setDns1(int dns) {
        dns1 = dns;
    }

    public int getDns1() {
        return dns1;
    }

    public void setDns2(int dns) {
        dns2 = dns;
    }

    public int getDns2() {
        return dns2;
    }

    public void setServerAddress(int server) {
        serverAddress = server;
    }

    public int getServerAddress() {
        return serverAddress;
    }

    public void setLeaseDuration(int sec) {
        leaseDuration = sec;
    }

    public int getLeaseDuration() {
        return leaseDuration;
    }

    public void setMtu(int size) {
        mtu = size;
    }

    public int getMtu() {
        return mtu;
    }

    public void setDomains(@Nullable String name) {
        domains = name;
    }

    @Nullable
    public String getDomains() {
        return domains;
    }

    public void setVendorInfo(@Nullable String info) {
        vendorInfo = info;
    }

    @Nullable
    public String getVendorInfo() {
        return vendorInfo;
    }

    public void setServerHostName(@Nullable String host) {
        serverHostName = host;
    }

    @Nullable
    public String getServerHostName() {
        return serverHostName;
    }

    /** Implement the Parcelable interface */
    public int describeContents() {
        return 0;
    }

    /** Implement the Parcelable interface */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ipAddress);
        dest.writeInt(gateway);
        dest.writeInt(netmask);
        dest.writeInt(dns1);
        dest.writeInt(dns2);
        dest.writeInt(serverAddress);
        dest.writeInt(leaseDuration);
        dest.writeInt(mtu);
        dest.writeString(domains);
        dest.writeString(vendorInfo);
        dest.writeString(serverHostName);
    }

    /** Implement the Parcelable interface */
    public static final @android.annotation.NonNull Creator<DhcpInfo> CREATOR =
        new Creator<DhcpInfo>() {
            public DhcpInfo createFromParcel(Parcel in) {
                DhcpInfo info = new DhcpInfo();
                info.ipAddress = in.readInt();
                info.gateway = in.readInt();
                info.netmask = in.readInt();
                info.dns1 = in.readInt();
                info.dns2 = in.readInt();
                info.serverAddress = in.readInt();
                info.leaseDuration = in.readInt();
                info.mtu = in.readInt();
                info.domains = in.readString();
                info.vendorInfo = in.readString();
                info.serverHostName = in.readString();
                return info;
            }

            public DhcpInfo[] newArray(int size) {
                return new DhcpInfo[size];
            }
        };
}
