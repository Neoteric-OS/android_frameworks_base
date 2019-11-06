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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteException;

import com.android.internal.net.VpnProfile;

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

    static Intent getIntentForConfirmation() {
        Intent intent = new Intent();
        ComponentName componentName = ComponentName.unflattenFromString(
                Resources.getSystem().getString(
                        com.android.internal.R.string.config_platformVpnConfirmDialogComponent));
        intent.setClassName(componentName.getPackageName(), componentName.getClassName());
        return intent;
    }

    /** @hide */
    public VpnManager(@NonNull Context ctx, @NonNull IConnectivityManager service) {
        mContext = checkNotNull(ctx, "missing Context");
        mService = checkNotNull(service, "missing IConnectivityManager");
    }

    /**
     * Installs a VpnProfile configuration based on the provisioning app's package name.
     *
     * @returns an intent to request user consent if needed (null otherwise).
     * @param profile the VpnProfile provided by this package. Will override any previous VpnProfile
     *     stored for this package.
     * @returns an Intent requesting user consent to start the VPN, or null if consent is not
     *     required based on privileges or previous user consent.
     * @hide
     */
    @Nullable
    public Intent provisionVpn(@NonNull VpnProfile profile) {
        try {
            if (mService.provisionVpnProfile(profile, mContext.getOpPackageName())) {
                return null;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        return getIntentForConfirmation();
    }

    /**
     * Deletes the VPN profile configuration keyed on the provisioning app's package name.
     *
     * <p>Apps wishing to add a new VPN profile should use one of the VPN profile builders.
     *
     * @see Ikev2VpnProfileBuilder
     */
    public void deleteVpnProfile() {
        try {
            mService.deleteVpnProfile(mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests the starting of a previously provisioned VPN.
     *
     * @throws SecurityException exception if user or device settings prevent this VPN from being
     *     setup, or if user consent has not been granted
     */
    public void startVpn() {
        try {
            mService.startVpnProfile(mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Tears down the VPN provided by this package (if any) */
    public void stopVpn() {
        try {
            mService.stopVpnProfile(mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
