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

package android.telephony;

import android.telephony.mbms.IMbmsListener;
import android.telephony.mbms.IStreaingListener;

/** @hide */
public class MbmsDownloadManager {
    private final Context mContext;
    private mSubId = INVALID_SUBSCRIPTION_ID;

    /**
     * should use createManager to create/initialize a copy
     * @hide
     */
    public MbmsDownloadManager(Context context) {
//        mContext = context;
    }

    public static MbmsDownloadManager createManager(Context context,
            IMbmsDownloadManagerListener listener, String downloadAppName) {
//        MbmsDownloadManager mdm = context.getSystemService(Context.MBMS_DOWNLOAD_SERVICE);
//        if (mdm == null) return mdm;
//        mdm.initialize(listener, downloadAppName,
//                SubscriptionManager.getDefaultSubscriptionId());
//        return mdm;
    }

    public static MbmsDownloadManager createManager(Context context,
            IMbmsDownloadManagerListener listener, String downloadAppName, int subId) {
//        MbmsDownloadManager mdm = context.getSystemService(Context.MBMS_DOWNLOAD_SERVICE);
//        if (mdm == null) return mdm;
//        mdm.initialize(listener, downloadAppName, subId);
//        return mdm;
    }

    private void initialize(MbmsDownloadCallbacks callbacks, String downloadAppName, int subId) {
        // assert all empty and set
    }

    /**
     * same is terminateMSDC.
     */
    public dispose() {
    }

    /**
     * Gets the list of files published for download.
     * They may occur at times far in the future.
     * servicesClasses lets the app filter on types of files and is opaque data between
     *     the app and the carrier
     */
    public List<FileServiceInfo> getFileServices(List<String> serviceClasses) {
    }


    public static final String EXTRA_REQUEST         = "extraRequest";

    public static final int RESULT_SUCCESSFUL = 1;
    public static final int RESULT_CANCELLED  = 2;
    public static final int RESULT_EXPIRED    = 3;
    // TODO - more results!

    public static final String EXTRA_RESULT          = "extraResult";
    public static final String EXTRA_URI             = "extraDownloadedUri";

    public static final String EXTRA_DOWNLOAD_SIZE   = "extraDownloadSize";
    public static final String EXTRA_CURRENT_SIZE    = "extraCurrentSize";
    public static final String EXTRA_DECODED_PERCENT = "extraDecodedPercent";

    /**
     * Requests a future download.
     * returns a token which may be used to cancel a download.
     * fileServiceInfo indicates what FileService to download from
     * source indicates which file to download from the given FileService.  This is
     *     an optional field - it may be null or empty to indicate download everything from
     *     the FileService.
     * requestTtl indicates how long (in seconds) the system should attempt this download.
     *     If it is exceeded the download will be aborted, any temporary contents deleted and
     *     a failure status given via PendingIntent
     * destination is a file URI for where in the apps accessible storage locations to write
     *     the content.  This URI may be used to store temporary data and should not be
     *     accessed until the PendingIntent is called indicating success.
     * resultIntent is sent when each file is completed and when the request is concluded
     *     either via TTL expiration, cancel or error.
     *     This intent is sent with three extras: a {@link DownloadRequest} typed extra called
     *     {@link #EXTRA_REQUEST}, an Integer called {@link #EXTRA_RESULT} for the result code
     *     and a {@link Uri} called {@link #EXTRA_URI} to the resulting file (if successful).
     * progressIntent is an optional intent used to report file download progress.  If
     *     provided it will be sent for each file as it reaches appreciable milestones.
     *     This intent is sent with X extras:
     *      - a {@link DownloadRequest} typed extra called {@link #EXTRA_REQUEST},
     *      - an Integer called {@link #EXTRA_DOWNLOAD_SIZE} for the number of bytes expected
     *        to be downloaded.
     *      - an Integer called {@link #EXTRA_CURRENT_SIZE} for the number of bytes currently
     *        downloaded.
     *      - an Integer called {@link #EXTRA_DECODED_PERCENT} for the percent (0-100) of
     *        the file currently decoded.  Note this typically remains 0 until the file is
     *        completely received.
     */
    public int download(FileServiceInfo fileServiceInfo, Uri source, int requestTtl,
            Uri destination, PendingIntent resultIntent, PendingIntent progressIntent) {
    }

    public List<DownloadRequest> listPendingDownloads() {
    }

    public boolean cancelDownload(int downloadId) {
    }
}
