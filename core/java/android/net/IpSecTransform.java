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
public final class IpSecTransform {
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

    /** @hide */
    private static final int MODE_TUNNEL = 0;

    /** @hide */
    private static final int MODE_TRANSPORT = 1;

    /**
     * Specifies IpSec traffic will be encapsulated in IP. This is the default mode for a Tunnel
     * mode transform, and needs not be specified explicitly.
     *
     * @hide
     */
    public static final int ENCAP_ESPINIP = 0;

    /**
     * IpSec traffic will be encapsulated within UDP using the standard IKE implementation. These
     * packets will have a UDP header applied encapsulating the ESP header.
     */
    public static final int ENCAP_ESPINUDP = 1;

    /**
     * IpSec traffic will be encapsulated within a UDP header with an additional 8-byte header pad
     * that prevents traffic from being interpreted as IKE or as ESP over UDP.
     */
    public static final int ENCAP_ESPINUDP_NONIKE = 2;

    /** @hide */
    @IntDef(value = {ENCAP_ESPINIP, ENCAP_ESPINUDP, ENCAP_ESPINUDP_NONIKE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EncapType {}

    /**
     * Sentinel for an invalid transform (means that this transform is inactive).
     *
     * @hide
     */
    public static final int INVALID_TRANSFORM_ID = -1;

    private IpSecTransform(IpSecConfig config) {
        mConfig = config;
        mTransformId = INVALID_TRANSFORM_ID;
    }

    /* Package */
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
        if (mConfig.getNattKeepaliveInterval() == 0) {
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

    /**
     * Builder object to facilitate the creation of IpSecTransform objects.
     *
     * <p>A builder object may be instantiated by calling on of the factory methods to fetch either
     * a tunnel mode or transport mode transform object. Apply additional properties to the
     * transform and then call the build() method to return an IpSecTransform object.
     *
     * @see Builder#newTransportModeTransform()
     * @see Builder#newTunnelModeTransform()
     */
    public static class Builder {
        private IpSecConfig mConfig;

        /**
         * Add an encryption algorithm to the transform for the given direction.
         *
         * @param direction either {@link #DIRECTION_IN or #DIRECTION_OUT}
         * @param algo {@link IpSecAlgorithm} specifying the encryption to be applied.
         */
        public IpSecTransform.Builder setEncryption(
                @TransformDirection int direction, IpSecAlgorithm algo) {
            mConfig.flow[direction].encryptionAlgo = algo;
            return this;
        }

        /**
         * Add an authentication/integrity algorithm to the transform.
         *
         * @param direction either {@link #DIRECTION_IN or #DIRECTION_OUT}
         * @param algo {@link IpSecAlgorithm} specifying the authentication to be applied.
         */
        public IpSecTransform.Builder setAuthentication(
                @TransformDirection int direction, IpSecAlgorithm algo) {
            mConfig.flow[direction].authenticationAlgo = algo;
            return this;
        }

        /**
         * Set the SPI, which uniquely identifies a particular IPSec session from others. Because
         * IPSec operates at the IP layer, this 32-bit identifier uniquely identifies packets
         * between a given sender and receiver.
         *
         * <p>Care should be chosen when selecting an SPI to ensure that is is as unique as
         * possible. Random number generation is a reasonable approach to selecting an SPI.
         *
         * @param direction either {@link #DIRECTION_IN or #DIRECTION_OUT}
         * @param spi a unique 32-bit integer to identify transformed traffic
         */
        public IpSecTransform.Builder setSpi(@TransformDirection int direction, int spi) {
            mConfig.flow[direction].spi = spi;
            return this;
        }

        /**
         * Specify the network on which this transform will emit its traffic; (otherwise it will
         * emit on the default network).
         *
         * <p>Restricts the transformed traffic to a particular {@link Network}. This is required in
         * tunnel mode.
         *
         * @hide
         */
        @SystemApi
        public IpSecTransform.Builder setViaNetwork(Network net) {
            mConfig.network = net;
            return this;
        }

        /**
         * Add UDP encapsulation to an IPv4 transform
         *
         * <p>This option allows IpSec traffic to pass through NAT. Refer to RFC 3947 and 3948 for
         * details on how UDP should be applied to IpSec.
         *
         * @param encapType an {@link #ENCAP_ESPINUDP} or {@link #ENCAP_ESPINUDP_NONIKE} indicating
         *     whether and how IPSec traffic will be encapsulated in UDP.
         * @param localPort a UDP port number that will be reserved for sending and receiving
         *     encapsulating traffic
         * @param remotePort the UDP port number of the remote that will send and receive
         *     encapsulated traffic. In the case of IKE, this is likely port 4500.
         */
        public IpSecTransform.Builder setIpv4Encapsulation(
                @EncapType int encapType, int localPort, int remotePort) {
            // TODO: check encap type is valid.
            mConfig.encapType = encapType;
            mConfig.encapLocalPort = localPort;
            mConfig.encapRemotePort = remotePort;
            return this;
        }

        /**
         * Send a NATT Keepalive packet with a given maximum interval. This will create an offloaded
         * request to do power-efficient NATT Keepalive.
         *
         * @param intervalSeconds the maximum number of seconds between keepalive packets.
         * @hide
         */
        @SystemApi
        public IpSecTransform.Builder setNattKeepalive(int intervalSeconds) {
            mConfig.nattKeepaliveInterval = intervalSeconds;
            return this;
        }

        /**
         * Build and return an {@link IpSecTransform} object as a Transport Mode Transform. Some
         * parameters have interdependencies that are checked at build time.
         *
         * @param remoteAddress the {@link InetAddress} that, when matched on traffic to/from this
         *     socket will cause the transform to be applied.
         * @throws IllegalArgumentException indicating that a particular combination of transform
         *     properties is invalid.
         */
        public IpSecTransform buildTransportModeTransform(InetAddress remoteAddress) {
            //FIXME: argument validation here
            //throw new IllegalArgumentException("Natt Keepalive requires UDP Encapsulation");
            mConfig.mode = MODE_TRANSPORT;
            mConfig.remoteAddress = remoteAddress;
            return new IpSecTransform(mConfig);
        }

        /**
         * Build and return an {@link IpSecTransform} object as a Tunnel Mode Transform. Some
         * parameters have interdependencies that are checked at build time.
         *
         * @param localAddress the {@link InetAddress} that provides the local endpoint for this
         *     IpSec tunnel. This is almost certainly an address belonging to the {@link Network}
         *     that will originate the traffic.
         * @param remoteAddress the {@link InetAddress} that, when matched on traffic to/from this
         *     socket will cause the transform to be applied.
         * @throws IllegalArgumentException indicating that a particular combination of transform
         *     properties is invalid.
         */
        public IpSecTransform buildTunnelModeTransform(
                InetAddress localAddress, InetAddress remoteAddress) {
            //FIXME: argument validation here
            //throw new IllegalArgumentException("Natt Keepalive requires UDP Encapsulation");
            mConfig.localAddress = localAddress;
            mConfig.remoteAddress = remoteAddress;
            mConfig.mode = MODE_TUNNEL;
            return new IpSecTransform(mConfig);
        }

        public Builder() {
            mConfig = new IpSecConfig();
        }
    }
}
