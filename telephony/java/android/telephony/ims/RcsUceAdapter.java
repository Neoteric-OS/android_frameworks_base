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

package android.telephony.ims;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.net.Uri;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Manages RCS User Capability Exchange for the subscription specified.
 *
 * @see RcsFeatureController#getUceAdapter()
 * @hide
 */
public class RcsUceAdapter {

    /**
     * An unknown error has caused the request to fail.
     */
    public static final int ERROR_GENERIC_FAILURE = 1;
    /**
     * The carrier network does not have UCE support enabled for this subscriber.
     */
    public static final int ERROR_NOT_ENABLED = 2;
    /**
     * The data network that the device is connected to does not support UCE currently (e.g. it is
     * 1x only currently).
     */
    public static final int ERROR_NOT_AVAILABLE = 3;
    /**
     * The network has responded with SIP 403 error and a reason "User not registered."
     */
    public static final int ERROR_NOT_REGISTERED = 4;
    /**
     * The network has responded to this request with a SIP 403 error and reason "not authorized for
     * presence" for this subscriber.
     */
    public static final int ERROR_NOT_AUTHORIZED = 5;
    /**
     * The network has responded to this request with a SIP 403 error and no reason.
     */
    public static final int ERROR_FORBIDDEN = 6;
    /**
     * The contact URI requested is not provisioned for VoLTE or it is not known as an IMS
     * subscriber to the carrier network.
     */
    public static final int ERROR_NOT_FOUND = 7;
    /**
     * The capabilities request contained too many URIs for the carrier network to handle. Retry
     * with a lower number of contact numbers. The number varies per carrier.
     */
    // TODO: Try to integrate this into the API so that the service will split based on carrier.
    public static final int ERROR_REQUEST_TOO_LARGE = 8;
    /**
     * The network did not respond to the capabilities request before the request timed out.
     */
    public static final int ERROR_REQUEST_TIMEOUT = 10;
    /**
     * The request failed due to the service having insufficient memory.
     */
    public static final int ERROR_INSUFFICIENT_MEMORY = 11;
    /**
     * The network was lost while trying to complete the request.
     */
    public static final int ERROR_LOST_NETWORK = 12;
    /**
     * The request has failed because the same request has already been added to the queue.
     */
    public static final int ERROR_ALREADY_IN_QUEUE = 13;

    /**@hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ERROR_", value = {
            ERROR_GENERIC_FAILURE,
            ERROR_NOT_ENABLED,
            ERROR_NOT_AVAILABLE,
            ERROR_NOT_REGISTERED,
            ERROR_NOT_AUTHORIZED,
            ERROR_FORBIDDEN,
            ERROR_NOT_FOUND,
            ERROR_REQUEST_TOO_LARGE,
            ERROR_REQUEST_TIMEOUT,
            ERROR_INSUFFICIENT_MEMORY,
            ERROR_LOST_NETWORK,
            ERROR_ALREADY_IN_QUEUE
    })
    public @interface ErrorCode {}

    /**
     * The last publish has resulted in a "200 OK" response or the device is using SIP OPTIONS for
     * UCE.
     */
    public static final int PUBLISH_STATE_200_OK = 1;

    /**
     * The hasn't published its capabilities since boot or hasn't gotten any publish response yet.
     */
    public static final int PUBLISH_STATE_NOT_PUBLISHED = 2;

    /**
     * The device has tried to publish its capabilities, which as resulted in an error. This error
     * is related to the fact that the device is not VoLTE provisioned.
     */
    public static final int PUBLISH_STATE_VOLTE_PROVISION_ERROR = 3;

    /**
     * The device has tried to publish its capabilities, which as resulted in an error. This error
     * is related to the fact that the device is not RCS or UCE provisioned.
     */
    public static final int PUBLISH_STATE_RCS_PROVISION_ERROR = 4;

    /**
     * The last publish resulted in a "408 Request Timeout" response.
     */
    public static final int PUBLISH_STATE_REQUEST_TIMEOUT = 5;

    /**
     * The last publish resulted in another unknown error, such as SIP 503 - "Service Unavailable"
     * or SIP 423 - "Interval too short".
     * <p>
     * Device shall retry with exponential back-off.
     */
    public static final int PUBLISH_STATE_OTHER_ERROR = 6;

    /**@hide*/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "PUBLISH_STATE_", value = {
            PUBLISH_STATE_200_OK,
            PUBLISH_STATE_NOT_PUBLISHED,
            PUBLISH_STATE_VOLTE_PROVISION_ERROR,
            PUBLISH_STATE_RCS_PROVISION_ERROR,
            PUBLISH_STATE_REQUEST_TIMEOUT,
            PUBLISH_STATE_OTHER_ERROR
    })
    public @interface PublishState {}


    /**
     * Provides a one-time callback for the response to a UCE request. After this callback is called
     * by the framework, the reference to this callback will be discarded on the service side.
     * @see #requestCapabilities(Executor, List, CapabilitiesCallback)
     */
    public static class CapabilitiesCallback {

        /**
         * Notify this application that the pending capability request has returned successfully.
         * @param contactCapabilities List of capabilities associated with each contact requested.
         */
        public void onCapabilitiesReceived(List<RcsContactUceCapability> contactCapabilities) {

        }

        /**
         * The pending request has resulted in an error and may need to be retried, depending on the
         * error code.
         * @param errorCode The reason for the framework being unable to process the request.
         */
        public void onError(@ErrorCode int errorCode) {

        }
    }

    private final int mSubId;

    /**
     * Not to be instantiated directly, use
     * {@link RcsFeatureController#createForSubscriptionId(Context, int)} and
     * {@link RcsFeatureController#getUceAdapter()} to instantiate this manager class.
     */
    RcsUceAdapter(int subId) {
        mSubId = subId;
    }

    /**
     * Request the User Capability Exchange capabilities for one or more contacts.
     * <p>
     * Be sure to check the availability of this feature using
     * {@link RcsFeatureController#isAvailable(int)} and ensuring
     * {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_OPTIONS_UCE} or
     * {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_PRESENCE_UCE} is enabled or else
     * this operation will fail with {@link #ERROR_NOT_AVAILABLE} or {@link #ERROR_NOT_ENABLED}.
     *
     * @param contactNumbers A list of numbers that the capabilities are being requested for.
     * @param c A one-time callback for when the request for capabilities completes or there is an
     *         error processing the request.
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public void requestCapabilities(@CallbackExecutor Executor executor, List<Uri> contactNumbers,
            CapabilitiesCallback c) {
        throw new UnsupportedOperationException("isUceSettingEnabled is not supported.");
    }

    /**
     * Gets the last publish result from the UCE service if the device is using an RCS presence
     * server.
     * @return The last publish result from the UCE service. If the device is using SIP OPTIONS,
     * this method will return {@link #PUBLISH_STATE_200_OK} as well.
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public int getUcePublishState() {
        throw new UnsupportedOperationException("getPublishState is not supported.");
    }

    /**
     * The user’s setting for whether or not Presence and User Capability Exchange (UCE) is enabled
     * for the associated subscription.
     *
     * @return true if the user’s setting for UCE is enabled, false otherwise. If false,
     * {@link RcsFeatureController#isCapable(int)} will return false for
     * {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_OPTIONS_UCE} and
     * {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_PRESENCE_UCE}
     * @see #setUceSettingEnabled(boolean)
     */
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isUceSettingEnabled() {
        // TODO: add SubscriptionController column for this property.
        throw new UnsupportedOperationException("isUceSettingEnabled is not supported.");
    }
    /**
     * Change the user’s setting for whether or not UCE is enabled for the associated subscription.
     * @param isEnabled the user's setting for whether or not they wish for Presence and User
     *         Capability Exchange to be enabled. If false,
     *         {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_OPTIONS_UCE} and
     *         {@link RcsFeature.RcsImsCapabilities#CAPABILITY_TYPE_PRESENCE_UCE} capability will be
     *         disabled, depending on which type of UCE the carrier supports.
     * @see #isUceSettingEnabled()
     */
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setUceSettingEnabled(boolean isEnabled) {
        // TODO: add SubscriptionController column for this property.
        throw new UnsupportedOperationException("setUceSettingEnabled is not supported.");
    }
}
