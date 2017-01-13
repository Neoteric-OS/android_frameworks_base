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

import android.os.Binder;
import android.content.Context;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * This class contains methods for managing IPSec sessions.
 *
 * @hide
 */
public class IpSecManager {
    private static final String TAG = IpSecManager.class.getSimpleName();

    /** Indicates that the socket on which IpSec is requested was unbound */
    public class SocketNotBoundException extends Exception {}

    /** Indicates that the combination of source IP, destination IP, and SPI was non-unique */
    public class TransformCollisionException extends Exception {}

    /**
     * Indicates that the requester does not have permission to perform IPSec on the given socket
     */
    public class InsufficientPermissionException extends Exception {}

    /**
     * Request creation of a security association and security policy to perform transport-mode
     * IPsec encapsulation of the traffic on a given socket bound for a specific destination. The
     * socket must be bound to a port prior to requesting an IPsec transform.
     *
     * @param transform a transport mode IpSecTransform object
     * @hide
     */
    public void addTransportModeTransform(IpSecTransform transform)
            throws SocketNotBoundException, TransformCollisionException,
                    InsufficientPermissionException {
        try {
            int transformId = mService.addTransportModeTransform(transform.getConfig(), new Binder());
            transform.setTransformId(transformId);
        } catch (RemoteException e) {
            Log.e(TAG, "Binder call failed");
        }
    }

    /**
     * Request creation of a security association and security policy to perform transport-mode
     * IPsec encapsulation of the traffic on a given socket bound for a specific destination. The
     * socket must be bound to a port prior to requesting an IPsec transform. This method is only
     * applicable to outbound IPv4 traffic.
     *
     * @param transform a transport mode IpSecTransform object
     * @param intervalSeconds the maximum number of seconds between requested NATT Keepalive packet
     *     transmisions.
     * @hide
     */
    public void addTransportModeTransform(IpSecTransform transform, int nattKeepalivePeriod)
            throws SocketNotBoundException, TransformCollisionException,
                    InsufficientPermissionException {}

    /**
     * Remove a transform that was created with the provided IpSecTransform argument
     *
     * @param transform a transport mode IpSecTransform object
     * @hide
     */
    public void removeTransportTransform(IpSecTransform transform)
            throws SocketNotBoundException, InsufficientPermissionException {
        int transformId;
        synchronized (transform) {
            transformId = transform.getTransformId();
            transform.setTransformId(IpSecTransform.INVALID_TRANSFORM_ID);
        }

        if(transformId == IpSecTransform.INVALID_TRANSFORM_ID) {
            return;
        }
        try {
            mService.removeTransform(transform.getTransformId());
        } catch (RemoteException e) {
            synchronized(transform) {
                transform.setTransformId(transformId);
            }
        }
    }

    private final Context mContext;
    private final INetworkManagementService mService;

    /** {@hide} */
    public IpSecManager(Context context, INetworkManagementService service) {
        mContext = checkNotNull(context, "missing context");
        mService = checkNotNull(service, "missing NetworkManagementService");
    }

    /** {@hide} */
    public static IpSecManager from(Context context) {
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);

        return new IpSecManager(
                context, (b != null) ? INetworkManagementService.Stub.asInterface(b) : null);
    }
}
