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

import android.os.IInterface;

/**
 * Base implementation for SMS over IMS functionality.
 *
 * Any class wishing to implement SMS over IMS functionality to the framework should override this
 * class.
 * @hide
 */

public class SmsFeature extends ImsFeature {

    // TODO: Implement SMS_FEATURE in ImsFeature as well as the ImsResolver so that an ImsService
    //       can register for it.
    // TODO: Create SMS feature AIDL interface
    // TODO: Create Documentation for methods

    public void registerSmsListener(SmsListener l) {

    }

    public void sendSms(int format, int messageRef, boolean retry, byte[] smsc,
            byte[] pdu) {

    }

    public void acknowledgeSms(int result) {

    }

    public void addSmsFeatureConfigCallback(SmsFeatureConfigCallback c) {

    }

    public void setFeatureValue(SmsFeatureConfig config) {

    }

    public void queryFeatureValue(int feature, SmsFeatureConfigCallback c) {

    }

    @Override
    public void onFeatureRemoved() {

    }

    @Override
    public final IInterface getBinder() {
        return null;
    }
}
