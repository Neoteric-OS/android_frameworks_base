/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.IVpnManager;
import android.os.HandlerThread;
import android.security.KeyStore;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.server.connectivity.Vpn;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Service that tracks and manages VPNs, and backs the VpnService and VpnManager APIs.
 * @hide
 */
public class VpnManagerService extends IVpnManager.Stub {
    private static final String TAG = VpnManagerService.class.getSimpleName();

    @VisibleForTesting
    protected final HandlerThread mHandlerThread;

    private final Context mContext;

    private final Dependencies mDependencies;

    private final KeyStore mKeyStore;

    @VisibleForTesting
    @GuardedBy("mVpns")
    protected final SparseArray<Vpn> mVpns = new SparseArray<>();

    /**
     * Dependencies of VpnManager, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** Creates a HandlerThread to be used by this class. */
        public HandlerThread makeHandlerThread() {
            return new HandlerThread("VpnManagerService");
        }

        /** Returns the KeyStore instance to be used by this class. */
        public KeyStore getKeyStore() {
            return KeyStore.getInstance();
        }
    }

    public VpnManagerService(Context context) {
        mContext = context;
        mDependencies = new Dependencies();
        mHandlerThread = mDependencies.makeHandlerThread();
        mKeyStore = mDependencies.getKeyStore();
    }

    /** Creates a new VpnManagerService */
    public static final VpnManagerService create(Context context) {
        return new VpnManagerService(context);
    }

    /** Informs the service that the system is ready. */
    public void systemReady() {
    }

    @Override
    /** Dumps service state. */
    protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter pw,
            @Nullable String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;
    }
}
