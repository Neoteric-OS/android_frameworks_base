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

package android.net;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.Context;
import android.provider.Settings;

/**
 * A manager class for connectivity module settings.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class ConnectivitySettingsManager {

    private ConnectivitySettingsManager() {}

    /**
     * Whether to automatically switch away from wifi networks that lose Internet access.
     * Only meaningful if config_networkAvoidBadWifi is set to 0, otherwise the system always
     * avoids such networks. Valid values are:
     *
     * 0: Don't avoid bad wifi, don't prompt the user. Get stuck on bad wifi like it's 2013.
     * null: Ask the user whether to switch away from bad wifi.
     * 1: Avoid bad wifi.
     *
     * @hide
     */
    public static final String NETWORK_AVOID_BAD_WIFI = "network_avoid_bad_wifi";

    /**
     * User setting for ConnectivityManager.getMeteredMultipathPreference(). This value may be
     * overridden by the system based on device or application state. If null, the value
     * specified by config_networkMeteredMultipathPreference is used.
     *
     * @hide
     */
    public static final String NETWORK_METERED_MULTIPATH_PREFERENCE =
            "network_metered_multipath_preference";

    /**
     * Get avoid bad wifi setting from {@link Settings}.
     *
     * @param context The {@link Context} to query the setting.
     * @return The setting whether to automatically switch away from wifi networks that lose
     *         internet access.
     */
    @RequiresPermission(android.Manifest.permission.NETWORK_SETTINGS)
    @Nullable
    public static String getNetworkAvoidBadWifi(@NonNull Context context) {
        context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS);
        return Settings.Global.getString(context.getContentResolver(), NETWORK_AVOID_BAD_WIFI);
    }

    /**
     * Set avoid bad wifi setting to {@link Settings}.
     *
     * @param context The {@link Context} to set the setting.
     * @param value Whether to automatically switch away from wifi networks that lose internet
     *              access.
     */
    @RequiresPermission(android.Manifest.permission.NETWORK_SETTINGS)
    public static void setNetworkAvoidBadWifi(@NonNull Context context, @Nullable String value) {
        context.checkCallingOrSelfPermission(android.Manifest.permission.NETWORK_SETTINGS);
        Settings.Global.putString(context.getContentResolver(), NETWORK_AVOID_BAD_WIFI, value);
    }

    /**
     * Get network metered multipath preference from {@link Settings}.
     *
     * @param context The {@link Context} to query the setting.
     * @return The network metered multipath preference.
     */
    @RequiresPermission(android.Manifest.permission.NETWORK_SETTINGS)
    @Nullable
    public static String getNetworkMeteredMultipathPreference(@NonNull Context context) {
        context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS);
        return Settings.Global.getString(
                context.getContentResolver(), NETWORK_METERED_MULTIPATH_PREFERENCE);
    }

    /**
     * Set network metered multipath preference to {@link Settings}.
     *
     * @param context The {@link Context} to set the setting.
     * @param preference The network metered multipath preference.
     */
    @RequiresPermission(android.Manifest.permission.NETWORK_SETTINGS)
    public static void setNetworkMeteredMultipathPreference(@NonNull Context context,
            @Nullable String preference) {
        context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS);
        Settings.Global.putString(
                context.getContentResolver(), NETWORK_METERED_MULTIPATH_PREFERENCE, preference);
    }
}
