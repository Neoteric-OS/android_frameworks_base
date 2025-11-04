/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.scan;

import android.media.tv.extension.scan.IScanListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IScanInterface {
    /**
     * Create scan session.
     *
     * @param broadcastType @ScanConstants.BroadcastType broadcast type, such as ATSC
     *        countryCode  countryCode based on ISO 3166-1 alpha-3
     *        operator  @ScanConstants.OperatorType satellite, cable and IP-based operator type
     *        listener  ScanListener listens for updates
     *        optionalParams  other optional scan parameters
     * @return IBinder of IScanSession
     */
    IBinder createSession(int broadcastType, String countryCode, String operator,
        in IScanListener listener, in Bundle optionalParams);
    /**
     * Get parameters, such as quick scan default parameters
     *
     * @param broadcastType   @ScanConstants.BroadcastType broadcast type, such as ATSC
     *        countryCode  countryCode based on ISO 3166-1 alpha-3
     *        operator  @ScanConstants.OperatorType satellite, cable and IP-based operator type
     *        listener  ScanListener listens for updates
     *        params  specify the type of parameters to be acquired, including frequency_list,
     *        quickScan_parameter, singleCable_bandFrequency, DCSS_bandFrequency, transponder,
     *        LNB_settings, LCN_type
     * @return Bundle with acquied information with the params
     */
    Bundle getParameters(int broadcastType, String countryCode, String operator, in Bundle params);
}
