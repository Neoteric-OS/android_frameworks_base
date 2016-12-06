/*
** Copyright 2017, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package android.telephony.mbms;

/**
 * The interface the opaque MbmsStreamingService will satisfy.
 * @hide
 */
interface IMbmsDownloadService
{
    /**
     * Initialize download service
     * Registers this listener, subId with this appName
     *
     * No return value.  Async errors may be reported, but none expected (not doing anything yet).
     */
    void initialize(String appName, int subId, IMbmsDownlaodManagerListener listener);

    /**
     * - Registers serviceClasses of interest with the uid/appName/subId key.
     * - Starts asynch fetching data on download services of matching classes to be reported
     * later by callback.
     *
     * Note that subsequent calls with the same callback, appName, subId and uid will replace
     * the service class list.
     */
    void getFileServices(String appName, int subId, List<String> serviceClasses);

    /**
     * should move the params into a DownloadRequest parcelable
     */
    int download(String appName, int subId, String serviceId, Uri source, Uri destination,
            int ttl, PendingIntent cleanUpPI, PendingIntent fileDescriptorPI,
	    PendingIntent resultPI);

    List<DownloadRequest> listPendingDownloads();

    boolean cancelDownload(int downloadId);

    /**
     * End of life for this MbmsDownloadManager.
     * Any pending downloads remain in affect and may start up independently in the future.
     */
    void dispose(String appName, int subId);
}
