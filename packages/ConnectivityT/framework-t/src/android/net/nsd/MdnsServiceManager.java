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
import android.annotation.Nullable;
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
     * Set socket connection to mdnsresponder.
     */
    public void mdnsStart() {
        try {
            mMdns.mdnsStart();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Start mdns failed. e=" + e);
        }
    }

    /**
     * Stop socket connection from mdnsresponder.
     */
    public void mdnsStop() {
        try {
            mMdns.mdnsStop();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop mdns failed. e=" + e);
        }
    }

    /**
     * Register the service.
     *
     * @param id The operation ID.
     * @param serviceName The service name to be registered.
     * @param registrationType The service type to be registered.
     * @param port The port on which the service accepts connections.
     * @param txtRecord The primary txt record.
     * @param registerInterface The interface on which to register the service.
     * @return {@code true} if registration is successful, else {@code false}.
     */
    public boolean registerService(int id, @NonNull String serviceName,
            @NonNull String registrationType, int port, @NonNull byte[] txtRecord,
            @Nullable String registerInterface) {
        final RegistrationInfo info = new RegistrationInfo(id, serviceName, registrationType, port,
                txtRecord, registerInterface);
        try {
            mMdns.registerService(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Register service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop registering the service.
     *
     * @param id The registration operation id to be stopped.
     * @return {@code true} if registration is stopped successfully, else {@code false}.
     */
    public boolean registerServiceStop(int id) {
        try {
            mMdns.registerServiceStop(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Unregister service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start discovering service.
     *
     * @param id The operation ID.
     * @param registrationType The service type to be discovered.
     * @param discoverInterface The interface on which to discover for services.
     * @return {@code true} if discovery is started successfully, else {@code false}.
     */
    public boolean discover(int id, @NonNull String registrationType,
            @Nullable String discoverInterface) {
        final DiscoveryInfo info = new DiscoveryInfo(id, registrationType, discoverInterface);
        try {
            mMdns.discover(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Discover service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop discovering service.
     *
     * @param id The discovery operation id to be stopped.
     * @return {@code true} if discovery is stopped successfully, else {@code false}.
     */
    public boolean discoverStop(int id) {
        try {
            mMdns.discoverStop(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop discovering failed. e=" + e);
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
     * @param resolveInterface The interface on which to resolve the service.
     * @return {@code true} if resolution is started successfully, else {@code false}.
     */
    public boolean resolve(int id, @NonNull String serviceName, @NonNull String registrationType,
            @NonNull String domain, @NonNull String resolveInterface) {
        final ResolutionInfo info = new ResolutionInfo(id, serviceName, registrationType,
                domain, resolveInterface);
        try {
            mMdns.resolve(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Resolve service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop resolving the service.
     *
     * @param id The resolution operation id to be stopped.
     * @return {@code true} if resolution is stopped successfully, else {@code false}.
     */
    public boolean resolveStop(int id) {
        try {
            mMdns.resolveStop(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop resolving failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start getting the target service address.
     *
     * @param id The operation ID.
     * @param hostName The fully qualified domain name of the host to be queried for.
     * @param interfaceIdx The interface on which to issue the query.
     * @return {@code true} if registration is successful, else {@code false}.
     */
    public boolean getServiceAddress(int id, @NonNull String hostName, int interfaceIdx) {
        final GetAddressInfo info = new GetAddressInfo(id, hostName, interfaceIdx);
        try {
            mMdns.getServiceAddress(info);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Get service address failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop getting the service address.
     *
     * @param id the getting address operation id to be stopped.
     * @return {@code true} if getting address is stopped successfully, else {@code false}.
     */
    public boolean getServiceAddressStop(int id) {
        try {
            mMdns.getServiceAddressStop(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop getting service address failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Register a event listener.
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
     * Unregister a event listener.
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
