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

package android.net;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * This class provides an interface for apps to manage platform VPN profiles
 *
 * <p>Apps can use this API to provide profiles with which the platform can set up a VPN without
 * further app intermediation. If the app is selected as an always-on VPN, the platform will trigger
 * the negotiation of the VPN without binding to the provisioning app.
 *
 * <p>VPN apps using supported protocols should preferentially use this API over the {@link
 * VpnService}, due to improved platform integration.
 *
 * @see Ikev2VpnProfileBuilder
 */
public class VpnManager {
    private final Context mContext;
    private final IConnectivityManager mService;

    /** Package method to validate address and prefixLength. */
    static void validateInetAddress(InetAddress address, int prefixLength) {
        if (address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Bad address");
        }
        if (address instanceof Inet4Address) {
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("Bad prefixLength");
            }
        } else if (address instanceof Inet6Address) {
            if (prefixLength < 0 || prefixLength > 128) {
                throw new IllegalArgumentException("Bad prefixLength");
            }
        } else {
            throw new IllegalArgumentException("Unsupported family");
        }
    }

    /** @hide */
    public VpnManager(@NonNull Context ctx, @NonNull IConnectivityManager service) {
        mContext = checkNotNull(ctx, "missing Context");
        mService = checkNotNull(service, "missing IConnectivityManager");
    }

    /**
     * Installs a VpnProfile configuration based on the provisioning app's package name.
     *
     * @param profile the VpnProfile provided by this package. Will override any previous VpnProfile
     *     stored for this package. May be null to remove a previously provisioned profile.
     * @returns an intent to request user consent if needed (null otherwise).
     */
    @Nullable
    public Intent provisionVpn(@NonNull Context ctx, @Nullable VpnProfile profile) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Requests the starting of a previously provisioned VPN.
     *
     * @throws SecurityException exception if user consent was not obtained, or user settings
     *     prevent this VPN from being setup.
     */
    public void startVpn() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** Tears down the VPN provided by this package (if any) */
    public void stopVpn() {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
