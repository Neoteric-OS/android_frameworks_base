/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.net.nsd;

import android.annotation.NonNull;
import android.net.mdns.aidl.DiscoveryInfo;
import android.net.mdns.aidl.GetAddressInfo;
import android.net.mdns.aidl.IMdns;
import android.net.mdns.aidl.IMdnsEventListener;
import android.net.mdns.aidl.RegistrationInfo;
import android.net.mdns.aidl.ResolutionInfo;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

/**
 * A manager class for mdns service.
 *
 * @hide
 */
public class MdnsServiceManager {
    private static final String TAG = MdnsServiceManager.class.getSimpleName();
    private final IMdns mMdns;

    /** Service name for this. */
    public static final String MDNS_SERVICE = "mdns";

    public MdnsServiceManager(IMdns mdns) {
        mMdns = mdns;
    }

    /**
     * Start the MDNSResponder daemon.
     */
    public void startDaemon() {
        try {
            mMdns.startDaemon();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Start mdns failed. e=" + e);
        }
    }

    /**
     * Stop the MDNSResponder daemon.
     */
    public void stopDaemon() {
        try {
            mMdns.stopDaemon();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop mdns failed. e=" + e);
        }
    }

    /**
     * Start registering a service.
     *
     * @param id The operation ID.
     * @param serviceName The service name to be registered.
     * @param registrationType The service type to be registered.
     * @param port The port on which the service accepts connections.
     * @param txtRecord The primary txt record.
     * @param interfaceIdx The interface on which to register the service.
     * @return {@code true} if registration is successful, else {@code false}.
     */
    public boolean registerService(int id, @NonNull String serviceName,
            @NonNull String registrationType, int port, @NonNull byte[] txtRecord,
            int interfaceIdx) {
        final RegistrationInfo info = new RegistrationInfo(id, -1 /* result */, serviceName,
                registrationType, port, txtRecord, interfaceIdx);
        try {
            mMdns.registerService(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Register service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start discovering services.
     *
     * @param id The operation ID.
     * @param registrationType The service type to be discovered.
     * @param interfaceIdx The interface on which to discover for services.
     * @return {@code true} if discovery is started successfully, else {@code false}.
     */
    public boolean discover(int id, @NonNull String registrationType,
            int interfaceIdx) {
        final DiscoveryInfo info = new DiscoveryInfo(id, -1 /* result */, "" /* serviceName */,
                registrationType, "" /* domainName */, interfaceIdx, 0 /* netId */);
        try {
            mMdns.discover(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Discover service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start resolving the target service.
     *
     * @param id The operation ID.
     * @param serviceName The service name to be resolved.
     * @param registrationType The service type to be resolved.
     * @param domain The service domain to be resolved.
     * @param interfaceIdx The interface on which to resolve the service.
     * @return {@code true} if resolution is started successfully, else {@code false}.
     */
    public boolean resolve(int id, @NonNull String serviceName, @NonNull String registrationType,
            @NonNull String domain, int interfaceIdx) {
        final ResolutionInfo info = new ResolutionInfo(id, -1 /* result */, serviceName,
                registrationType, domain, "" /* serviceFullName */, "" /* hostname */, 0 /* port */,
                new byte[0] /* txtRecord */, interfaceIdx);
        try {
            mMdns.resolve(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Resolve service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start getting the target service address.
     *
     * @param id The operation ID.
     * @param hostname The fully qualified domain name of the host to be queried for.
     * @param interfaceIdx The interface on which to issue the query.
     * @return {@code true} if getting address is started successful, else {@code false}.
     */
    public boolean getServiceAddress(int id, @NonNull String hostname, int interfaceIdx) {
        final GetAddressInfo info = new GetAddressInfo(id, -1 /* result */, hostname,
                "" /* address */, interfaceIdx, 0 /* netId */);
        try {
            mMdns.getServiceAddress(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Get service address failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop a operation which's requested before.
     *
     * @param id the operation id to be stopped.
     * @return {@code true} if operation is stopped successfully, else {@code false}.
     */
    public boolean stopOperation(int id) {
        try {
            mMdns.stopOperation(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop operation failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Register an event listener.
     * Note: The listener can't be null and same listener can only be registered once.
     *
     * @param listener The listener to be registered.
     */
    public void registerEventListener(@NonNull IMdnsEventListener listener) {
        try {
            mMdns.registerEventListener(listener);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Register listener failed. e=" + e);
        }
    }

    /**
     * Unregister an event listener.
     * Note: The listener can't be null. No error to unregister a listener that was not registered.
     *
     * @param listener The listener to be unregistered.
     */
    public void unregisterEventListener(@NonNull IMdnsEventListener listener) {
        try {
            mMdns.unregisterEventListener(listener);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Unregister listener failed. e=" + e);
        }
    }
}
