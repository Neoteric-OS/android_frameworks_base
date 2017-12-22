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

package com.android.internal.telephony.euicc;

import com.android.internal.telephony.euicc.IGetAllProfilesCallback;
import com.android.internal.telephony.euicc.IGetProfileCallback;
import com.android.internal.telephony.euicc.IDisableProfileCallback;
import com.android.internal.telephony.euicc.ISwitchToProfileCallback;
import com.android.internal.telephony.euicc.ISetNicknameCallback;
import com.android.internal.telephony.euicc.IDeleteProfileCallback;
import com.android.internal.telephony.euicc.IResetMemoryCallback;
import com.android.internal.telephony.euicc.IGetDefaultSmdpAddressCallback;
import com.android.internal.telephony.euicc.IGetSmdsAddressCallback;
import com.android.internal.telephony.euicc.ISetDefaultSmdpAddressCallback;

/** @hide */
interface IEuiccCardController {
    oneway void getAllProfiles(String callingPackage, in IGetAllProfilesCallback callback);
    oneway void getProfile(String callingPackage, String iccid, in IGetProfileCallback callback);
    oneway void disableProfile(String callingPackage, String iccid, boolean refresh, in IDisableProfileCallback callback);
    oneway void switchToProfile(String callingPackage, String iccid, boolean refresh, in ISwitchToProfileCallback callback);
    String getEid();
    oneway void setNickname(String callingPackage, String iccid, String nickname, in ISetNicknameCallback callback);
    oneway void deleteProfile(String callingPackage, String iccid, in IDeleteProfileCallback callback);
    oneway void resetMemory(String callingPackage, int options, in IResetMemoryCallback callback);
    oneway void getDefaultSmdpAddress(String callingPackage, in IGetDefaultSmdpAddressCallback callback);
    oneway void getSmdsAddress(String callingPackage, in IGetSmdsAddressCallback callback);
    oneway void setDefaultSmdpAddress(String callingPackage, String address, in ISetDefaultSmdpAddressCallback callback);
}
