/*
 * Copyright (c) 2013 The Android Open Source Project
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


package android.telephony.ims.internal.aidl;

import android.telephony.ims.internal.aidl.IImsConfigCallback;

import com.android.ims.ImsConfigListener;

/**
 * Provides APIs to get/set the IMS service feature/capability/parameters.
 * The config items include i tems provisioned by the operator.
 *
 * {@hide}
 */
interface IImsConfig {

    void addImsConfigCallback(IImsConfigCallback c);
    void removeImsConfigCallback(IImsConfigCallback c);
    int getProvisionedValue(int item);
    String getProvisionedStringValue(int item);
    int setProvisionedValue(int item, int value);
    int setProvisionedStringValue(int item, String value);
}
