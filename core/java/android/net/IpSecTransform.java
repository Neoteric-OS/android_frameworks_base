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

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.content.Context;
import android.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetAddress;

/**
 * This class represents an IpSecTransform, which encapsulates both properties and state of IpSec.
 *
 * <p>IpSecTransforms must be built from an IpSecTransform.Builder, and they must persist throughout
 * the lifetime of the underlying transform. If a transform object leaves scope, the underlying
 * transform may be disabled automatically.
 *
 * <p>An IpSecTransform may either represent a tunnel mode transform that operates on a wide array
 * of traffic or may represent a transport mode transform operating on a single socket.
 */
public class IpSecTransform {
    private static final String TAG = "IpSecTransform";

    /**
     * IpSec Transform applies to inbound traffic.
     *
     * <p>IpSecTransforms are fundamentally unidirectional. Thus a two way connection requires two
     * Transform objects. This primitive specifies the direction of the transform as inbound.
     */
    public static final int DIRECTION_IN = 0;

    /**
     * IpSec Transform applies to outbound traffic.
     *
     * <p>See detail in {@link #DIRECTION_IN}
     */
    public static final int DIRECTION_OUT = 1;

    /** @hide */
    @IntDef(value = {DIRECTION_IN, DIRECTION_OUT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransformDirection {}

    /**
     * Specifies IpSec traffic will be encapsulated in IP. This is the default mode for a Tunnel
     * mode transform, and needs not be specified explicitly.
     */
    public static final int ENCAP_ESP_OVER_IP = 0;

    /**
     * IpSec traffic will be encapsulated within UDP using the standard IKE implementation. These
     * packets will have a UDP header applied encapsulating the ESP header.
     */
    public static final int ENCAP_ESP_OVER_UDP_IKE = 1;

    /**
     * IpSec traffic will be encapsulated within a UDP header with an additional 8-byte header pad
     * that prevents traffic from being interpreted as IKE or as ESP over UDP.
     */
    public static final int ENCAP_ESP_OVER_UDP_NON_IKE = 2;

    /** @hide */
    @IntDef(value = {ENCAP_ESP_OVER_IP, ENCAP_ESP_OVER_UDP_IKE, ENCAP_ESP_OVER_UDP_NON_IKE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EncapType {}

    /**
     * Sentinel for an invalid transform (means that this transform is inactive).
     *
     * @hide
     */
    public static final int INVALID_TRANSFORM_ID = -1;

    /** package private */
    IpSecTransform(IpSecConfig config) {
        mConfig = config;
        mTransformId = INVALID_TRANSFORM_ID;
    }

    IpSecConfig getConfig() {
        return mConfig;
    }

    private final IpSecConfig mConfig;
    private int mTransformId;

    private ConnectivityManager.PacketKeepalive mKeepalive;
    private int mKeepaliveStatus = ConnectivityManager.PacketKeepalive.NO_KEEPALIVE;
    private Object mKeepaliveSyncLock = new Object();
    private ConnectivityManager.PacketKeepaliveCallback mKeepaliveCallback =
            new ConnectivityManager.PacketKeepaliveCallback() {

                @Override
                public void onStarted() {
                    synchronized (mKeepaliveSyncLock) {
                        mKeepaliveStatus = ConnectivityManager.PacketKeepalive.SUCCESS;
                        mKeepaliveSyncLock.notifyAll();
                    }
                }

                @Override
                public void onStopped() {
                    synchronized (mKeepaliveSyncLock) {
                        mKeepaliveStatus = ConnectivityManager.PacketKeepalive.NO_KEEPALIVE;
                        mKeepaliveSyncLock.notifyAll();
                    }
                }

                @Override
                public void onError(int error) {
                    synchronized (mKeepaliveSyncLock) {
                        mKeepaliveStatus = error;
                        mKeepaliveSyncLock.notifyAll();
                    }
                }
            };

    /* Package */
    void startKeepalive(Context c) {
        if (mConfig.hasProperty(IpSecConfig.Properties.NATT_KEEPALIVE) == false) {
            return;
        }

        ConnectivityManager cm =
                (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (mKeepalive != null) {
            Log.e(TAG, "Keepalive already started for this IpSecTransform.");
            return;
        }

        synchronized (mKeepaliveSyncLock) {
            mKeepalive =
                    cm.startNattKeepalive(
                            mConfig.getNetwork(),
                            mConfig.getNattKeepaliveInterval(),
                            mKeepaliveCallback,
                            mConfig.getLocalIp(),
                            mConfig.getEncapLocalPort(),
                            mConfig.getRemoteIp());
            try {
                mKeepaliveSyncLock.wait(2000);
            } catch (InterruptedException e) {
            }
        }
        if (mKeepaliveStatus != ConnectivityManager.PacketKeepalive.SUCCESS) {
            throw new UnsupportedOperationException("Packet Keepalive cannot be started");
        }
    }

    /* Package */
    void stopKeepalive() {
        if (mKeepalive == null) {
            return;
        }
        mKeepalive.stop();
        synchronized (mKeepaliveSyncLock) {
            if (mKeepaliveStatus == ConnectivityManager.PacketKeepalive.SUCCESS) {
                try {
                    mKeepaliveSyncLock.wait(2000);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /* Package */
    void setTransformId(int transformId) {
        mTransformId = transformId;
    }

    /* Package */
    int getTransformId() {
        return mTransformId;
    }

    public static class TunnelModeBuilder extends IpSecTransform.Builder {
        /**
         * Return an IpSecTransform.Builder object that will build an IPSec tunnel.
         *
         * <p>Get a transform builder that will permit the addition of Transform parameters that are
         * relevant to tunnel mode. This will cause all traffic matched by the combination of
         * remoteIp, SPI, and direction to be transformed if it contains an IpSec header.
         *
         * @param localIp the {@link InetAddress} that provides the local endpoint for this IpSec
         *     tunnel.
         * @param remoteIp the {@link InetAddress} that, when matched on traffic to/from this socket
         *     will cause the transform to be applied.
         * @param spi a unique 32-bit SPI identifying this transform
         * @param direction {@link #DIRECTION_IN} or {@link #DIRECTION_OUT}
         */
        public TunnelModeBuilder(
                InetAddress localIp, InetAddress remoteIp, int spi, int direction) {
            super(remoteIp, spi, direction);
            mConfig.localIp = localIp;
            mConfig.features |= IpSecConfig.Properties.MODE_TUNNEL;
        }

        public IpSecTunnelModeTransform build() {
            if (mConfig.hasProperty(IpSecConfig.Properties.NATT_KEEPALIVE)
                    != mConfig.hasProperty(IpSecConfig.Properties.UDP_ENCAP)) {
                throw new IllegalArgumentException("Natt Keepalive requires UDP Encapsulation");
            }
            return new IpSecTunnelModeTransform(mConfig);
        }
    };

    public static class TransportModeBuilder extends IpSecTransform.Builder {
        /**
         * Return an IpSecTransform.Builder object that will build a transform applied to a stream
         * socket.
         *
         * <p>Get a transform builder that will permit the addition of Transform parameters that are
         * relevant to transport mode.
         *
         * @param remoteIp the {@link InetAddress} that, when matched on traffic to/from this socket
         *     will cause the transform to be applied.
         * @param spi a unique 32-bit SPI identifying this transform
         * @param direction {@link #DIRECTION_IN} or {@link #DIRECTION_OUT}
         */
        public TransportModeBuilder(InetAddress remoteIp, int spi, int direction) {
            super(remoteIp, spi, direction);
            mConfig.features |= IpSecConfig.Properties.MODE_TRANSPORT;
        }

        public IpSecTransportModeTransform build() {
            if (mConfig.hasProperty(IpSecConfig.Properties.NATT_KEEPALIVE)
                    != mConfig.hasProperty(IpSecConfig.Properties.UDP_ENCAP)) {
                throw new IllegalArgumentException("Natt Keepalive requires UDP Encapsulation");
            }
            return new IpSecTransportModeTransform(mConfig);
        }
    };

    /**
     * Builder object to facilitate the creation of IpSecTransform objects.
     *
     * <p>A builder object may be instantiated by calling on of the factory methods to fetch either
     * a tunnel mode or transport mode transform object. Apply additional properties to the
     * transform and then call the build() method to return an IpSecTransform object.
     *
     * @see IpSecTransform#transportModeTransform
     * @see IpSecTransform#tunnelModeTransform
     */
    public static class Builder {
        IpSecConfig mConfig;

        private void addSelector(InetAddress remoteIp, int spi, @TransformDirection int direction) {
            mConfig.remoteIp = remoteIp;
            mConfig.spi = spi;
            mConfig.direction = direction;
            mConfig.features |= IpSecConfig.Properties.SELECTOR;
        }

        /** Add an encryption algorithm to the transform. */
        public IpSecTransform.Builder setEncryption(IpSecAlgorithm algo) {
            mConfig.encryptionAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.ENCRYPTION_ALGO;
            return this;
        }

        /** Add an authentication/integrity algorithm to the transform. */
        public IpSecTransform.Builder setAuthentication(IpSecAlgorithm algo) {
            mConfig.authenticationAlgo = algo;
            mConfig.features |= IpSecConfig.Properties.AUTHENTICATION_ALGO;
            return this;
        }

        /**
         * Add UDP encapsulation to an IPv4 tunnel mode transform
         *
         * <p>This option allows IpSec traffic to pass through NAT. Refer to RFC 3947 and 3948 for
         * details on how UDP should be applied to IpSec.
         */
        public IpSecTransform.Builder setIpv4Encapsulation(
                @EncapType int encapType, int localPort, int remotePort) {
            mConfig.encapType = encapType;
            mConfig.encapLocalPort = localPort;
            mConfig.encapRemotePort = remotePort;
            mConfig.features |= IpSecConfig.Properties.UDP_ENCAP;
            return this;
        }

        /**
         * Provides encapsulation of the traffic on a given socket bound for a specific destination.
         * This option is only applicable when destination IP address is IPv4, the direction is
         * {@link IpSecTransform#DIRECTION_OUT}, and UDP Encapsulation is requested.
         *
         * @param intervalSeconds the maximum number of seconds between keepalive packets.
         * @hide
         */
        @SystemApi
        public IpSecTransform.Builder setNattKeepalive(int intervalSeconds) {
            mConfig.nattKeepaliveInterval = intervalSeconds;
            mConfig.features |= IpSecConfig.Properties.NATT_KEEPALIVE;
            return this;
        }

        /**
         * Limit the selected traffic to a particular IP protocol, such as UDP, TCP, or ESP. By
         * default, all protocols will be matched.
         *
         * @param selectorProto particular protocol to be matched by the selector.
         * @hide
         */
        @SystemApi
        public IpSecTransform.Builder setProtocol(int selectorProto) {
            mConfig.selectorProto = selectorProto;
            return this;
        }

        /** @hide */
        public Builder(InetAddress remoteIp, int spi, int direction) {
            mConfig = new IpSecConfig();
            addSelector(remoteIp, spi, direction);
        }
    }
}
