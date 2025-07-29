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

import android.os.Bundle;

/**
 * @hide
 */
oneway interface IScanListener {
    /**
     * Notify events during scan.
     *
     * @param event information that occurred in the scan, must include event_id and event_type.
     */
    void onEvent(in Bundle eventArgs);
    /**
     * Notify the scan progress.
     *
     * @param scanProgress scan progress
     *        scanProgressInfo bundle of progress information, must include channel_number_found
     */
    void onScanProgress(String scanProgress, in Bundle scanProgressInfo);
    /**
     * Notify the scan completion.
     *
     * @param ScanResult.SUCCESS/FAILED/CANCEL/BUSY depending on the scan
     *        optionScanInfo optional bundle for addition information
     */
    void onScanCompleted(int scanResult, in Bundle optionScanInfo);
    /**
     * Notify that the temporaily held channel list is stored.
     *
     * @param StoreResult.SUCCESS/FAILED/BUSY depending on the store
     */
    void onStoreCompleted(int storeResult);
}
