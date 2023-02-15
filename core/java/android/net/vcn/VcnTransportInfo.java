/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn;

import static android.net.NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS;
import static android.net.vcn.VcnGatewayConnectionConfig.UDP_PORT_4500_NAT_TIMEOUT_UNSET;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SubscriptionManager;

import java.util.Objects;

/**
 * VcnTransportInfo contains information about the VCN's underlying transports for SysUi.
 *
 * <p>Presence of this class in the NetworkCapabilities.TransportInfo implies that the network is a
 * VCN.
 *
 * <p>VcnTransportInfo must exist on top of either an underlying Wifi or Cellular Network. If the
 * underlying Network is WiFi, the subId will be {@link
 * SubscriptionManager#INVALID_SUBSCRIPTION_ID}. If the underlying Network is Cellular, the WifiInfo
 * will be {@code null}.
 *
 * <p>Receipt of a VcnTransportInfo requires the NETWORK_SETTINGS permission; else the entire
 * VcnTransportInfo instance will be redacted.
 *
 * @hide
 */
public class VcnTransportInfo implements TransportInfo, Parcelable {
    @Nullable private final WifiInfo mWifiInfo;
    private final int mSubId;
    private final int mUdpPort4500NatTimeoutSeconds;

    public VcnTransportInfo(@NonNull WifiInfo wifiInfo, int udpPort4500NatTimeoutSeconds) {
        this(wifiInfo, INVALID_SUBSCRIPTION_ID, udpPort4500NatTimeoutSeconds);
    }

    public VcnTransportInfo(int subId, int udpPort4500NatTimeoutSeconds) {
        this(null /* wifiInfo */, subId, udpPort4500NatTimeoutSeconds);
    }

    private VcnTransportInfo(
            @Nullable WifiInfo wifiInfo, int subId, int udpPort4500NatTimeoutSeconds) {
        mWifiInfo = wifiInfo;
        mSubId = subId;
        mUdpPort4500NatTimeoutSeconds = udpPort4500NatTimeoutSeconds;
    }

    /**
     * Get the {@link WifiInfo} for this VcnTransportInfo.
     *
     * <p>If the underlying Network for the associated VCN is Cellular, returns null.
     *
     * @return the WifiInfo if there is an underlying WiFi connection, else null.
     */
    @Nullable
    public WifiInfo getWifiInfo() {
        return mWifiInfo;
    }

    /**
     * Get the subId for the VCN Network associated with this VcnTransportInfo.
     *
     * <p>If the underlying Network for the associated VCN is WiFi, returns {@link
     * SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
     *
     * @return the Subscription ID if a cellular underlying Network is present, else {@link
     *     android.telephony.SubscriptionManager#INVALID_SUBSCRIPTION_ID}.
     */
    public int getSubId() {
        return mSubId;
    }

    /**
     * Get the VCN provided UDP port 4500 NAT timeout
     *
     * @return the UDP 4500 NAT timeout, or
     *     VcnGatewayConnectionConfig.UDP_PORT_4500_NAT_TIMEOUT_UNSET if not set.
     */
    public int getUdpPort4500NatTimeoutSeconds() {
        return mUdpPort4500NatTimeoutSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWifiInfo, mSubId, mUdpPort4500NatTimeoutSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VcnTransportInfo)) return false;
        final VcnTransportInfo that = (VcnTransportInfo) o;
        return Objects.equals(mWifiInfo, that.mWifiInfo)
                && mSubId == that.mSubId
                && mUdpPort4500NatTimeoutSeconds == that.mUdpPort4500NatTimeoutSeconds;
    }

    /** {@inheritDoc} */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public TransportInfo makeCopy(long redactions) {
        if ((redactions & NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS) != 0) {
            return new VcnTransportInfo(
                    null, INVALID_SUBSCRIPTION_ID, UDP_PORT_4500_NAT_TIMEOUT_UNSET);
        }

        return new VcnTransportInfo(
                (mWifiInfo == null) ? null : mWifiInfo.makeCopy(redactions),
                mSubId,
                mUdpPort4500NatTimeoutSeconds);
    }

    @Override
    public long getApplicableRedactions() {
        long redactions = REDACT_FOR_NETWORK_SETTINGS;

        // Add additional wifi redactions if necessary
        if (mWifiInfo != null) {
            redactions |= mWifiInfo.getApplicableRedactions();
        }

        return redactions;
    }

    /** {@inheritDoc} */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mSubId);
        dest.writeParcelable(mWifiInfo, flags);
        dest.writeInt(mUdpPort4500NatTimeoutSeconds);
    }

    @Override
    public String toString() {
        return "VcnTransportInfo { mWifiInfo = " + mWifiInfo + ", mSubId = " + mSubId + " }";
    }

    /** Implement the Parcelable interface */
    public static final @NonNull Creator<VcnTransportInfo> CREATOR =
            new Creator<VcnTransportInfo>() {
                public VcnTransportInfo createFromParcel(Parcel in) {
                    final int subId = in.readInt();
                    final WifiInfo wifiInfo =
                            in.readParcelable(null, android.net.wifi.WifiInfo.class);
                    final int udpPort4500NatTimeoutSeconds = in.readInt();

                    // If all fields are their null values, return null TransportInfo to avoid
                    // leaking information about this being a VCN Network (instead of macro
                    // cellular, etc)
                    if (wifiInfo == null
                            && subId == INVALID_SUBSCRIPTION_ID
                            && udpPort4500NatTimeoutSeconds == UDP_PORT_4500_NAT_TIMEOUT_UNSET) {
                        return null;
                    }

                    return new VcnTransportInfo(wifiInfo, subId, udpPort4500NatTimeoutSeconds);
                }

                public VcnTransportInfo[] newArray(int size) {
                    return new VcnTransportInfo[size];
                }
            };
}
