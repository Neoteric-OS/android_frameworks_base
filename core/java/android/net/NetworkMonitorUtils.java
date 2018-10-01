/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.IntDef;
import android.content.Context;
import android.provider.Settings;

import com.android.internal.util.Protocol;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** @hide */
public class NetworkMonitorUtils {

    // IPC constants
    public static final String ACTION_NETWORK_CONDITIONS_MEASURED =
            "android.net.conn.NETWORK_CONDITIONS_MEASURED";
    public static final String EXTRA_CONNECTIVITY_TYPE = "extra_connectivity_type";
    public static final String EXTRA_NETWORK_TYPE = "extra_network_type";
    public static final String EXTRA_RESPONSE_RECEIVED = "extra_response_received";
    public static final String EXTRA_IS_CAPTIVE_PORTAL = "extra_is_captive_portal";
    public static final String EXTRA_CELL_ID = "extra_cellid";
    public static final String EXTRA_SSID = "extra_ssid";
    public static final String EXTRA_BSSID = "extra_bssid";
    /** real time since boot */
    public static final String EXTRA_REQUEST_TIMESTAMP_MS = "extra_request_timestamp_ms";
    public static final String EXTRA_RESPONSE_TIMESTAMP_MS = "extra_response_timestamp_ms";
    public static final String PERMISSION_ACCESS_NETWORK_CONDITIONS =
            "android.permission.ACCESS_NETWORK_CONDITIONS";
    private static final String DEFAULT_HTTPS_URL     = "https://www.google.com/generate_204";
    private static final String DEFAULT_HTTP_URL      =
            "http://connectivitycheck.gstatic.com/generate_204";

    // After a network has been tested this result can be sent with EVENT_NETWORK_TESTED.
    // The network should be used as a default internet connection.  It was found to be:
    // 1. a functioning network providing internet access, or
    // 2. a captive portal and the user decided to use it as is.
    public static final int NETWORK_TEST_RESULT_VALID = 0;

    // After a network has been tested this result can be sent with EVENT_NETWORK_TESTED.
    // The network should not be used as a default internet connection.  It was found to be:
    // 1. a captive portal and the user is prompted to sign-in, or
    // 2. a captive portal and the user did not want to use it, or
    // 3. a broken network (e.g. DNS failed, connect failed, HTTP request failed).
    public static final int NETWORK_TEST_RESULT_INVALID = 1;

    private static String getSetting(Context context, String symbol, String defaultValue) {
        final String value = Settings.Global.getString(context.getContentResolver(), symbol);
        return value != null ? value : defaultValue;
    }

    public static String getCaptivePortalServerHttpUrl(Context context) {
        return getSetting(context, Settings.Global.CAPTIVE_PORTAL_HTTP_URL, DEFAULT_HTTP_URL);
    }

    public static String getCaptivePortalServerHttpsUrl(Context context) {
        return getSetting(context, Settings.Global.CAPTIVE_PORTAL_HTTPS_URL, DEFAULT_HTTPS_URL);
    }

    public static boolean isValidationRequired(
            NetworkCapabilities dfltNetCap, NetworkCapabilities nc) {
        // TODO: Consider requiring validation for DUN networks.
        return dfltNetCap.satisfiedByNetworkCapabilities(nc);
    }
}
