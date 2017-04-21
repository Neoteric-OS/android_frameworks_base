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

import android.annotation.NonNull;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.AndroidException;
import dalvik.system.CloseGuard;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * This class contains methods for managing IPsec sessions, which will perform kernel-space
 * encryption and decryption of socket or Network traffic.
 *
 * <p>An IpSecManager may be obtained by calling {@link
 * android.content.Context#getSystemService(String) Context#getSystemService(String)} with {@link
 * android.content.Context#IPSEC_SERVICE Context#IPSEC_SERVICE}
 *
 * @hide
 */
public final class IpSecManager {
    private static final String TAG = "IpSecManager";

    /**
     * The Security Parameter Index (SPI) 0 indicates an unknown or invalid index.
     *
     * <p>No IPsec packet may contain an SPI of 0.
     */
    public static final int INVALID_SECURITY_PARAMETER_INDEX = 0;

    /** @hide */
    public interface Status {
        public static final int OK = 0;
        public static final int RESOURCE_UNAVAILABLE = 1;
        public static final int SPI_UNAVAILABLE = 2;
    }

    /** @hide */
    public static final String KEY_STATUS = "status";
    /** @hide */
    public static final String KEY_RESOURCE_ID = "resourceId";
    /** @hide */
    public static final String KEY_SPI = "spi";
    /** @hide */
    public static final int INVALID_RESOURCE_ID = 0;

    /**
     * Thrown to indicate that a requested SPI is in use.
     *
     * <p>The combination of remote {@code  InetAddress} and SPI must be globally unique. If this
     * error is encountered, a new SPI is required before a transform may be created. This error can
     * be avoided by calling {@link IpSecManager#reserveSecurityParameterIndex}.
     */
    public static final class SpiUnavailableException extends AndroidException {
        private final int mSpi;

        /**
         * Construct an exception indicating that a transform with the given SPI is already in use
         * or otherwise unavailable.
         *
         * @param msg description indicating the colliding SPI
         * @param spi the SPI that could not be used due to a collision
         */
        SpiUnavailableException(String msg, int spi) {
            super(msg + " (spi: " + spi + ")");
            mSpi = spi;
        }

        /** Retrieve the SPI that caused a collision. */
        public int getSpi() {
            return mSpi;
        }
    }

    /**
     * Thrown to indicate that an IPsec resource is unavailable.
     *
     * <p>This could apply to resources such as sockets, {@link SecurityParameterIndex}, {@link
     * IpSecTransform}, or other system resources. If this exception is thrown, users should release
     * allocated objects of the type requested.
     */
    public static final class ResourceUnavailableException extends AndroidException {

        ResourceUnavailableException(String msg) {
            super(msg);
        }
    }

    private final IIpSecService mService;

    /**
     * This class represents a reserved SPI.
     *
     * <p>Objects of this type are used to track reserved security parameter indexes. They can be
     * obtained by calling {@link IpSecManager#reserveSecurityParameterIndex} and must be released
     * by calling {@link #close()} when they are no longer needed.
     */
    public static final class SecurityParameterIndex implements AutoCloseable {
        private final IIpSecService mService;
        private final InetAddress mRemoteAddress;
        private final CloseGuard mCloseGuard = CloseGuard.get();
        private int mSpi = INVALID_SECURITY_PARAMETER_INDEX;
        private int mResourceId;

        /** Return the underlying SPI held by this object */
        public int getSpi() {
            return mSpi;
        }

        /**
         * Release an SPI that was previously reserved.
         *
         * <p>Release an SPI for use by other users in the system. If a SecurityParameterIndex is
         * applied to an IpSecTransform, it will become unusable for future transforms but should
         * still be closed to ensure system resources are released.
         */
        @Override
        public void close() {
            mSpi = INVALID_SECURITY_PARAMETER_INDEX;
            mCloseGuard.close();
        }

        @Override
        protected void finalize() {
            if (mCloseGuard != null) {
                mCloseGuard.warnIfOpen();
            }

            close();
        }

        private SecurityParameterIndex(
                @NonNull IIpSecService service, int direction, InetAddress remoteAddress, int spi)
                throws ResourceUnavailableException, SpiUnavailableException {
            mService = service;
            mRemoteAddress = remoteAddress;
            try {
                Bundle result =
                        mService.reserveSecurityParameterIndex(
                                direction, remoteAddress.getHostAddress(), spi, new Binder());

                if (result == null) {
                    throw new NullPointerException("Received null response from IpSecService");
                }

                int status = result.getInt(KEY_STATUS);
                switch (status) {
                    case Status.OK:
                        break;
                    case Status.RESOURCE_UNAVAILABLE:
                        throw new ResourceUnavailableException(
                                "No more SPIs may be allocated by this requester.");
                    case Status.SPI_UNAVAILABLE:
                        throw new SpiUnavailableException("Requested SPI is unavailable", spi);
                    default:
                        throw new RuntimeException(
                                "Unknown status returned by IpSecService: " + status);
                }
                mSpi = result.getInt(KEY_SPI);
                mResourceId = result.getInt(KEY_RESOURCE_ID);

                if (mSpi == INVALID_SECURITY_PARAMETER_INDEX) {
                    throw new RuntimeException("Invalid SPI returned by IpSecService: " + status);
                }

                if (mResourceId == INVALID_RESOURCE_ID) {
                    throw new RuntimeException(
                            "Invalid Resource ID returned by IpSecService: " + status);
                }

            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
            mCloseGuard.open("open");
        }
    }

    /**
     * Reserve a random SPI for traffic bound to or from the specified remote address.
     *
     * <p>If successful, this SPI is guaranteed available until released by a call to {@link
     * SecurityParameterIndex#close()}.
     *
     * @param direction {@link IpSecTransform#DIRECTION_IN} or {@link IpSecTransform#DIRECTION_OUT}
     * @param remoteAddress address of the remote. SPIs must be unique for each remoteAddress
     * @return the reserved SecurityParameterIndex
     * @throws ResourceUnavailableException indicating that too many SPIs are currently allocated
     *     for this user
     */
    public SecurityParameterIndex reserveSecurityParameterIndex(
            int direction, InetAddress remoteAddress)
            throws ResourceUnavailableException {
        try {
            return new SecurityParameterIndex(
                    mService,
                    direction,
                    remoteAddress,
                    IpSecManager.INVALID_SECURITY_PARAMETER_INDEX);
        } catch (SpiUnavailableException unlikely) {
            throw new ResourceUnavailableException("No SPIs available");
        }
    }

    /**
     * Reserve the requested SPI for traffic bound to or from the specified remote address.
     *
     * <p>If successful, this SPI is guaranteed available until released by a call to {@link
     * SecurityParameterIndex#close()}.
     *
     * @param direction {@link IpSecTransform#DIRECTION_IN} or {@link IpSecTransform#DIRECTION_OUT}
     * @param remoteAddress address of the remote. SPIs must be unique for each remoteAddress
     * @param requestedSpi the requested SPI, or '0' to allocate a random SPI
     * @return the reserved SecurityParameterIndex
     * @throws ResourceUnavailableException indicating that too many SPIs are currently allocated
     *     for this user
     * @throws SpiUnavailableException indicating that the requested SPI could not be reserved
     */
    public SecurityParameterIndex reserveSecurityParameterIndex(
            int direction, InetAddress remoteAddress, int requestedSpi)
            throws SpiUnavailableException, ResourceUnavailableException {
        if (requestedSpi == IpSecManager.INVALID_SECURITY_PARAMETER_INDEX) {
            throw new IllegalArgumentException("Requested SPI must be a valid (non-zero) SPI");
        }
        return new SecurityParameterIndex(mService, direction, remoteAddress, requestedSpi);
    }

    /**
     * Apply an IPsec transform to a stream socket.
     *
     * <p>This applies transport mode encapsulation to the given socket. Once applied, I/O on the
     * socket will be encapsulated according to the parameters of the {@code IpSecTransform}. When
     * the transform is removed from the socket by calling {@link #removeTransportModeTransform},
     * unprotected traffic can resume on that socket.
     *
     * <p>For security reasons, the destination address of any traffic on the socket must match the
     * remote {@code  InetAddress} of the {@code IpSecTransform}. Attempts to send traffic to any
     * other IP address will result in an IOException. In addition, reads and writes on the socket
     * will throw IOException if the user deactivates the transform (by calling {@link
     * IpSecTransform#close()}) without calling {@link #removeTransportModeTransform}.
     *
     * <h4>Rekey Procedure</h4> <p>When applying a new tranform to a socket, the previous transform
     * will be removed. However, inbound traffic on the old transform will continue to be decrypted
     * until that transform is deallocated by calling {@link IpSecTransform#close()}. This overlap
     * allows rekey procedures where both transforms are valid until both endpoints are using the
     * new transform and all in-flight packets have been received.
     *
     * @param socket a stream socket
     * @param transform a transport mode {@code IpSecTransform}
     * @throws IOException indicating that the transform could not be applied
     * @hide
     */
    public void applyTransportModeTransform(Socket socket, IpSecTransform transform)
            throws IOException {
        applyTransportModeTransform(ParcelFileDescriptor.fromSocket(socket), transform);
    }

    /**
     * Apply an IPsec transform to a datagram socket.
     *
     * <p>This applies transport mode encapsulation to the given socket. Once applied, I/O on the
     * socket will be encapsulated according to the parameters of the {@code IpSecTransform}. When
     * the transform is removed from the socket by calling {@link #removeTransportModeTransform},
     * unprotected traffic can resume on that socket.
     *
     * <p>For security reasons, the destination address of any traffic on the socket must match the
     * remote {@code InetAddress} of the {@code IpSecTransform}. Attempts to send traffic to any
     * other IP address will result in an IOException. In addition, reads and writes on the socket
     * will throw IOException if the user deactivates the transform (by calling {@link
     * IpSecTransform#close()}) without calling {@link #removeTransportModeTransform}.
     *
     * <h4>Rekey Procedure</h4> <p>When applying a new tranform to a socket, the previous transform
     * will be removed. However, inbound traffic on the old transform will continue to be decrypted
     * until that transform is deallocated by calling {@link IpSecTransform#close()}. This overlap
     * allows rekey procedures where both transforms are valid until both endpoints are using the
     * new transform and all in-flight packets have been received.
     *
     * @param socket a datagram socket
     * @param transform a transport mode {@code IpSecTransform}
     * @throws IOException indicating that the transform could not be applied
     * @hide
     */
    public void applyTransportModeTransform(DatagramSocket socket, IpSecTransform transform)
            throws IOException {
        applyTransportModeTransform(ParcelFileDescriptor.fromDatagramSocket(socket), transform);
    }

    /* Call down to apply a transform. */
    private void applyTransportModeTransform(ParcelFileDescriptor pfd, IpSecTransform transform) {
        // TODO: Verify that the documented rekey procedure works.
        try {
            mService.applyTransportModeTransform(pfd, transform.getResourceId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Apply an IPsec transform to a socket.
     *
     * <p>This applies transport mode encapsulation to the given socket. Once applied, I/O on the
     * socket will be encapsulated according to the parameters of the {@code IpSecTransform}. When
     * the transform is removed from the socket by calling {@link #removeTransportModeTransform},
     * unprotected traffic can resume on that socket.
     *
     * <p>For security reasons, the destination address of any traffic on the socket must match the
     * remote {@code InetAddress} of the {@code IpSecTransform}. Attempts to send traffic to any
     * other IP address will result in an IOException. In addition, reads and writes on the socket
     * will throw IOException if the user deactivates the transform (by calling {@link
     * IpSecTransform#close()}) without calling {@link #removeTransportModeTransform}.
     *
     * <h4>Rekey Procedure</h4> <p>When applying a new tranform to a socket, the previous transform
     * will be removed. However, inbound traffic on the old transform will continue to be decrypted
     * until that transform is deallocated by calling {@link IpSecTransform#close()}. This overlap
     * allows rekey procedures where both transforms are valid until both endpoints are using the
     * new transform and all in-flight packets have been received.
     *
     * @param socket a socket file descriptor
     * @param transform a transport mode {@code IpSecTransform}
     * @throws IOException indicating that the transform could not be applied
     */
    public void applyTransportModeTransform(FileDescriptor socket, IpSecTransform transform)
            throws IOException {
        applyTransportModeTransform(new ParcelFileDescriptor(socket), transform);
    }

    /**
     * Apply an IPsec transform to a network.
     *
     * <p>This applies tunnel mode encapsulation to the given network. Once applied, all traffic to
     * and from that network's interface will be tunneled according to the parameters of the {@code
     * IpSecTransform}.
     *
     * <p>Applications should probably not use this API directly. Instead, they should use {@link
     * VpnService} to provide VPN capability in a more generic fashion.
     *
     * <h4>Rekey Procedure</h4> <p>When applying a new tranform to a network, the previous transform
     * will be removed. However, inbound traffic on the old transform will continue to be decrypted
     * until that transform is deallocated by calling {@link IpSecTransform#close()}. This overlap
     * allows rekey procedures where both transforms are valid until both endpoints are using the
     * new transform and all in-flight packets have been received.
     *
     * @param net the {@code Network} that will be tunneled
     * @param transform a tunnel mode {@code IpSecTransform}
     * @hide
     */
    public void applyTunnelModeTransform(Network net, IpSecTransform transform) {}

    /**
     * Remove an IPsec transform from a stream socket.
     *
     * <p>Once removed, traffic on the socket will not be encrypted. This operation will succeed
     * regardless of the state of the transform. Removing a transform from a socket allows the
     * socket to be reused for communication in the clear.
     *
     * <p>If an {@code IpSecTransform} object applied to this socket was deallocated by calling
     * {@link IpSecTransform#close()}, then communication on the socket will fail until this method
     * is called.
     *
     * @param socket a socket that previously had a transform applied to it
     * @param transform the IPsec Transform that was previously applied to the given socket
     * @throws IOException indicating that the transform could not be removed from the socket
     * @hide
     */
    public void removeTransportModeTransform(Socket socket, IpSecTransform transform)
            throws IOException {
        removeTransportModeTransform(ParcelFileDescriptor.fromSocket(socket), transform);
    }

    /**
     * Remove an IPsec transform from a datagram socket.
     *
     * <p>Once removed, traffic on the socket will not be encrypted. This operation will succeed
     * regardless of the state of the transform. Removing a transform from a socket allows the
     * socket to be reused for communication in the clear.
     *
     * <p>If an {@code IpSecTransform} object applied to this socket was deallocated by calling
     * {@link IpSecTransform#close()}, then communication on the socket will fail until this method
     * is called.
     *
     * @param socket a socket that previously had a transform applied to it
     * @param transform the IPsec Transform that was previously applied to the given socket
     * @throws IOException indicating that the transform could not be removed from the socket
     * @hide
     */
    public void removeTransportModeTransform(DatagramSocket socket, IpSecTransform transform)
            throws IOException {
        removeTransportModeTransform(ParcelFileDescriptor.fromDatagramSocket(socket), transform);
    }

    /**
     * Remove an IPsec transform from a socket.
     *
     * <p>Once removed, traffic on the socket will not be encrypted. This operation will succeed
     * regardless of the state of the transform. Removing a transform from a socket allows the
     * socket to be reused for communication in the clear.
     *
     * <p>If an {@code IpSecTransform} object applied to this socket was deallocated by calling
     * {@link IpSecTransform#close()}, then communication on the socket will fail until this method
     * is called.
     *
     * @param socket a socket that previously had a transform applied to it
     * @param transform the IPsec Transform that was previously applied to the given socket
     * @throws IOException indicating that the transform could not be removed from the socket
     */
    public void removeTransportModeTransform(FileDescriptor socket, IpSecTransform transform)
            throws IOException {
        removeTransportModeTransform(new ParcelFileDescriptor(socket), transform);
    }

    /* Call down to remove a transform */
    private void removeTransportModeTransform(ParcelFileDescriptor pfd, IpSecTransform transform) {
        try {
            mService.removeTransportModeTransform(pfd, transform.getResourceId());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove an IPsec transform from a network.
     *
     * <p>This must be called to clean up a {@link Network} if it experiences a change in default
     * route. Leaving a transform applied to a network after a default route change will result in
     * all traffic to that network being dropped.
     *
     * @param net a network that previously had a transform applied to it
     * @param transform the IPsec Transform that was previously applied to the given network
     * @hide
     */
    public void removeTunnelModeTransform(Network net, IpSecTransform transform) {}

    /**
     * This class provides access to a UDP encapsulation Socket.
     *
     * <p>{@code UdpEncapsulationSocket} wraps system-provided datagram sockets intended for IKEv2
     * signalling as well as UDP encapsulated IPsec traffic. Instances can be obtained by calling
     * {@link IpSecManager#openUdpEncapsulationSocket}. The provided socket cannot be re-bound or
     * closed by the caller. Instead, it must be closed by calling {@link #close()}.
     *
     * <p>Allowing the user to close or unbind a UDP encapsulation socket could impact the traffic
     * of the next user who binds to that port. To prevent this scenario, these sockets are held
     * open by the system so that they may only be closed by calling {@link
     * UdpEncapsulationSocket#close()} or when the user process exits.
     */
    public static final class UdpEncapsulationSocket implements AutoCloseable {
        private final FileDescriptor mFd;
        private final IIpSecService mService;
        private final CloseGuard mCloseGuard = CloseGuard.get();

        private UdpEncapsulationSocket(@NonNull IIpSecService service, int port)
                throws ResourceUnavailableException {
            mService = service;
            mCloseGuard.open("constructor");
            // TODO: go down to the kernel and get a socket on the specified
            mFd = new FileDescriptor();
        }

        private UdpEncapsulationSocket(IIpSecService service) throws ResourceUnavailableException {
            mService = service;
            mCloseGuard.open("constructor");
            // TODO: go get a random socket on a random port
            mFd = new FileDescriptor();
        }

        /** Get the wrapped socket. */
        public FileDescriptor getSocket() {
            return mFd;
        }

        /** Get the bound port of the wrapped socket. */
        public int getPort() {
            return 0; // TODO get the port number from the Socket;
        }

        @Override
        /**
         * Close this socket.
         *
         * <p>This closes the wrapped socket. Open encapsulation sockets count against a user's
         * resource limits, and forgetting to close them eventually will result in {@link
         * ResourceUnavailableException} being thrown.
         *
         * TODO: can a user keep the socket open when calling this? (e.g. by duping?)
         */
        public void close() throws IOException {
            // TODO: Go close the socket
            mCloseGuard.close();
        }

        @Override
        protected void finalize() throws Throwable {
            if (mCloseGuard != null) {
                mCloseGuard.warnIfOpen();
            }

            close();
        }
    };

    /**
     * Open a socket for UDP encapsulation and bind to the given port.
     *
     * <p>This opens a datagram socket bound to the given port. The provided socket cannot be
     * re-bound or closed by the caller. Instead, it must be closed by calling {@link
     * UdpEncapsulationSocket#close()}. The wrapped socket is intended for IKEv2 signalling as well
     * as UDP encapsulated IPsec traffic.
     *
     * <p>Allowing the user to close or unbind a UDP encapsulation socket could impact the traffic
     * of the next user who binds to that port. To prevent this scenario, these sockets are held
     * open by the system so that they may only be closed by calling {@link
     * UdpEncapsulationSocket#close()} or when the user process exits.
     *
     * @param port a local UDP port
     * @return a socket that is bound to the given port
     * @throws IOException indicating that the socket could not be opened or bound
     * @throws ResourceUnavailableException indicating that too many encapsulation sockets are open
     */
    public UdpEncapsulationSocket openUdpEncapsulationSocket(int port)
            throws IOException, ResourceUnavailableException {
        // Temporary code
        return new UdpEncapsulationSocket(mService, port);
    }

    /**
     * Open a socket for UDP encapsulation.
     *
     * <p>This opens a datagram socket bound to a port selected by the system. The provided socket
     * cannot be re-bound or closed by the caller. Instead, it must be closed by calling {@link
     * UdpEncapsulationSocket#close()}. The wrapped socket is intended for IKEv2 signalling as well
     * as UDP encapsulated IPsec traffic.
     *
     * <p>The local port of the returned socket can be obtained by calling {@link
     * UdpEncapsulationSocket#getPort()}.
     *
     * <p>Allowing the user to close or unbind a UDP encapsulation socket could impact the traffic
     * of the next user who binds to that port. To prevent this scenario, these sockets are held
     * open by the system so that they may only be closed by calling {@link
     * UdpEncapsulationSocket#close()} or when the user process exits.
     *
     * @return a socket that is bound to a local port
     * @throws IOException indicating that the socket could not be opened or bound
     * @throws ResourceUnavailableException indicating that too many encapsulation sockets are open
     */
    public UdpEncapsulationSocket openUdpEncapsulationSocket()
            throws IOException, ResourceUnavailableException {
        // Temporary code
        return new UdpEncapsulationSocket(mService);
    }

    /**
     * Construct an instance of IpSecManager within an application context.
     *
     * @param context the application context for this manager
     * @hide
     */
    public IpSecManager(IIpSecService service) {
        mService = checkNotNull(service, "missing service");
    }
}
