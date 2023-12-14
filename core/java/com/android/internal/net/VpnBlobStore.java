/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.net;

import android.security.Credentials;
import android.security.LegacyVpnProfileStore;

import java.util.List;

/**
 * Database blob store for VPN.
 * @hide
 */
public class VpnBlobStore extends ConnectivityBlobStore {
    private static final String DB_NAME = "VpnBlobStore.db";
    // Prefix of vpn app excluded list, taken from Vpn.java
    private static final String VPN_APP_EXCLUDED = "VPNAPPEXCLUDED_";

    private static VpnBlobStore sInstance;
    private VpnBlobStore() {
        super(DB_NAME);

        // Import profiles from legacy keystore
        final List<String> prefixes = List.of(Credentials.VPN, Credentials.PLATFORM_VPN,
                Credentials.LOCKDOWN_VPN, VPN_APP_EXCLUDED);
        for (String prefix : prefixes) {
            for (String key : LegacyVpnProfileStore.list(prefix)) {
                final String name = prefix + key;
                boolean res = put(name, LegacyVpnProfileStore.get(name));
                LegacyVpnProfileStore.remove(name);
            }
        }
    }

    /** Returns an instance of VpnBlobStore. */
    public static VpnBlobStore getInstance() {
        if (sInstance == null) {
            sInstance = new VpnBlobStore();
        }
        return sInstance;
    }
}
