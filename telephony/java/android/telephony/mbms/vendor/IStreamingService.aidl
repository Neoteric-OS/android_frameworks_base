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

package android.telephony.mbms.vendor;

import android.net.Uri;
import android.telephony.SignalStrength;

/**
 * Controls activitity for a single StreamingService.
 * @hide
 */
interface IStreamingService
{
    Uri getPlaybackUri(String appName, int subId, String serviceId);

    void switchStreams(String appName, int subId, String oldServiceId, String newClassName);

    int getState(String appName, int subId, String serviceId);

    void stopStreaming(String appName, int subId, String serviceId);

    SignalStrength getSignalStrength(String appName, int subId, String serviceId);

    void dispose(String appName, int subId, String serviceId);
}
