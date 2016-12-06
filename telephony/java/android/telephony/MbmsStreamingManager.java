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
import android.telephony.mbms.IStreamingListener;

static import android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

/** @hide */
public class MbmsStreamingManager(Context context) {

    private final Context mContext;
    private mSubId = INVALID_SUBSCRIPTION_ID;

    /**
     * Create a new MbmsStreamingManager using the system default subscription ID.
     * @hide
     */
    public MbmsStreamingManager(Context context) {
        mContext = context;
    }

    public static MbmsStreamingManager createManager(Context context,
            MbmsStreamingManagerListener listener, String streamingAppName) {
        MbmsStreamingManager msm = context.getSystemService(Context.MBMS_STREAMING_SERVICE);
        if (msm == null) return msm;
        msm.initialize(listener, streamingAppName, SubscriptionManager.getDefaultSubscriptionId());
        return msm;
    }

    public static MbmsStreamingManager createmanager(Context,
            MbmsStreamingManagerListener listener, String streamingAppName, int subId) {
        if (SubscriptionManager.isValidSubscription(subId) == false) return null;
        MbmsStreamingManager msm = context.getSystemService(Context.MBMS_STREAMING_SERVICE);
        if (msm == null) return msm;
        msm.initialize(callbacks, streamingAppName, subId);
        return msm;
    }

    private void initialize(MbmsStreamingCallbacks callbacks, String streamingAppName, int subId) {
        // assert all empty and set
    }

    /**
     * same is terminateMSDC - also terminates any streaming services, etc.
     * The callbacks registered for by this uid for this streamingAppName will be terminated
     * and their disposed fucntions called, including those registered for child streaming services.
     */
    public dispose() {
        // service.dispose(streamingAppName);
    }

    /**
     * An inspection API to retrieve the list of streaming media currently be advertised.
     * The results come back asynchronously through the previously registered callbacks.
     * serviceClasses lets the app filter on types of programming and is opaque data between
     * the app and the carrier.
     *
     * Multiple calls replace the list of serviceClasses of interest.
     */
    public void getStreamingServices(List<Sting> serviceClasses) {
    }

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
}
