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

import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.net.InetAddress;

/**
 * This class contains methods for managing IPSec sessions.
 *
 * @hide
 */
public final class IpSecTransform {
    private static final String TAG = IpSecTransform.class.getSimpleName();

    public static final int DIRECTION_IN = 0;
    public static final int DIRECTION_OUT = 1;

    /** @hide */
    public static final int ENCAP_ESP_OVER_IP = 0;
    /** @hide */
    public static final int ENCAP_ESP_OVER_UDP_IKE = 1;
    /** @hide */
    public static final int ENCAP_ESP_OVER_UDP_NON_IKE = 2;

    /** @hide */
    public static final int INVALID_TRANSFORM_ID = -1;

    private final IpSecConfig mConfig;

    private ConnectivityManager.PacketKeepalive mKeepalive;

    private int mTransformId;

    private IpSecTransform(IpSecConfig config) {
        mConfig = config;
        mTransformId = INVALID_TRANSFORM_ID;
    }

    // Package private
    IpSecConfig getConfig() {
        return mConfig;
    }

    public static IpSecTransform.Builder transportModeTransform(
            ParcelFileDescriptor boundSocket,
            InetAddress remoteIp,
            int remotePort,
            int spi,
            int direction) {
        return new IpSecTransform.Builder(boundSocket, remoteIp, remotePort, spi, direction);
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
        //TODO: hook this up to ConnectivityService#startNattKeepalive
    }

    /** @hide */
    void stopKeepalive() {
        //TODO: hook this up to ConnectivityService#stopNattKeepalive
    }

    /** @hide */
    public void setTransformId(int transformId) {
        mTransformId = transformId;
    }

    /** @hide */
    public int getTransformId() {
        return mTransformId;
    }

    public static class Builder {
        private IpSecConfig mConfig;

        private void addSelector(InetAddress remoteIp, int spi, int direction) {
            mConfig.remoteIp = remoteIp;
            mConfig.spi = spi;
            mConfig.direction = direction;
            mConfig.features |= IpSecConfig.Properties.SELECTOR;
        }

        public IpSecTransform.Builder addEncryption(IpSecAlgorithm algo) {
            mConfig.encryptionAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.ENCRYPTION_ALGO;
            return this;
        }

        public IpSecTransform.Builder addAuthentication(IpSecAlgorithm algo) {
            mConfig.authenticationAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.AUTHENTICATION_ALGO;
            return this;
        }

        public IpSecTransform.Builder addNetwork(Network net) {
            mConfig.network = net;
            mConfig.features |= IpSecConfig.Properties.NETWORK;
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

        /** @hide */
        public IpSecTransform.Builder addSelectorProto(int selectorProto) {
            mConfig.selectorProto = selectorProto;
            return this;
        }

        public IpSecTransform build() {
            return new IpSecTransform(mConfig);
        }

        /** @hide */
        private Builder(
                ParcelFileDescriptor boundSocket,
                InetAddress remoteIp,
                int remotePort,
                int spi,
                int direction) {
            mConfig = new IpSecConfig();
            mConfig.localSocket = boundSocket;
            mConfig.remotePort = remotePort;
            mConfig.features |= IpSecConfig.Properties.MODE_TRANSPORT;

            addSelector(remoteIp, spi, direction);
        }

        /** @hide */
        public Builder(InetAddress localIp, InetAddress remoteIp, int spi, int direction) {
            mConfig = new IpSecConfig();
            mConfig.localIp = localIp;
            mConfig.features |= IpSecConfig.Properties.MODE_TUNNEL;

            addSelector(remoteIp, spi, direction);
        }
    }
}
