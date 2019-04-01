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

package android.net.util;

/**
 * Collection of utilities for data stall.
 */
public class DataStallUtils {
    /**
     * Detect data stall via using dns timeout counts.
     */
    public static final int DATA_STALL_EVALUATION_TYPE_DNS = 1;
    /**
     * Detect data stall via reporting bad connectivity from
     * {@link ConnectivityManager#reportNetworkConnectivity(@link Network, boolean)}.
     */
    public static final int DATA_STALL_EVALUATION_TYPE_REPORT_CONNECTIVITY = 2;

    // Default configuration values for data stall detection.
    public static final int DEFAULT_CONSECUTIVE_DNS_TIMEOUT_THRESHOLD = 5;
    public static final int DEFAULT_DATA_STALL_MIN_EVALUATE_TIME_MS = 60 * 1000;
    public static final int DEFAULT_DATA_STALL_VALID_DNS_TIME_THRESHOLD_MS = 30 * 60 * 1000;

    public static final int DEFAULT_DATA_STALL_EVALUATION_TYPES =
            DATA_STALL_EVALUATION_TYPE_DNS;
    // Reevaluate it as intending to increase the number. Larger log size may cause statsd
    // log buffer bust and have stats log lost.
    public static final int DEFAULT_DNS_LOG_SIZE = 20;
}
