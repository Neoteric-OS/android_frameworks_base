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
package com.android.server.net;

import android.net.INetd;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.ErrnoException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.structs.U32Struct;

import java.net.NetworkInterface;
import java.net.SocketException;
/**
 * Monitor interface added (without removed) and right interface name and its index to bpf map.
 */
public class BpfInterfaceMapUpdater {
    private static final String TAG = BpfInterfaceMapUpdater.class.getSimpleName();
    // This is current path but may be changed soon.
    private static final String IFACE_INDEX_NAME_MAP_PATH =
            "/sys/fs/bpf/map_netd_iface_index_name_map";
    private final BpfMap<U32Struct, InterfaceMapValue> mBpfMap;
    private final INetd mNetd;
    private final Handler mHandler;

    public BpfInterfaceMapUpdater(Handler handler, INetd netd) {
        this(handler, netd, getInterfaceMap());
    }

    @VisibleForTesting
    public BpfInterfaceMapUpdater(Handler handler, INetd netd,
            BpfMap<U32Struct, InterfaceMapValue> bpfMap) {
        mBpfMap = bpfMap;
        mNetd = netd;
        mHandler = handler;
    }

    private static BpfMap<U32Struct, InterfaceMapValue> getInterfaceMap() {
        try {
            return new BpfMap<>(IFACE_INDEX_NAME_MAP_PATH, BpfMap.BPF_F_RDWR,
                U32Struct.class, InterfaceMapValue.class);
        } catch (ErrnoException e) {
            Log.e(TAG, "Cannot create interface map: " + e);
            return null;
        }
    }

    /**
     * Start listening interface update event.
     * Query current interface names before listening.
     */
    public boolean start() {
        if (mBpfMap == null) return false;

        try {
            mNetd.registerUnsolicitedEventListener(new InterfaceChangeObserver());
        } catch (RemoteException e) {
            Log.d(TAG, "Unable to register netd UnsolicitedEventListener, " + e);
            return false;
        }

        final String[] ifaces;
        try {
            ifaces = mNetd.interfaceGetList();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Unable to query interface names by netd, " + e);
            return false;
        }

        for (String ifaceName : ifaces) {
            addInterface(ifaceName);
        }

        return true;
    }

    private static NetworkInterface getNetworkInterface(String ifaceName) {
        try {
            return NetworkInterface.getByName(ifaceName);
        } catch (NullPointerException | SocketException e) {
            return null;
        }
    }

    private void addInterface(String ifaceName) {
        final NetworkInterface netif = getNetworkInterface(ifaceName);
        if (netif == null) {
            Log.e(TAG, "Unable to find NetworkInterface for " + ifaceName);
            return;
        }

        try {
            mBpfMap.updateEntry(new U32Struct(netif.getIndex()), new InterfaceMapValue(ifaceName));
        } catch (ErrnoException e) {
            Log.e(TAG, "Unable to update entry for " + ifaceName + ", " + e);
        }
    }

    private class InterfaceChangeObserver extends BaseNetdUnsolicitedEventListener {
        @Override
        public void onInterfaceAdded(String ifName) {
            mHandler.post(() -> addInterface(ifName));
        }
    }
}
