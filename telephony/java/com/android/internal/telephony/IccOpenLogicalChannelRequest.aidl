/*
** Copyright 2007, The Android Open Source Project
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

package com.android.internal.telephony;

import android.os.IBinder;

/**
 * A request to open a logical channel to the ICC card.
 *
 * @hide
 */
@JavaOnlyImmutable @JavaDerive(toString=true, equals=true)
parcelable IccOpenLogicalChannelRequest {

    /** Subscription id */
    int subId;

    /** Physical slot index of the ICC card */
    int slotIndex;

    /** Package name for the calling app */
    String callingPackage;

    /** Application id */
    String aid;

    /** The P2 parameter described in ISO 7816-4 */
    int p2;
}
