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

package android.telephony.ims.feature;

/**
 * Callback interface used by the ImsService to notify a process when a feature has been set or
 * queried. Also notifies the process when another process has changed a feature.
 */

public class SmsFeatureConfigCallback {

    // TODO: Create Internal AIDL interface
    // TODO: Documentation

    public void onSetFeatureValue(int result, SmsFeatureConfig config) {

    }

    public void onQueryFeatureValue(SmsFeatureConfig config) {

    }

    public void onFeaturesChanged(SmsFeatureConfig config) {

    }
}
