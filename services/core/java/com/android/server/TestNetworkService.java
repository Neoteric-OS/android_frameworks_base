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

package com.android.server;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.net.INetd;
import android.net.ITestNetworkManager;
import android.net.LinkAddress;
import android.net.util.NetdService;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

/** @hide */
class TestNetworkService extends ITestNetworkManager.Stub {
    private static final String TAG = TestNetworkService.class.getSimpleName();
    private static final String TEST_NETWORK_TYPE = "TEST_NETWORK";

    private final Context mContext;
    private INetworkManagementService mNMS;
    private INetd mNetd;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    @VisibleForTesting
    protected TestNetworkService(Context context, INetworkManagementService netManager) {
        Log.d(TAG, "TestNetworkService starting up");

        mHandlerThread = new HandlerThread("TestNetworkServiceThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mContext = checkNotNull(context, "missing Context");
        mNMS = checkNotNull(netManager, "missing INetworkManagementService");
        mNetd = NetdService.getInstance();
    }

    /**
     * Build a TUN interface with the given interface name and link addresses
     *
     * <p>This method will return the FileDescriptor to the TUN interface. Close it to teardown the
     * TUN interface.
     */
    @Override
    public synchronized ParcelFileDescriptor buildTun(
            String intfName, LinkAddress[] linkAddrs, String callingPackage) {
        return null;
    }

    /**
     * Build a Network with extremely limited privileges, guarded by the OP_MANAGE_TEST_NETWORKS.
     *
     * <p>This method provides a Network that is useful only for testing.
     */
    @Override
    public synchronized void buildTestNetwork(
            String intfName, IBinder binder, String callingPackage) {}

    /** Teardown a test network */
    @Override
    public synchronized void teardownTestNetwork(int netId, String callingPackage) {}
}
