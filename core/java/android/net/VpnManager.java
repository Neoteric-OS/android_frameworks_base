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

import java.io.IOException;
import java.security.GeneralSecurityException;

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
    @NonNull private final Context mContext;
    @NonNull private final IConnectivityManager mService;

    static Intent getIntentForConfirmation() {
        Intent intent = new Intent();
        ComponentName componentName = ComponentName.unflattenFromString(
                Resources.getSystem().getString(
                        com.android.internal.R.string.config_customVpnConfirmDialogComponent));
        intent.setClassName(componentName.getPackageName(), componentName.getClassName());
        return intent;
    }

    /** @hide */
    public VpnManager(@NonNull Context ctx, @NonNull IConnectivityManager service) {
        mContext = checkNotNull(ctx, "missing Context");
        mService = checkNotNull(service, "missing IConnectivityManager");
    }

    /**
     * Install a VpnProfile configuration keyed on the calling app's package name.
     *
     * @param profile the VpnProfile provided by this package. Will override any previous VpnProfile
     *     stored for this package.
     * @return an Intent requesting user consent to start the VPN, or null if consent is not
     *     required based on privileges or previous user consent.
     * @throws IOException if the profile was unable to be parsed.
     */
    @Nullable
    public Intent provisionVpn(@NonNull PlatformVpnProfile profile) throws IOException {
        try {
            if (mService.provisionVpnProfile(profile.toVpnProfile(), mContext.getOpPackageName())) {
                return null;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to parse certificates or keys", e);
        }
        return getIntentForConfirmation();
    }

    /** Delete the VPN profile configuration that was provisioned by the calling app */
    public void deleteVpnProfile() {
        try {
            mService.deleteVpnProfile(mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Request the startup of a previously provisioned VPN.
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

    /** Tear down the VPN provided by the calling app (if any) */
    public void stopVpn() {
        try {
            mService.stopVpnProfile(mContext.getOpPackageName());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
