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
import android.net.mdns.aidl.IMdns;
import android.net.mdns.aidl.IMdnsEventListener;
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
     * Register the service name.
     */
    public boolean registerService(int id, @NonNull String name, @NonNull String type, int port,
            @NonNull String record) {
        try {
            mMdns.registerService(id, name, type, port, record);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Register service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Unregister the service name.
     */
    public boolean unregisterService(int id) {
        try {
            mMdns.unregisterService(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Unregister service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Start discovering other services.
     */
    public boolean discover(int id, @NonNull String type) {
        try {
            mMdns.discover(id, type);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Discover service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop discovering other services.
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
     */
    public boolean resolve(int id, @NonNull String name, @NonNull String type,
            @NonNull String domain, @NonNull String resolveInterface) {
        try {
            mMdns.resolve(id, name, type, domain, resolveInterface);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Resolve service failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop resolving the service.
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
     * Start getting the target service address info.
     */
    public boolean getServiceAddressInfo(int id, @NonNull String hostName) {
        try {
            mMdns.getServiceAddressInfo(id, hostName);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Get service address failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Stop getting the service address info.
     */
    public boolean getServiceAddressInfoStop(int id) {
        try {
            mMdns.getServiceAddressInfoStop(id);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Stop getting service address failed. e=" + e);
            return false;
        }
        return true;
    }

    /**
     * Register a event listener.
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
     */
    public void unregisterEventListener(@NonNull IMdnsEventListener listener) {
        try {
            mMdns.unregisterEventListener(listener);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Unregister listener failed. e=" + e);
        }
    }
}
