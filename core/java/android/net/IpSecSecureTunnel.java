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
package android.net;

import android.content.Context;
import android.util.Log;

import dalvik.system.CloseGuard;

import java.io.IOException;
import java.net.InetAddress;

/**
 * This class contains methods for building and managing IPsec-secured tunnels.
 *
 * @hide
 */
public final class IpSecSecureTunnel implements AutoCloseable {
    private static final String TAG = "IpSecSecureTunnel";

    private final CloseGuard mCloseGuard = CloseGuard.get();
    public final IpSecManager.SecurityParameterIndex mSpiIn;
    public final IpSecManager.SecurityParameterIndex mSpiOut;
    public final IpSecTransform mTransformIn;
    public final IpSecTransform mTransformOut;
    public final IpSecManager.UdpEncapsulationSocket mEncapSocket;
    public final IpSecManager.IpSecTunnelInterface mTunnelIntf;

    private IpSecSecureTunnel(
            IpSecManager.SecurityParameterIndex spiIn,
            IpSecManager.SecurityParameterIndex spiOut,
            IpSecTransform transformIn,
            IpSecTransform transformOut,
            IpSecManager.UdpEncapsulationSocket encapSocket,
            IpSecManager.IpSecTunnelInterface tunnelInterface) {
        mSpiIn = spiIn;
        mSpiOut = spiOut;
        mTransformIn = transformIn;
        mTransformOut = transformOut;
        mEncapSocket = encapSocket;
        mTunnelIntf = tunnelInterface;
    }

    /**
     * Add an address to the IpSecTunnelInterface
     *
     * @hide
     */
    public void addInnerAddress(InetAddress address, int prefixLen) throws IOException {
        mTunnelIntf.addAddress(address, prefixLen);
    }

    /**
     * Remove an address from the IpSecTunnelInterface
     *
     * @hide
     */
    public void removeInnerAddress(InetAddress address, int prefixLen) throws IOException {
        mTunnelIntf.removeAddress(address, prefixLen);
    }

    /** Releases a SecureTunnel */
    @Override
    public void close() {
        mTunnelIntf.close();
        mTransformOut.close();
        mTransformIn.close();
        mSpiOut.close();
        mSpiIn.close();
        if (mEncapSocket != null) {
            try {
                mEncapSocket.close();
            } catch (Exception e) {
                // On close we swallow all random exceptions since failure to close is not
                // actionable by the user.
                Log.e(TAG, "Failed to close SecureTunnel, Exception=" + e);
            }
        }

        mCloseGuard.close();
    }

    /** Check that the Secure Tunnel was closed properly. */
    @Override
    protected void finalize() throws Throwable {
        if (mCloseGuard != null) {
            mCloseGuard.warnIfOpen();
        }

        close();
    }

    /**
     * Builder for IpSecSecureTunnel. Allows configuration of algorithms and encapsulation.
     *
     * <p>Validity of config checked at build time by IpSecService.
     *
     * @hide
     */
    public static class Builder {
        private final IpSecManager mISM;
        private final InetAddress mSourceAddr;
        private final InetAddress mDestAddr;
        private final Network mUnderlyingNetwork;
        private final IpSecTransform.Builder mInTransformBuilder;
        private final IpSecTransform.Builder mOutTransformBuilder;

        private boolean mIsEncapEnabled = false;

        /** Create a builder with the minimal required information. */
        public Builder(
                Context context,
                InetAddress sourceAddr,
                InetAddress destAddr,
                Network underlyingNetwork) {
            mISM = (IpSecManager) context.getSystemService(Context.IPSEC_SERVICE);
            mSourceAddr = sourceAddr;
            mDestAddr = destAddr;
            mUnderlyingNetwork = underlyingNetwork;
            mInTransformBuilder = new IpSecTransform.Builder(context);
            mOutTransformBuilder = new IpSecTransform.Builder(context);

            if (!context.getOpPackageName().equals("android.net.cts")) {
                throw new SecurityException("Test methods for CTS only");
            }
        }

        private IpSecTransform.Builder getBuilderForDirection(int direction) {
            switch (direction) {
                case IpSecManager.DIRECTION_IN:
                    return mInTransformBuilder;
                case IpSecManager.DIRECTION_OUT:
                    return mOutTransformBuilder;
                default:
                    throw new IllegalArgumentException("Invalid direction");
            }
        }

        /** Set the authentication algorithm for the given direction */
        public Builder setAuthentication(int direction, IpSecAlgorithm auth) {
            getBuilderForDirection(direction).setAuthentication(auth);
            return this;
        }

        /** Set the encryption algorithm for the given direction */
        public Builder setEncryption(int direction, IpSecAlgorithm crypt) {
            getBuilderForDirection(direction).setEncryption(crypt);
            return this;
        }

        /** Set the authenticated encryption algorithm for the given direction */
        public Builder setAuthenticatedEncryption(int direction, IpSecAlgorithm aead) {
            getBuilderForDirection(direction).setAuthenticatedEncryption(aead);
            return this;
        }

        /** Set whether UDP encapsulation is used */
        public Builder useEncap(boolean enabled) {
            mIsEncapEnabled = enabled;
            return this;
        }

        /**
         * Build the relevant resource objects, returned and tracked in an instance of
         * IpSecSecureTunnel
         */
        public IpSecSecureTunnel build()
                throws IOException, IpSecManager.SpiUnavailableException,
                        IpSecManager.ResourceUnavailableException {
            boolean useMirroredTransforms = mSourceAddr.equals(mDestAddr);

            IpSecManager.SecurityParameterIndex spiIn =
                    mISM.allocateSecurityParameterIndex(mSourceAddr);
            IpSecManager.SecurityParameterIndex spiOut =
                    useMirroredTransforms ? spiIn : mISM.allocateSecurityParameterIndex(mDestAddr);

            IpSecManager.UdpEncapsulationSocket encapSocket = null;
            if (mIsEncapEnabled) {
                encapSocket = mISM.openUdpEncapsulationSocket();
                mInTransformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
                mOutTransformBuilder.setIpv4Encapsulation(encapSocket, encapSocket.getPort());
            }

            IpSecManager.IpSecTunnelInterface tunnelIntf =
                    mISM.createIpSecTunnelInterface(mSourceAddr, mDestAddr, mUnderlyingNetwork);

            IpSecTransform transformIn =
                    mInTransformBuilder.buildTunnelModeTransform(mDestAddr, spiIn);
            IpSecTransform transformOut =
                    useMirroredTransforms
                            ? transformIn
                            : mOutTransformBuilder.buildTunnelModeTransform(mSourceAddr, spiOut);

            mISM.applyTunnelModeTransform(tunnelIntf, IpSecManager.DIRECTION_IN, transformIn);
            mISM.applyTunnelModeTransform(tunnelIntf, IpSecManager.DIRECTION_OUT, transformOut);

            return new IpSecSecureTunnel(
                    spiIn, spiOut, transformIn, transformOut, encapSocket, tunnelIntf);
        }
    }
}
