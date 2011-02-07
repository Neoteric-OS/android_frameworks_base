/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.internal.telephony.data;

import com.android.internal.telephony.data.DataInterface.BearerType;

class CdmaNAI extends DataProfile {

    int mProfileId;
    DataServiceType mServiceTypes[];
    BearerType mBearerType = null;

    CdmaNAI(int profileId, DataServiceType[] serviceTypes, String bearerType) {
        super();
        this.mProfileId = profileId;
        mServiceTypes = serviceTypes;
        try {
            this.mBearerType = Enum.valueOf(BearerType.class, bearerType.toUpperCase());
        } catch (Exception e) {
            //TODO: is falling back to V4 a good thing?
            this.mBearerType = BearerType.IP;
        }
    }

    boolean canHandleServiceType(DataServiceType type) {
        for (DataServiceType t : mServiceTypes) {
            if (t == type)
                return true;
        }
        return false;
    }

    @Override
    DataProfileType getDataProfileType() {
        return DataProfileType.PROFILE_TYPE_3GPP2_NAI;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
        .append(", ").append(mProfileId)
        .append(", ").append(mBearerType)
        .append(", [");
        for (DataServiceType t : mServiceTypes) {
            sb.append(", ").append(t);
        }
        sb.append("]");
        sb.append("]");
        return sb.toString();
    }

    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString())
          .append(mProfileId)
          .append("]");
        return sb.toString();
    }

    @Override
    String toHash() {
        return this.toString();
    }

    @Override
    BearerType getBearerType() {
        return BearerType.IP;
    }
}
