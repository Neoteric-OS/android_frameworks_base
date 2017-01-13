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

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** @hide */
public final class IpSecConfig implements Parcelable {
    private static final String TAG = IpSecConfig.class.getSimpleName();

    static final int FEATURE_TRANSPORT_MODE = 1 << 0;
    static final int FEATURE_TUNNEL_MODE    = 1 << 1;
    static final int FEATURE_SELECTOR       = 1 << 2;
    static final int FEATURE_NETWORK        = 1 << 3;
    static final int FEATURE_ENCRYPTION     = 1 << 4;
    static final int FEATURE_AUTHENTICATION = 1 << 5;
    static final int FEATURE_UDP_ENCAP      = 1 << 6;
    static final int FEATURE_SELECTOR_PROTO = 1 << 7;


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
    int direction;

    // Limit selection by network interface
    Network network;

    // Encryption Algorithm
    IpSecAlgorithm encryptionAlgo;

    // Authentication Algorithm
    IpSecAlgorithm authenticationAlgo;

    // For tunnel mode IPv4 UDP Encapsulation
    int encapType;
    int encapLocalPort;
    int encapRemotePort;

    // An optional protocol to match with the selector
    int selectorProto;

    // A bitmask of FEATURE_* indicating which of the fields
    // of this class are valid.
    long features;

    // Parcelable Methods
    public boolean hasFeature(int featureBits) {
        return (features & featureBits) == featureBits;
    }

    public int describeContents() {
        return hasFeature(FEATURE_TRANSPORT_MODE) ?
            Parcelable.CONTENTS_FILE_DESCRIPTOR : 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(features);
        if (hasFeature(FEATURE_TRANSPORT_MODE)) {
            localSocket.writeToParcel(out, 0);
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
            network.writeToParcel(out, 0);
        }

        if (hasFeature(FEATURE_ENCRYPTION)) {
            encryptionAlgo.writeToParcel(out, 0);
        }

        if (hasFeature(FEATURE_AUTHENTICATION)) {
            authenticationAlgo.writeToParcel(out, 0);
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

    private InetAddress readInetAddressFromParcel(Parcel in) {
        String addrString = in.readString();
        try {
            return InetAddress.getByName(addrString);
        } catch(UnknownHostException e) {
            Log.wtf(TAG, "Invalid IpAddress " + addrString);
            return null;
        }
    }
    private IpSecConfig(Parcel in) {
        features = in.readInt();

        if (hasFeature(FEATURE_TRANSPORT_MODE)) {
            localSocket = in.readFileDescriptor();
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
            encryptionAlgo = (IpSecAlgorithm) in.readParcelable(
                    IpSecAlgorithm.class.getClassLoader());
        }

        if (hasFeature(FEATURE_AUTHENTICATION)) {
            authenticationAlgo = (IpSecAlgorithm) in.readParcelable(
                    IpSecAlgorithm.class.getClassLoader());
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

    public static final Parcelable.Creator<IpSecConfig> CREATOR
        = new Parcelable.Creator<IpSecConfig>() {
            public IpSecConfig createFromParcel(Parcel in) {
                return new IpSecConfig(in);
            }

            public IpSecConfig[] newArray(int size) {
                return new IpSecConfig[size];
            }
        };
}

