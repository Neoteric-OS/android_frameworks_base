/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.net.vcn.networkpriority;

import android.annotation.NonNull;

/** @hide */
public class CellNetworkPriority extends NetworkPriority {
    // public final String[] allowedPlmnIds;
    // public final String[] allowedCarrierIds;
    private final boolean mRoamingAllowed;
    private final boolean mOpportunisticRequired;

    public CellNetworkPriority(
            int networkQuality,
            boolean allowMetered,
            boolean roamingAllowed,
            boolean opportunisticRequired) {
        super(networkQuality, allowMetered);
        mRoamingAllowed = roamingAllowed;
        mOpportunisticRequired = opportunisticRequired;
    }

    public boolean isRoamingAllowed() {
        return mRoamingAllowed;
    }

    public boolean isOpportunisticRequired() {
        return mOpportunisticRequired;
    }

    public static class Builder extends NetworkPriority.Builder<Builder, CellNetworkPriority> {
        private boolean mRoamingAllowed;
        private boolean mOpportunisticRequired;

        public Builder() {}

        @NonNull
        public Builder setRoamingAllowed(boolean isRoamingAllowed) {
            mRoamingAllowed = isRoamingAllowed;
            return this;
        }

        @NonNull
        public Builder setOpportunisticRequired(boolean isRequired) {
            mOpportunisticRequired = isRequired;
            return this;
        }

        @Override
        @NonNull
        public CellNetworkPriority build() {
            return new CellNetworkPriority(
                    mNetworkQuality, mAllowMetered, mRoamingAllowed, mOpportunisticRequired);
        }
    }
}
