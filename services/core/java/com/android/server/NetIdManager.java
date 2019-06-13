/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;

/**
 * Class used to reserve and release net IDs.
 *
 * <p>Instances of this class is thread-safe.
 */
public class NetIdManager {
    // sequence number for Networks; keep in sync with system/netd/NetworkController.cpp
    public static final int MIN_NET_ID = 100; // some reserved marks
    public static final int MAX_NET_ID = 65535 - 0x0400; // Top 1024 bits reserved by IpSecService

    @GuardedBy("mNetIdInUse")
    private final SparseBooleanArray mNetIdInUse = new SparseBooleanArray();

    @GuardedBy("mNetIdInUse")
    private int mNextNetId = MIN_NET_ID;

    /**
     * Get the next netId that follows the provided netId.
     */
    private static int getNextNetId(int netId) {
        return netId < MAX_NET_ID ? netId + 1 : MIN_NET_ID;
    }

    /**
     * Get the first netId that is superior or equal to the provided nextNetId and is available.
     */
    private static int getNextAvailableNetIdLocked(
            int nextNetId, @NonNull SparseBooleanArray netIdInUse) {
        int netId = nextNetId;
        for (int i = MIN_NET_ID; i <= MAX_NET_ID; i++) {
            if (!netIdInUse.get(netId)) {
                return netId;
            }
            netId = getNextNetId(netId);
        }
        throw new IllegalStateException("No free netIds");
    }

    /**
     * Reserve a new ID for a network.
     */
    public int reserveNetId() {
        synchronized (mNetIdInUse) {
            final int netId = getNextAvailableNetIdLocked(mNextNetId, mNetIdInUse);
            // Set mNextNetId for the next call to this method
            mNextNetId = getNextNetId(netId);
            // Make sure NetID unused.  http://b/16815182
            mNetIdInUse.put(netId, true);
            return netId;
        }
    }

    /**
     * Clear a previously reserved ID for a network.
     */
    public void releaseNetId(int id) {
        synchronized (mNetIdInUse) {
            mNetIdInUse.delete(id);
        }
    }
}
