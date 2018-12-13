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

package android.net.ipmemorystore;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A POD object to represent attributes of a single L2 network entry.
 * @hide
 */
public class NetworkAttributes implements Parcelable {
    private static final boolean DBG = true;

    // The v4 address that was assigned to this device the last time it joined this network.
    // This typically comes from DHCP but could be something else like static configuration.
    // In v6 devices do not normally get their addresses from DHCP, and usually have many
    // different addresses. Importantly, the v6 addresses being different do not consitute
    // a reliable signal to test for L3 network sameness.
    // TODO : add a list of v6 prefixes for the v6 case.
    @Nullable
    public final InetAddress assignedV4Address;

    // Optionally supplied by the client if it has an opinion on L3 network. For example, this
    // could be a hash of the SSID + security type on WiFi.
    @Nullable
    public final String groupHint;

    // The list of DNS server addresses.
    @Nullable
    public final List<InetAddress> dnsAddresses;

    // The mtu on this network.
    @Nullable
    public final Integer mtu;

    NetworkAttributes(
            @Nullable final InetAddress assignedV4Address,
            @Nullable final String groupHint,
            @Nullable final List<InetAddress> dnsAddresses,
            @Nullable final Integer mtu) {
        this.assignedV4Address = assignedV4Address;
        this.groupHint = groupHint;
        this.dnsAddresses = null == dnsAddresses ? null :
                Collections.unmodifiableList(new ArrayList<>(dnsAddresses));
        this.mtu = mtu;
    }

    /** @hide */
    public static class Builder {
        @Nullable
        private InetAddress mAssignedAddress = null;
        @Nullable
        private String mGroupHint = null;
        @Nullable
        private List<InetAddress> mDnsAddresses = null;
        @Nullable
        private Integer mMtu = null;

        /**
         * Set the assigned address.
         * @param assignedV4Address The assigned address.
         * @return This builder.
         */
        public Builder setAssignedAddress(@Nullable final InetAddress assignedV4Address) {
            mAssignedAddress = assignedV4Address;
            return this;
        }

        /**
         * Set the group hint.
         * @param groupHint The group hint.
         * @return This builder.
         */
        public Builder setGroupHint(@Nullable final String groupHint) {
            mGroupHint = groupHint;
            return this;
        }

        /**
         * Set the DNS addresses.
         * @param dnsAddresses The DNS addresses.
         * @return This builder.
         */
        public Builder setDnsAddresses(@Nullable final List<InetAddress> dnsAddresses) {
            if (DBG && null != dnsAddresses) {
                // Parceling code crashes if one of the addresses is null, therefore validate
                // them when running in debug.
                for (final InetAddress address : dnsAddresses) {
                    if (null == address) throw new IllegalArgumentException("Null DNS address");
                }
            }
            this.mDnsAddresses = dnsAddresses;
            return this;
        }

        /**
         * Set the MTU.
         * @param mtu The MTU.
         * @return This builder.
         */
        public Builder setMtu(@Nullable final Integer mtu) {
            mMtu = mtu;
            return this;
        }

        /**
         * Return the built NetworkAttributes object.
         * @return The built NetworkAttributes object.
         */
        public NetworkAttributes build() {
            return new NetworkAttributes(mAssignedAddress, mGroupHint, mDnsAddresses, mMtu);
        }
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (!(o instanceof NetworkAttributes)) return false;
        final NetworkAttributes other = (NetworkAttributes) o;
        return Objects.equals(assignedV4Address, other.assignedV4Address)
                && Objects.equals(groupHint, other.groupHint)
                && Objects.equals(dnsAddresses, other.dnsAddresses)
                && Objects.equals(mtu, other.mtu);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignedV4Address, groupHint, dnsAddresses, mtu);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@Nullable final Parcel dest, final int flags) {
        if (null == dest) return;
        if (null == assignedV4Address) {
            dest.writeBoolean(false);
        } else {
            dest.writeBoolean(true);
            dest.writeByteArray(assignedV4Address.getAddress());
        }
        dest.writeString(groupHint);
        if (null == dnsAddresses) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(dnsAddresses.size());
            for (final InetAddress addr : dnsAddresses) dest.writeByteArray(addr.getAddress());
        }
        if (null == mtu) {
            dest.writeBoolean(false);
        } else {
            dest.writeBoolean(true);
            dest.writeInt(mtu);
        }
    }

    public static final Creator<NetworkAttributes> CREATOR = new Creator<NetworkAttributes>() {
        @Override
        public NetworkAttributes createFromParcel(@NonNull final Parcel in) {
            final Builder builder = new Builder();
            if (in.readBoolean()) {
                try {
                    builder.setAssignedAddress(InetAddress.getByAddress(in.createByteArray()));
                } catch (final UnknownHostException ignored) { }
            }
            builder.setGroupHint(in.readString());
            final int dnsAddressCount = in.readInt();
            if (dnsAddressCount >= 0) { // Including an empty array
                final ArrayList<InetAddress> addresses = new ArrayList<>(dnsAddressCount);
                while (addresses.size() < dnsAddressCount) {
                    try {
                        addresses.add(InetAddress.getByAddress(in.createByteArray()));
                    } catch (final UnknownHostException ignored) { }
                }
                builder.setDnsAddresses(addresses);
            }
            if (in.readBoolean()) builder.setMtu(in.readInt());
            return builder.build();
        }

        @Override
        public NetworkAttributes[] newArray(final int size) {
            return new NetworkAttributes[size];
        }
    };

    /** Pretty print */
    public String toString() {
        final StringJoiner resultJoiner = new StringJoiner(" ", "{", "}");
        final ArrayList<String> nullFields = new ArrayList<>();

        if (null != assignedV4Address) {
            resultJoiner.add("assignedV4Address :");
            resultJoiner.add(assignedV4Address.toString());
        } else {
            nullFields.add("assignedV4Address");
        }

        if (null != groupHint) {
            resultJoiner.add("groupHint :");
            resultJoiner.add(groupHint);
        } else {
            nullFields.add("groupHint");
        }

        if (null != dnsAddresses) {
            resultJoiner.add("dnsAddresses : [");
            for (final InetAddress addr : dnsAddresses) {
                resultJoiner.add(addr.getHostAddress());
            }
            resultJoiner.add("]");
        } else {
            nullFields.add("dnsAddresses");
        }

        if (null != mtu) {
            resultJoiner.add("mtu :");
            resultJoiner.add(mtu.toString());
        } else {
            nullFields.add("mtu");
        }

        if (!nullFields.isEmpty()) {
            resultJoiner.add("; Null fields : [");
            for (final String field : nullFields) {
                resultJoiner.add(field);
            }
            resultJoiner.add("]");
        }

        return resultJoiner.toString();
    }
}
