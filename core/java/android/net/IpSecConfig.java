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

import android.os.Binder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.Process;
import android.util.Log;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** @hide */
public final class IpSecConfig implements Parcelable {
    private static final String TAG = IpSecConfig.class.getSimpleName();

    /** @hide */
    public static final int FEATURE_TRANSPORT_MODE = 1 << 0;
    /** @hide */
    public static final int FEATURE_TUNNEL_MODE = 1 << 1;
    /** @hide */
    public static final int FEATURE_SELECTOR = 1 << 2;
    /** @hide */
    public static final int FEATURE_NETWORK = 1 << 3;
    /** @hide */
    public static final int FEATURE_ENCRYPTION = 1 << 4;
    /** @hide */
    public static final int FEATURE_AUTHENTICATION = 1 << 5;
    /** @hide */
    public static final int FEATURE_UDP_ENCAP = 1 << 6;
    /** @hide */
    public static final int FEATURE_SELECTOR_PROTO = 1 << 7;

    void enforceSystemUid() {
        final int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only Available to the System UID");
        }
    }

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
        enforceSystemUid();
        return localSocket;
    }

    /** @hide */
    public int getRemotePort() {
        enforceSystemUid();
        return remotePort;
    }

    /** @hide */
    public InetAddress getLocalIp() {
        enforceSystemUid();
        return localIp;
    }

    /** @hide */
    public int getSpi() {
        enforceSystemUid();
        return spi;
    }

    /** @hide */
    public InetAddress getRemoteIp() {
        enforceSystemUid();
        return remoteIp;
    }

    /** @hide */
    public int getDirection() {
        enforceSystemUid();
        return direction;
    }

    /** @hide */
    public IpSecAlgorithm getEncryptionAlgo() {
        enforceSystemUid();
        return encryptionAlgo;
    }

    /** @hide */
    public IpSecAlgorithm getAuthenticationAlgo() {
        enforceSystemUid();
        return authenticationAlgo;
    }

    /** @hide */
    public Network getNetwork() {
        enforceSystemUid();
        return network;
    }

    /** @hide */
    public int getEncapType() {
        enforceSystemUid();
        return encapType;
    }

    /** @hide */
    public int getEncapLocalPort() {
        enforceSystemUid();
        return encapLocalPort;
    }

    /** @hide */
    public int getEncapRemotePort() {
        enforceSystemUid();
        return encapRemotePort;
    }

    /** @hide */
    public int getSelectorProto() {
        enforceSystemUid();
        return selectorProto;
    }

    /** @hide */
    public boolean hasFeature(int featureBits) {
        return (features & featureBits) == featureBits;
    }

    // Parcelable Methods

    @Override
    public int describeContents() {
        return hasFeature(FEATURE_TRANSPORT_MODE) ? Parcelable.CONTENTS_FILE_DESCRIPTOR : 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(features);
        if (hasFeature(FEATURE_TRANSPORT_MODE)) {
            out.writeParcelable(localSocket, flags);
            out.writeInt(remotePort);
        } else if (hasFeature(FEATURE_TUNNEL_MODE)) {
            out.writeString(localIp.getHostAddress());
        }
        if (hasFeature(FEATURE_SELECTOR)) {
            out.writeInt(spi);
            out.writeString(remoteIp.getHostAddress());
            out.writeInt(direction);
        }

        if (hasFeature(FEATURE_NETWORK)) {
            out.writeParcelable(network, flags);
        }

        if (hasFeature(FEATURE_ENCRYPTION)) {
            out.writeParcelable(encryptionAlgo, flags);
        }

        if (hasFeature(FEATURE_AUTHENTICATION)) {
            out.writeParcelable(authenticationAlgo, flags);
        }

        if (hasFeature(FEATURE_UDP_ENCAP)) {
            out.writeInt(encapType);
            out.writeInt(encapLocalPort);
            out.writeInt(encapRemotePort);
        }

        if (hasFeature(FEATURE_SELECTOR_PROTO)) {
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

        if (hasFeature(FEATURE_TRANSPORT_MODE)) {
            localSocket = in.readParcelable(ParcelFileDescriptor.class.getClassLoader());
            remotePort = in.readInt();
        } else if (hasFeature(FEATURE_TUNNEL_MODE)) {
            localIp = readInetAddressFromParcel(in);
        }

        if (hasFeature(FEATURE_SELECTOR)) {
            spi = in.readInt();
            remoteIp = readInetAddressFromParcel(in);
            direction = in.readInt();
        }

        if (hasFeature(FEATURE_NETWORK)) {
            network = (Network) in.readParcelable(Network.class.getClassLoader());
        }

        if (hasFeature(FEATURE_ENCRYPTION)) {
            encryptionAlgo =
                    (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        }

        if (hasFeature(FEATURE_AUTHENTICATION)) {
            authenticationAlgo =
                    (IpSecAlgorithm) in.readParcelable(IpSecAlgorithm.class.getClassLoader());
        }

        if (hasFeature(FEATURE_UDP_ENCAP)) {
            encapType = in.readInt();
            encapLocalPort = in.readInt();
            encapRemotePort = in.readInt();
        }

        if (hasFeature(FEATURE_SELECTOR_PROTO)) {
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
