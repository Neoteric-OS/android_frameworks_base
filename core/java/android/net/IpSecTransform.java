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
import android.net.ConnectivityManager;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.net.InetAddress;

/**
 * This class contains methods for managing IPSec sessions.
 * @hide
 */
public final class IpSecTransform {
    private static final String TAG = IpSecTransform.class.getSimpleName();

    /** @hide */
    public static final int FLOW_DIRECTION_IN = 0;
    /** @hide */
    public static final int FLOW_DIRECTION_OUT = 1;

    /** @hide */
    public static final int ENCAP_ESP_OVER_IP = 0;
    /** @hide */
    public static final int ENCAP_ESP_OVER_UDP_IKE = 1;
    /** @hide */
    public static final int ENCAP_ESP_OVER_UDP_NON_IKE = 2;

    private final IpSecConfig mConfig;

    private ConnectivityManager.PacketKeepalive mKeepalive;

    private IpSecTransform(IpSecConfig config) {
        mConfig = config;
    }

    public static IpSecTransform.Builder transportModeTransform(
            ParcelFileDescriptor boundSocket, int remotePort,
            InetAddress remoteIp, int spi, int direction) {
        return new IpSecTransform.Builder(
                boundSocket, remotePort, remoteIp, spi, direction);
    }

    /** @hide */
    public static IpSecTransform.Builder tunnelModeTransform(
            InetAddress localIp, InetAddress remoteIp, int spi, int direction) {
        return new IpSecTransform.Builder(localIp, remoteIp, spi, direction);
    }

    /** @hide */
    void startKeepalive(int intervalSeconds) {
        if (mKeepalive != null) {
            Log.e(TAG, "Keepalive already started for this IpSecTransform.");
            return;
        }
        //TODO: hook this up to ConnectivityService.startNattKeepalive
    }

    /** @hide */
    void stopKeepalive() {
        //TODO: hook this up to ConnectivityService.stopNattKeepalive
    }

    public static class Builder {
        private IpSecConfig mConfig;

        private void addSelector(InetAddress remoteIp, int spi, int direction) {
            mConfig.remoteIp = remoteIp;
            mConfig.spi = spi;
            mConfig.direction = direction;
            mConfig.features |= IpSecConfig.FEATURE_SELECTOR;
        }

        /** @hide */
        public IpSecTransform.Builder addEncryption(IpSecAlgorithm algo) {
            mConfig.encryptionAlgo = algo;
            mConfig.features |= IpSecConfig.FEATURE_ENCRYPTION;
            return this;
        }

        /** @hide */
        public IpSecTransform.Builder addAuthentication(IpSecAlgorithm algo) {
            mConfig.authenticationAlgo = algo;
            mConfig.features |= IpSecConfig.FEATURE_AUTHENTICATION;
            return this;
        }

        /** @hide */
        public IpSecTransform.Builder addIpv4Encapsulation(
                int encapType, int localPort, int remotePort) {
            mConfig.encapType = encapType;
            mConfig.encapLocalPort = localPort;
            mConfig.encapRemotePort = remotePort;
            return this;
        }

        public IpSecTransform.Builder addSelectorProto(int selectorProto) {
            mConfig.selectorProto = selectorProto;
            return this;
        }

        public IpSecTransform build() {
            return new IpSecTransform(mConfig);
        }

        /** @hide */
        private Builder(ParcelFileDescriptor boundSocket, int remotePort,
                InetAddress remoteIp, int spi, int direction) {
            mConfig = new IpSecConfig();
            mConfig.localSocket = boundSocket;
            mConfig.remotePort = remotePort;
            mConfig.features |= IpSecConfig.FEATURE_TRANSPORT_MODE;

            addSelector(remoteIp, spi, direction);
        }

        /** @hide */
        public Builder(InetAddress localIp, InetAddress remoteIp,
                int spi, int direction) {
            mConfig = new IpSecConfig();
            mConfig.localIp = localIp;
            mConfig.features |= IpSecConfig.FEATURE_TUNNEL_MODE;

            addSelector(remoteIp, spi, direction);
        }
    }
}
