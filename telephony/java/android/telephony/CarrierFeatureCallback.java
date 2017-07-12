/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.telephony;

import android.annotation.SystemApi;

import com.android.internal.telephony.ICarrierFeatureCallback;

/**
 * Callback base class for receiving information on whether an app is authorized to use a certain
 * carrier feature.
 * @hide
 */
@SystemApi
abstract public class CarrierFeatureCallback extends ICarrierFeatureCallback.Stub {
    /**
     * Called when the carrier app finishes checking whether the app identified by {@code appUid}
     * is authorized to use the feature.
     * @param appUid The uid of the app that was queried.
     * @param feature The integer feature code as defined in {@link TelephonyManager}
     * @param isAuthorized true if the app is authorized, false otherwise.
     */
    @Override
    public void onFeatureAuthorizationCheckComplete(int appUid,
            @TelephonyManager.CarrierFeatureCode int feature, boolean isAuthorized) { }
}
