/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity;

import static com.android.server.connectivity.Flags.replaceVpnProfileStore;

import android.annotation.NonNull;
import android.security.LegacyVpnProfileStore;

import com.android.internal.net.VpnBlobStore;

/**
 * Mockable indirection to the actual profile store.
 * @hide
 */
public class VpnProfileStore {
    /**
     * Stores the profile under the alias in the profile database. Existing profiles by the
     * same name will be replaced.
     * @param alias The name of the profile
     * @param profile The profile.
     * @return true if the profile was successfully added. False otherwise.
     * @hide
     */
    public boolean put(@NonNull String alias, @NonNull byte[] profile) {
        if (replaceVpnProfileStore()) {
            return VpnBlobStore.getInstance().put(alias, profile);
        }
        return LegacyVpnProfileStore.put(alias, profile);
    }

    /**
     * Retrieves a profile by the name alias from the profile database.
     * @param alias Name of the profile to retrieve.
     * @return The unstructured blob, that is the profile that was stored using
     *         LegacyVpnProfileStore#put or with
     *         android.security.Keystore.put(Credentials.VPN + alias).
     *         Returns null if no profile was found.
     * @hide
     */
    public byte[] get(@NonNull String alias) {
        if (replaceVpnProfileStore()) {
            return VpnBlobStore.getInstance().get(alias);
        }
        return LegacyVpnProfileStore.get(alias);
    }

    /**
     * Removes a profile by the name alias from the profile database.
     * @param alias Name of the profile to be removed.
     * @return True if a profile was removed. False if no such profile was found.
     * @hide
     */
    public boolean remove(@NonNull String alias) {
        if (replaceVpnProfileStore()) {
            return VpnBlobStore.getInstance().remove(alias);
        }
        return LegacyVpnProfileStore.remove(alias);
    }

    /**
     * Lists the vpn profiles stored in the database.
     * @return An array of strings representing the aliases stored in the profile database.
     *         The return value may be empty but never null.
     * @hide
     */
    public @NonNull String[] list(@NonNull String prefix) {
        if (replaceVpnProfileStore()) {
            return VpnBlobStore.getInstance().list(prefix);
        }
        return LegacyVpnProfileStore.list(prefix);
    }
}
