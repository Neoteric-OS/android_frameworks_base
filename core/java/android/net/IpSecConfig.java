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
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.util.Log;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** @hide */
public final class IpSecConfig implements Parcelable {
    private static final String TAG = IpSecConfig.class.getSimpleName();

    /** @hide */
    public static final class Properties {
        public static final int MODE_TRANSPORT = 1 << 0;
        public static final int MODE_TUNNEL = 1 << 1;
        public static final int SELECTOR = 1 << 2;
        public static final int NETWORK = 1 << 3;
        public static final int ENCRYPTION_ALGO = 1 << 4;
        public static final int AUTHENTICATION_ALGO = 1 << 5;
        public static final int UDP_ENCAP = 1 << 6;
        public static final int SELECTOR_PROTO = 1 << 7;
    };

    // For transport mode
    ParcelFileDescriptor localSocket;
    int remotePort;

    // For tunnel mode
    InetAddress localIp;

    // Minimum requirements for identifying a transform
    // SPI identifying the IPSec flow in packet processing
    // and a remote IP address
    int spi;
    InetAddress remoteIp;

    //IpSecTransformr#DIRECTION_IN or IpSecTransform#DIRECTION_OUT
    int direction;

    // Limit selection by network interface
    Network network;

    // Encryption Algorithm
    IpSecAlgorithm encryptionAlgo;

    // Authentication Algorithm
    IpSecAlgorithm authenticationAlgo;

    // For tunnel mode IPv4 UDP Encapsulation
    // IpSecTransform#ENCAP_ESP_*, such as ENCAP_ESP_OVER_UDP_IKE
    int encapType;
    int encapLocalPort;
    int encapRemotePort;

    // An optional protocol to match with the selector
    int selectorProto;

    // A bitmask of FEATURE_* indicating which of the fields
    // of this class are valid.
    long features;

    /** @hide */
    public ParcelFileDescriptor getLocalSocket() {
        return localSocket;
    }

    /** @hide */
    public int getRemotePort() {
        return remotePort;
    }

    /** @hide */
    public InetAddress getLocalIp() {
        return localIp;
    }

    /** @hide */
    public int getSpi() {
        return spi;
    }

    /** @hide */
    public InetAddress getRemoteIp() {
        return remoteIp;
    }

    /** @hide */
    public int getDirection() {
        return direction;
    }

    /** @hide */
    public IpSecAlgorithm getEncryptionAlgo() {
        return encryptionAlgo;
    }

    /** @hide */
    public IpSecAlgorithm getAuthenticationAlgo() {
        return authenticationAlgo;
    }

    /** @hide */
    public Network getNetwork() {
        return network;
    }

    /** @hide */
    public int getEncapType() {
        return encapType;
    }

    /** @hide */
    public int getEncapLocalPort() {
        return encapLocalPort;
    }

    /** @hide */
    public int getEncapRemotePort() {
        return encapRemotePort;
    }

    /** @hide */
    public int getSelectorProto() {
        return selectorProto;
    }

    /** @hide */
    public boolean hasProperty(int featureBits) {
        return (features & featureBits) == featureBits;
    }

    // Parcelable Methods

    @Override
    public int describeContents() {
        return hasProperty(Properties.MODE_TRANSPORT) ? Parcelable.CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(features);
        if (hasProperty(Properties.MODE_TRANSPORT)) {
            out.writeParcelable(localSocket, flags);
            out.writeInt(remotePort);
        } else if (hasProperty(Properties.MODE_TUNNEL)) {
            out.writeString(localIp.getHostAddress());
        }
        if (hasProperty(Properties.SELECTOR)) {
            out.writeInt(spi);
            out.writeString(remoteIp.getHostAddress());
            out.writeInt(direction);
        }

        if (hasProperty(Properties.NETWORK)) {
            out.writeParcelable(network, flags);
        }

        if (hasProperty(Properties.ENCRYPTION_ALGO)) {
            out.writeParcelable(encryptionAlgo, flags);
        }

        if (hasProperty(Properties.AUTHENTICATION_ALGO)) {
            out.writeParcelable(authenticationAlgo, flags);
        }

        if (hasProperty(Properties.UDP_ENCAP)) {
            out.writeInt(encapType);
            out.writeInt(encapLocalPort);
            out.writeInt(encapRemotePort);
        }

        if (hasProperty(Properties.SELECTOR_PROTO)) {
            out.writeInt(selectorProto);
        }
    }

    IpSecConfig() {}

    private static InetAddress readInetAddressFromParcel(Parcel in) {
        String addrString = in.readString();
        try {
            return InetAddress.getByName(addrString);
        } catch (UnknownHostException e) {
            Log.wtf(TAG, "Invalid IpAddress " + addrString);
            return null;
        }
    }

    private IpSecConfig(Parcel in) {
        features = in.readLong();

        if (hasProperty(Properties.MODE_TRANSPORT)) {
            localSocket = in.readParcelable(ParcelFileDescriptor.class.getClassLoader());
            remotePort = in.readInt();
        } else if (hasProperty(Properties.MODE_TUNNEL)) {
            localIp = readInetAddressFromParcel(in);
        }

        if (hasProperty(Properties.SELECTOR)) {
            spi = in.readInt();
            remoteIp = readInetAddressFromParcel(in);
            direction = in.readInt();
        }

        if (hasProperty(Properties.NETWORK)) {
            network = (Network) in.readParcelable(Network.class.getClassLoader());
        }

        if (hasProperty(Properties.ENCRYPTION_ALGO)) {
            encryptionAlgo =
                    (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        }

        if (hasProperty(Properties.AUTHENTICATION_ALGO)) {
            authenticationAlgo =
                    (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        }

        if (hasProperty(Properties.UDP_ENCAP)) {
            encapType = in.readInt();
            encapLocalPort = in.readInt();
            encapRemotePort = in.readInt();
        }

        if (hasProperty(Properties.SELECTOR_PROTO)) {
            selectorProto = in.readInt();
        }
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
