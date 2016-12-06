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

/**
 */
public class MbmsManager {
    /**
     * Contacts middleware, validates app and returns errors through exception or listener.
     * Same as Initialization, add listener.
     */
    public static MbmsManager getManager(Context context, String appId, IMbmsListener listener) {
    }

    /**
     * same is terminateMSDC - also terminates any streaming services, etc.
     */
    public static dispose();

    /**
     * An inspection API to retrieve the list of streaming media currently be advertised.
     * serviceClasses lets the app filter on types of programming and is opaque data between
     *     the app and the carrier.
     */
    public List<StreamingServiceInfo> getStreamingServices(List<Sting> serviceClasses);

    /**
     * Starts streaming a requested service, reporting status to the indicated listener.
     * Returns an object used to control that stream.
     *
     * replaces IMSDCStreamingController.initializeStreamgService and *.startStreaming
     */
    public StreamingService startStreaming(StreamingServiceInfo serviceInfo,
            IStreamingListener listener);

    /**
     * Lists all the services currently being streamed to the device.
     */
    public List<StreamingServiceInfo> getActiveStreamingServices();

    /**
     * Gets the list of files published for download.
     * They may occur at times far in the future.
     * servicesClasses lets the app filter on types of files and is opaque data between
     *     the app and the carrier
     */
    public List<FileServiceInfo> getFileServices(List<String> serviceClasses);

    /**
     * Requests a future download.
     * returns a token which may be used to cancel a download.
     * source indicates which file to download
     * requestTtl indicates how long the system should attempt this download.  If it is
     *     is exceeded the download will be aborted, any temporary contents deleted and
     *     a failure status given via PendingIntent
     * destination is a file URI for where in the apps accessible storage locations to write
     *     the content.  This URI may be used to store temporary data and should not be
     *     accessed until the PendingIntent is called indicating success.
     */
    public int download(Uri source, int requestTtl, Uri destination,
            PendingIntent resultIntent);
}
