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

package android.debug;

import android.annotation.SystemService;
import android.content.Context;

/**
 * This class allows the control of ADB-related functions. Currently only ADB over USB is
 * supported, and none of the API is public.
 *
 * @hide
 */
@SystemService(Context.ADB_SERVICE)
public class AdbManager {
    private static final String TAG = "AdbManager";

    public static final String WIRELESS_DEBUG_DEVICE_GUID_NONE = "";
    public static final int WIRELESS_DEBUG_PAIR_MODE_QR = 0;
    public static final int WIRELESS_DEBUG_PAIR_MODE_CODE = 1;

    /**
     * Contains the list of paired devices.
     *
     * @hide
     */
    public static final String WIRELESS_DEBUG_PAIRED_DEVICES_ACTION =
            "com.android.server.adb.WIRELESS_DEBUG_PAIRED_DEVICES";

    /**
     * Action indicating the status of set discoverable. Can be either
     *   STATUS_FAIL
     *   STATUS_SUCCESS
     *
     * @hide
     */
    public static final String WIRELESS_DEBUG_ENABLE_DISCOVER_ACTION =
            "com.android.server.adb.WIRELESS_DEBUG_SET_DISCOVERABLE";

    /**
     * Contains the list of discoverable devices.
     */
    public static final String WIRELESS_DEBUG_PAIRING_DEVICES_ACTION =
            "com.android.server.adb.WIRELESS_DEBUG_PAIRING_DEVICES";

    /**
     * Action indicating the status of a pairing. Can be either
     *   STATUS_FAIL
     *   STATUS_SUCCESS
     *   STATUS_CANCELLED
     *   STATUS_PAIRING_CODE
     *
     * @hide
     */
    public static final String WIRELESS_DEBUG_PAIRING_RESULT_ACTION =
            "com.android.server.adb.WIRELESS_DEBUG_PAIRING_RESULT";

    /**
     * Action indicating the status of a unpairing. Can be either
     *   STATUS_FAIL
     *   STATUS_SUCCESS
     *   STATUS_CANCELLED
     *
     * @hide
     */
    public static final String WIRELESS_DEBUG_UNPAIRING_RESULT_ACTION =
            "com.android.server.adb.WIRELESS_DEBUG_UNPAIRING_RESULT";

    /**
     * Extra containing the PairDevice map of paired/pairing devices.
     *
     * @hide
     */
    public static final String WIRELESS_DEVICES_EXTRA = "devices_map";

    /**
     * The status of the pairing/unpairing.
     *
     * @hide
     */
    public static final String WIRELESS_STATUS_EXTRA = "status";

    /**
     * The PairDevice.
     *
     * @hide
     */
    public static final String WIRELESS_PAIR_DEVICE_EXTRA = "pair_device";

    /**
     * The six-digit pairing code.
     *
     * @hide
     */
    public static final String WIRELESS_PAIRING_CODE_EXTRA = "pairing_code";

    /**
     * Status indicating the pairing/unpairing failed.
     *
     * @hide
     */
    public static final int WIRELESS_STATUS_FAIL = 0;

    /**
     * Status indicating the pairing/unpairing succeeded.
     *
     * @hide
     */
    public static final int WIRELESS_STATUS_SUCCESS = 1;

    /**
     * Status indicating the pairing/unpairing was cancelled.
     *
     * @hide
     */
    public static final int WIRELESS_STATUS_CANCELLED = 2;

    /**
     * Status indicating the pairing code for pairing.
     *
     * @hide
     */
    public static final int WIRELESS_STATUS_PAIRING_CODE = 3;

    private final Context mContext;
    private final IAdbManager mService;

    /**
     * {@hide}
     */
    public AdbManager(Context context, IAdbManager service) {
        mContext = context;
        mService = service;
    }
}
