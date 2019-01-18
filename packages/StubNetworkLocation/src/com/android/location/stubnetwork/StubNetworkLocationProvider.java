/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.location.stubnetwork;

import com.android.location.provider.LocationProviderBase;
import com.android.location.provider.ProviderPropertiesUnbundled;
import com.android.location.provider.ProviderRequestUnbundled;

import android.content.Context;
import android.location.Criteria;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.WorkSource;


public class StubNetworkLocationProvider extends LocationProviderBase {
    private static final String TAG = "StubNetworkLocationProvider";

    private static ProviderPropertiesUnbundled PROPERTIES = ProviderPropertiesUnbundled.create(
            false, false, false, false, true, true, true, Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE);

    public StubNetworkLocationProvider(Context context) {
        super(TAG, PROPERTIES);
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void onSetRequest(ProviderRequestUnbundled request, WorkSource source) {}

    @Override
    public int onGetStatus(Bundle extras) {
        return LocationProvider.AVAILABLE;
    }

    @Override
    public long onGetStatusUpdateTime() {
        return 0;
    }
}
