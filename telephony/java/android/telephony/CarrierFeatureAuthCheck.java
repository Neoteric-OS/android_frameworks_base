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
import android.os.RemoteException;

import com.android.internal.telephony.ICarrierFeatureAuthCheck;
import com.android.internal.telephony.ICarrierFeatureCallback;

/**
 * Carrier auth service base class for receiving requests to check whether an app (identified by
 * its uid) is authorized to use a certain carrier feature.
 * @hide
 */
@SystemApi
abstract public class CarrierFeatureAuthCheck extends ICarrierFeatureAuthCheck.Stub {
    /**
     * Called when a feature provider wishes to check whether a given app should be allowed to
     * use some carrier feature.
     * @param appUid The uid of the app to check.
     * @param feature The integer feature code as defined in {@link TelephonyManager}
     * @param callback A callback to use to return results. See {@link CarrierFeatureCallback}.
     */
    public void checkFeatureAuthorized(int appUid,
            @TelephonyManager.CarrierFeatureCode int feature, CarrierFeatureCallback callback) {

    }

    /**
     * Actual overridden binder method -- abstracts the ICarrierFeatureCallback aidl away from
     * implementing services.
     * @hide
     */
    @Override
    public void checkFeatureAuthorized(int appUid,
            @TelephonyManager.CarrierFeatureCode int feature, ICarrierFeatureCallback callback) {
        checkFeatureAuthorized(appUid, feature, new CarrierFeatureCallback() {
            @Override
            public void onFeatureAuthorizationCheckComplete(int appUid,
                    @TelephonyManager.CarrierFeatureCode int feature, boolean isAuthorized) {
                try {
                    callback.onFeatureAuthorizationCheckComplete(appUid, feature, isAuthorized);
                } catch (RemoteException e) {
                    // Ignore
                }
            }
        });
    }
}
