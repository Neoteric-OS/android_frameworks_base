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

import android.annotation.SystemApi;
import android.content.Context;
import android.os.Binder;
import android.os.INetworkManagementService;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.util.AndroidException;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

/**
 * This class contains methods for managing IPSec sessions, which will perform kernel-space
 * encryption and decryption of socket or Network traffic.
 *
 * <p>To establish an IPSec connection for a socket, users must create a transport mode {@link
 * android.net.IpSecTransform}. After creating one of these objects, use the methods here to {@link
 * android.net.IpSecManager#activateTransform(IpSecTransform)
 * IpSecManager#activateTransform(IpSecTransform)} which readies the transform for use by Sockets or
 * DatagramSockets to which the IpSecTransform can be applied by calling {@link
 * android.net.IpSecManager#applyTransportModeTransform(Socket, IpSecTransform)
 * IpSecManager#applyTransportModeTransform(Socket, IpSecTransform)} or {@link
 * android.net.IpSecManager#applyTransportModeTransform(DatagramSocket, IpSecTransform)
 * IpSecManager#applyTransportModeTransform(DatagramSocket, IpSecTransform)}. Users of this
 * functionality should take care to clean up after completion by calling {@link
 * android.net.IpSecManager#removeTransportModeTransform(DatagramSocket, IpSecTransform)
 * IpSecManager#removeTransportModeTransform(DatagramSocket, IpSecTransform)} and finally {@link
 * android.net.IpSecManager#deactivateTransform(IpSecTransform)
 * IpSecManager#deactivateTransform(IpSecTransform)} to free allocated system resources.
 *
 * <p>An IpSecManager may be obtained by calling {@link
 * android.content.Context#getSystemService(String) Context#getSystemService(String)} with {@link
 * android.content.Context#IPSEC_SERVICE Context#IPSEC_SERVICE}
 */
public final class IpSecManager {
    private static final String TAG = "IpSecManager";

    /**
     * Indicates that the combination of remote Inet Address and SPI was non-unique for a given
     * request. If encountered, selection of a new SPI is required before a transform may be
     * created. Note, this should happen very rarely if the SPI is chosen to be sufficiently random.
     */
    public final class TransformCollisionException extends AndroidException {
        private final int mSpi;

        /**
         * Construct an exception indicating that a transform with the given SPI is already present,
         * and that creating the requested transform would thus collide with an existing flow.
         *
         * @param msg Description indicating the colliding SPI
         * @param spi the SPI that could not be used due to a collision
         */
        TransformCollisionException(String msg, int spi) {
            super(msg);
            mSpi = spi;
        }

        public int getSpi() {
            return mSpi;
        }
    }

    private final Context mContext;
    private final INetworkManagementService mService;

    /**
     * Reserve an SPI for use in the outbound direction.
     *
     * <p>If successful, this SPI is guaranteed available until released by a call to {@link
     * #releaseOutboundSpi(InetAddress, int)}.
     *
     * @param remoteAddr SPIs must be unique for each combination of SPI and remote address. thus,
     *     the remoteAddress to which the SPI will communicate must be supplied.
     * @param requestedSpi the requested SPI, or '0' to allocate a random SPI.
     * @return the reserved SPI or 0 if allocation fails.
     */
    public int reserveOutboundSpi(InetAddress remoteAddr, int requestedSpi) {
        return 0;
    }

    /**
     * Release an SPI that was previously reserved.
     *
     * <p>Release an SPI for use by other users in the system. This will fail if the SPI is
     * currently in use by an IpSecTransform.
     *
     * @param remoteAddr SPIs must be unique for each combination of SPI and remote address. Thus,
     *     the remoteAddress to which the SPI will communicate must be supplied.
     * @param spi the previously reserved SPI to be freed.
     * @throws IllegalArgumentException if the SPI was not reserved or cannot be freed
     */
    public void releaseOutboundSpi(InetAddress remoteAddr, int spi) throws IllegalArgumentException {}

    /**
     * Apply an active Transport Mode IPSec Transform to a stream socket to perform IPsec
     * encapsulation of the traffic flowing between the socket and the remote InetAddress of that
     * transform. For security reasons, attempts to send traffic to any IP address other than the
     * address associated with that transform will throw an IOException. In addition, if the
     * IpSecTransform is later deactivated, the socket will throw an IOException on any calls to
     * send() or receive() until the transform is removed from the socket by calling {@link
     * #removeTransportModeTransform(Socket, IpSecTransform)};
     *
     * @param socket a stream socket
     * @param transform an {@link IpSecTransform}, which must be an active Transport Mode transform.
     */
    public void applyTransportModeTransform(Socket socket, IpSecTransform transform)
            throws IOException {
        applyTransportModeTransform(ParcelFileDescriptor.fromSocket(socket), transform);
    }

    /**
     * Apply an active Transport Mode IPSec Transform to a datagram socket to perform IPsec
     * encapsulation of the traffic flowing between the socket and the remote InetAddress of that
     * transform. For security reasons, attempts to send traffic to any IP address other than the
     * address associated with that transform will throw an IOException. In addition, if the
     * IpSecTransform is later deactivated, the socket will throw an IOException on any calls to
     * send() or receive() until the transform is removed from the socket by calling {@link
     * #removeTransportModeTransform(DatagramSocket, IpSecTransform)};
     *
     * @param socket a datagram socket
     * @param transform an {@link IpSecTransform}, which must be an active Transport Mode transform.
     */
    public void applyTransportModeTransform(DatagramSocket socket, IpSecTransform transform)
            throws TransformCollisionException, IOException {
        applyTransportModeTransform(ParcelFileDescriptor.fromDatagramSocket(socket), transform);
    }

    /* Call down to activate a transform */
    private void applyTransportModeTransform(ParcelFileDescriptor pfd, IpSecTransform transform) {}

    /**
     * Apply an active Tunnel Mode IPSec Transform to a network, which will tunnel all traffic to
     * and from that network's interface with IPSec (applies an outer IP header and IPSec Header to
     * all traffic, and expects an additional IP header and IPSec Header on all inbound traffic).
     * Applications should probably not use this API directly. Instead, they should use {@link
     * VpnService} to provide VPN capability in a more generic fashion.
     *
     * @param net a {@link Network} that will be tunneled via IP Sec.
     * @param transform an {@link IpSecTransform}, which must be an active Tunnel Mode transform.
     * @hide
     */
    @SystemApi
    public void applyTunnelModeTransform(Network net, IpSecTransform transform) {}

    /**
     * Remove a transform from a given stream socket. Once removed, traffic on the socket will not
     * be encypted. This allows sockets that have been used for IPSec to be reclaimed for
     * communication in the clear in the event socket reuse is desired. This operation will succeed
     * regardless of the underlying state of a transform. If a transform is removed, communication
     * on all sockets to which that transform was applied will fail until this method is called.
     *
     * @param socket a socket that previously had a tranform applied to it.
     * @param transform the IPSec Transform that was previously applied to the given socket
     */
    public void removeTransportModeTransform(Socket socket, IpSecTransform transform) {}

    /**
     * Remove a transform from a given datagram socket. Once removed, traffic on the socket will not
     * be encypted. This allows sockets that have been used for IPSec to be reclaimed for
     * communication in the clear in the event socket reuse is desired. This operation will succeed
     * regardless of the underlying state of a transform. If a transform is removed, communication
     * on all sockets to which that transform was applied will fail until this method is called.
     *
     * @param socket a socket that previously had a tranform applied to it.
     * @param transform the IPSec Transform that was previously applied to the given socket
     */
    public void removeTransportModeTransform(DatagramSocket socket, IpSecTransform transform) {}

    /**
     * Remove a Tunnel Mode IPSec Transform from a {@link Network}. This must be used as part of
     * cleanup if a tunneled Network experiences a change in default route. The Network will drop
     * all traffic that cannot be routed to the Tunnel's outbound interface. If that interface is
     * lost, all traffic will drop.
     *
     * @param net a network that currently has transform applied to it.
     * @param transform a Tunnel Mode IPSec Transform that has been previously applied to the given
     *     network
     * @hide
     */
    @SystemApi
    public void removeTunnelModeTransform(Network net, IpSecTransform transform) {}

    /**
     * Activate a transform, which will reserve and allocate all necessary resources for this
     * transform to be applied to Sockets or Networks. This call performs a variety of operations
     * including opening and reserving sockets for UDP encapsulation, reserving the SPIs that are
     * needed for unambiguous IPSec communication, creating interfaces for tunneling traffic,
     * starting hardware offload to ensure that UDP encapsulated traffic passes through reverse NAT
     * via NAT-T, etc. Upon return from this call, the transform will be considered active. If too
     * many IpSecTransform objects are active for a given user and the system has insufficient
     * resources to handle the request, this operation will fail with an IOException. Because
     * Tranform objects have substantial impact on the system, stale Transform objects should be
     * cleaned up by calling {@link #deactivateTransform(IpSecTransform)} when they are no longer
     * needed.
     *
     * @param transform a Transport Mode IpSecTransform object
     * @throws IOException in the event that resources in the kernel cannot be allocated; in the
     *     case of UDP Encapsulation, this likely means that requested ports could not be reserved.
     * @throws TransformCollisionException if the SPI collides with an existing transform
     *     (unlikely).
     */
    public void activateTransform(IpSecTransform transform)
            throws IOException, TransformCollisionException {
        int transformId;
        synchronized (transform) {
            try {
                transformId = mService.addTransform(transform.getConfig(), new Binder());
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }

            if (transformId < 0) {
                throw new ErrnoException("addTransform", -transformId).rethrowAsIOException();
            }

            transform.startKeepalive(mContext); // Will silently fail if not required
            transform.setTransformId(transformId);
            Log.d(TAG, "Added Transform with Id " + transformId);
        }
    }

    /**
     * Deactivate an IpSecTransform and free all resources for that transform that are managed by
     * the system.
     *
     * <p>This will close all system-managed ports, release the reservation of the SPIs used in the
     * transform, and stop all ongoing processes related to managing the transform. Deactivating a
     * transform while it is still applied to any socket or Network will result in sockets refusing
     * to send or receive data. This method will silently succeed if the specified transform has
     * already been removed.
     *
     * @param transform a transport mode IpSecTransform object
     */
    public void deactivateTransform(IpSecTransform transform) {
        final int transformId;
        synchronized (transform) {
            transformId = transform.getTransformId();
            Log.d(TAG, "Removing Transform with Id " + transformId);

            // Always safe to attempt cleanup
            if (transformId == IpSecTransform.INVALID_TRANSFORM_ID) {
                return;
            }
            try {
                transform.stopKeepalive();
                mService.removeTransform(transformId);
            } catch (RemoteException e) {
                transform.setTransformId(transformId);
                throw e.rethrowFromSystemServer();
            } finally {
                transform.setTransformId(IpSecTransform.INVALID_TRANSFORM_ID);
            }
        }
    }

    /**
     * Open a socket that is bound to the given port.
     *
     * <p>By binding in this manner and holding the FileDescriptor, the socket cannot be un-bound by
     * the caller. This provides safe access to a socket on a port that can later be co-opted as a
     * UDP Encap port. Otherwise, there is no safe way to ensure that a socket is both accessible
     * and usable for Encapsulation (that causes an additional layer of headers to be
     * applied/removed).
     *
     * <p>This socket reservation works in conjunction with IpSecTransforms, which may re-use the
     * socket port. Explicitly opening this port is only necessary if communication is desired on
     * that port; however, if this port is not opened (and thereby reserved) prior to establishing
     * an IpSecTransform, creation of that transform may fail if the port is not available.
     *
     * @param port a local UDP port to be reserved for UDP Encapsulation.
     * @throws SocketException if the port is in use or the system otherwise refuses to reserve an
     *     additional socket.
     * @return a UDP port that is bound (irrevocably) to the requested port
     */
    public FileDescriptor openUdpEncapsulationSocket(int port) throws SocketException {
        // Temporary code
        return new FileDescriptor();
    }

    /**
     * Release a socket that was provided via {@link #openUdpEncapsulationSocket(int)}
     *
     * <p>This method releases a socket, reducing a user's allocated sockets in the system. This
     * must be done as part of cleanup following use of a socket. Failure to do so will cause the
     * socket to count against a total allocation limit for IpSec and eventually fail due to
     * resource limits.
     *
     * @param fd a file descriptor previously returned as a UDP Encap socket.
     */
    public void closeUdpEncapsulationSocket(FileDescriptor fd) {}

    /**
     * Retrieve an instance of an IpSecManager within you application context
     *
     * @param context the application context for this manager
     * @hide
     */
    public IpSecManager(Context context, INetworkManagementService service) {
        mContext = checkNotNull(context, "missing context");
        mService = checkNotNull(service, "missing service");
    }
}
