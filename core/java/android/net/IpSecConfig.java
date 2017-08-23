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
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** @hide */
public final class IpSecConfig implements Parcelable {
    private static final String TAG = "IpSecConfig";

    // MODE_TRANSPORT or MODE_TUNNEL
    private int mMode = IpSecTransform.MODE_TRANSPORT;

    // For tunnel mode
    private InetAddress mLocalAddress;

    private InetAddress mRemoteAddress;

    // The underlying network interface that represents the "gateway" Network
    // for outbound packets. It may also be used to select packets.
    Network mNetwork;

    public static class Flow {
        // Minimum requirements for identifying a transform
        // SPI identifying the IPsec flow in packet processing
        // and a remote IP address
        private int mSpiResourceId = IpSecManager.INVALID_RESOURCE_ID;

        // Encryption Algorithm
        private IpSecAlgorithm mEncryption;

        // Authentication Algorithm
        private IpSecAlgorithm mAuthentication;

        // Authenticated Encryption Algorithm
        private IpSecAlgorithm mAuthenticatedEncryption;

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("{mSpiResourceId=")
                    .append(mSpiResourceId)
                    .append(", mEncryption=")
                    .append(mEncryption)
                    .append(", mAuthentication=")
                    .append(mAuthentication)
                    .append(", mAuthenticatedEncryption=")
                    .append(mAuthenticatedEncryption)
                    .append("}")
                    .toString();
        }
    }

    private final Flow[] mFlow = new Flow[] {new Flow(), new Flow()};

    // For tunnel mode IPv4 UDP Encapsulation
    // IpSecTransform#ENCAP_ESP_*, such as ENCAP_ESP_OVER_UDP_IKE
    private int mEncapType = IpSecTransform.ENCAP_NONE;
    private int mEncapSocketResourceId = IpSecManager.INVALID_RESOURCE_ID;
    private int mEncapRemotePort;

    // An interval, in seconds between the NattKeepalive packets
    private int mNattKeepaliveInterval;

    // Transport or Tunnel
    public void setMode(int mode) {
        mMode = mode;
    }

    public void setLocalAddress(InetAddress localAddress) {
        mLocalAddress = localAddress;
    }

    public void setRemoteAddress(InetAddress remoteAddress) {
        mRemoteAddress = remoteAddress;
    }

    /** Set the SPI for a given direction by resource ID */
    public void setSpiResourceId(int direction, int resourceId) {
        mFlow[direction].mSpiResourceId = resourceId;
    }

    /** Set the encryption algorithm for a given direction */
    public void setEncryption(int direction, IpSecAlgorithm encryption) {
        mFlow[direction].mEncryption = encryption;
    }

    /** Set the authentication algorithm for a given direction */
    public void setAuthentication(int direction, IpSecAlgorithm authentication) {
        mFlow[direction].mAuthentication = authentication;
    }

    /** Set the authenticated encryption algorithm for a given direction */
    public void setAuthenticatedEncryption(int direction, IpSecAlgorithm authenticatedEncryption) {
        mFlow[direction].mAuthenticatedEncryption = authenticatedEncryption;
    }

    public void setNetwork(Network network) {
        mNetwork = network;
    }

    public void setEncapType(int encapType) {
        mEncapType = encapType;
    }

    public void setEncapSocketResourceId(int resourceId) {
        mEncapSocketResourceId = resourceId;
    }

    public void setEncapRemotePort(int port) {
        mEncapRemotePort = port;
    }

    public void setNattKeepaliveInterval(int interval) {
        mNattKeepaliveInterval = interval;
    }

    // Transport or Tunnel
    public int getMode() {
        return mMode;
    }

    public InetAddress getLocalAddress() {
        return mLocalAddress;
    }

    public int getSpiResourceId(int direction) {
        return mFlow[direction].mSpiResourceId;
    }

    public InetAddress getRemoteAddress() {
        return mRemoteAddress;
    }

    public IpSecAlgorithm getEncryption(int direction) {
        return mFlow[direction].mEncryption;
    }

    public IpSecAlgorithm getAuthentication(int direction) {
        return mFlow[direction].mAuthentication;
    }

    public IpSecAlgorithm getAuthenticatedEncryption(int direction) {
        return mFlow[direction].mAuthenticatedEncryption;
    }

    public Network getNetwork() {
        return mNetwork;
    }

    public int getEncapType() {
        return mEncapType;
    }

    public int getEncapSocketResourceId() {
        return mEncapSocketResourceId;
    }

    public int getEncapRemotePort() {
        return mEncapRemotePort;
    }

    public int getNattKeepaliveInterval() {
        return mNattKeepaliveInterval;
    }

    // Parcelable Methods

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mMode);
        out.writeString((mLocalAddress != null) ? mLocalAddress.getHostAddress() : null);
        out.writeString((mRemoteAddress != null) ? mRemoteAddress.getHostAddress() : null);
        out.writeParcelable(mNetwork, flags);
        out.writeInt(mFlow[IpSecTransform.DIRECTION_IN].mSpiResourceId);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_IN].mEncryption, flags);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_IN].mAuthentication, flags);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_IN].mAuthenticatedEncryption, flags);
        out.writeInt(mFlow[IpSecTransform.DIRECTION_OUT].mSpiResourceId);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_OUT].mEncryption, flags);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_OUT].mAuthentication, flags);
        out.writeParcelable(mFlow[IpSecTransform.DIRECTION_OUT].mAuthenticatedEncryption, flags);
        out.writeInt(mEncapType);
        out.writeInt(mEncapSocketResourceId);
        out.writeInt(mEncapRemotePort);
    }

    @VisibleForTesting
    public IpSecConfig() {}

    private static InetAddress readInetAddressFromParcel(Parcel in) {
        String addrString = in.readString();
        if (addrString == null) {
            return null;
        }
        try {
            return InetAddress.getByName(addrString);
        } catch (UnknownHostException e) {
            Log.wtf(TAG, "Invalid IpAddress " + addrString);
            return null;
        }
    }

    private IpSecConfig(Parcel in) {
        mMode = in.readInt();
        mLocalAddress = readInetAddressFromParcel(in);
        mRemoteAddress = readInetAddressFromParcel(in);
        mNetwork = (Network) in.readParcelable(Network.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_IN].mSpiResourceId = in.readInt();
        mFlow[IpSecTransform.DIRECTION_IN].mEncryption =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_IN].mAuthentication =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_IN].mAuthenticatedEncryption =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_OUT].mSpiResourceId = in.readInt();
        mFlow[IpSecTransform.DIRECTION_OUT].mEncryption =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_OUT].mAuthentication =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mFlow[IpSecTransform.DIRECTION_OUT].mAuthenticatedEncryption =
                (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        mEncapType = in.readInt();
        mEncapSocketResourceId = in.readInt();
        mEncapRemotePort = in.readInt();
    }

    @Override
    public String toString() {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder
                .append("{mMode=")
                .append(mMode == IpSecTransform.MODE_TUNNEL ? "TUNNEL" : "TRANSPORT")
                .append(", mLocalAddress=")
                .append(mLocalAddress)
                .append(", mRemoteAddress=")
                .append(mRemoteAddress)
                .append(", mNetwork=")
                .append(mNetwork)
                .append(", mEncapType=")
                .append(mEncapType)
                .append(", mEncapSocketResourceId=")
                .append(mEncapSocketResourceId)
                .append(", mEncapRemotePort=")
                .append(mEncapRemotePort)
                .append(", mNattKeepaliveInterval=")
                .append(mNattKeepaliveInterval)
                .append(", mFlow[OUT]=")
                .append(mFlow[IpSecTransform.DIRECTION_OUT])
                .append(", mFlow[IN]=")
                .append(mFlow[IpSecTransform.DIRECTION_IN])
                .append("}");

        return strBuilder.toString();
    }

    public static final Parcelable.Creator<IpSecConfig> CREATOR =
            new Parcelable.Creator<IpSecConfig>() {
                public IpSecConfig createFromParcel(Parcel in) {
                    return new IpSecConfig(in);
                }

                public IpSecConfig[] newArray(int size) {
                    return new IpSecConfig[size];
                }
            };
}
