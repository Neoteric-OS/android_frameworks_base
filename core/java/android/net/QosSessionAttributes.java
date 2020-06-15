/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.SystemApi;

/**
 * Implemented by classes that encapsulate Qos related attributes that describe a Qos Session.
 * These attributes are not guaranteed to be different in between calls to onQosSessionAvailable().
 *
 * Use the instanceof keyword to determine the underlying type of the qos session attributes object.
 *
 * {@hide}
 */
@SystemApi
public interface QosSessionAttributes {
    /**
     *  The maximum allowed uplink bitrate in kbps.  Returns 0 if not provided.
     *
     *  Note: The Qos Session may be shared with OTHER applications besides yours.
     *
     *  @return the max uplink bit rate in kbps
     */
    default long getMaxUplinkBitRate() {
        return 0;
    }

    /**
     * The maximum allowed downlink bitrate in kbps.  Returns 0 if not provided.
     *
     * @return the max downlink bit rate in kbps
     */
    default long getMaxDownlinkBitRate() {
        return 0;
    }

    /**
     * Indicates the type of qos session.
     *
     * @return the qos session type
     */
    @QosSession.QosSessionType int getSessionType();
}
