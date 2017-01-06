/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.net.wifi.util;

import android.annotation.SystemApi;
import android.net.IpConfiguration;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;

import com.android.internal.annotations.VisibleForTesting;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

/**
 * @hide
 * WifiConfiguration utility for any {@link android.net.wifi.WifiConfiguration} related operations.
 * Currently contains:
 *   > Helper methods to identify the encryption of a WifiConfiguration object.
 */
@SystemApi
public class WifiConfigurationUtil {
    private WifiConfigurationUtil() { /* not constructable */ }

    /**
     * Checks if the provided |wepKeys| array contains any non-null value;
     */
    public static boolean hasAnyValidWepKey(String[] wepKeys) {
        for (int i = 0; i < wepKeys.length; i++) {
            if (wepKeys[i] != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper method to check if the provided |config| corresponds to a PSK network or not.
     */
    public static boolean isConfigForPskNetwork(WifiConfiguration config) {
        return config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK);
    }

    /**
     * Helper method to check if the provided |config| corresponds to a EAP network or not.
     */
    public static boolean isConfigForEapNetwork(WifiConfiguration config) {
        return (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)
                || config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X));
    }

    /**
     * Helper method to check if the provided |config| corresponds to a WEP network or not.
     */
    public static boolean isConfigForWepNetwork(WifiConfiguration config) {
        return (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE)
                && hasAnyValidWepKey(config.wepKeys));
    }

    /**
     * Helper method to check if the provided |config| corresponds to an open network or not.
     */
    public static boolean isConfigForOpenNetwork(WifiConfiguration config) {
        return !(isConfigForWepNetwork(config) || isConfigForPskNetwork(config)
                || isConfigForEapNetwork(config));
    }
}
