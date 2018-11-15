/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.annotation.TestApi;
import android.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import com.android.internal.util.Preconditions;

/**
 * Class that allows creation and management of per-app, test-only networks
 *
 * @hide
 */
@TestApi
public class TestNetworkManager {
    private static final String TAG = "TestNetworkManager";

    @UnsupportedAppUsage private final ITestNetworkManager mService;
    private final Context mContext;
    private final String mOpPkg;

    /** @hide */
    public TestNetworkManager(Context context, ITestNetworkManager service) {
        mContext = Preconditions.checkNotNull(context, "missing Context");
        mService = Preconditions.checkNotNull(service, "missing ITestNetworkManager");
        mOpPkg = mContext.getOpPackageName();
    }

    /**
     * Teardown the capability-limited, testing-only network for a given interface
     *
     * @param network The test network that should be torn down
     * @hide
     */
    @TestApi
    public void teardownTestNetwork(Network network) {
        try {
            mService.teardownTestNetwork(network.netId, mOpPkg);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Build a capability-limited, testing-only network for a given interface
     *
     * @param intfName the name of the interface to be used for the Network LinkProperties.
     * @param binder A binder object guarding the lifecycle of this test network.
     * @hide
     */
    @TestApi
    public void buildTestNetwork(String intfName, IBinder binder) {
        try {
            mService.buildTestNetwork(intfName, binder, mOpPkg);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Build a tun interface for testing purposes
     *
     * @param intfName the name to assign to the created TUN
     * @param linkAddrs an array of LinkAddresses to assign to the TUN interface
     * @return A ParcelFileDescriptor of the underlying TUN interface. Close this to teardown the
     *     TUN interface.
     * @hide
     */
    @TestApi
    public ParcelFileDescriptor buildTun(String intfName, LinkAddress[] linkAddrs) {
        try {
            return mService.buildTun(intfName, linkAddrs, mOpPkg);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
