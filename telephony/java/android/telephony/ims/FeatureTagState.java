/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * Maps an IMS feature tag to its current state as set by the ImsService managing the related IMS
 * registration.
 * @hide
 */
public final class FeatureTagState implements Parcelable {

    private final String mFeatureTag;
    private final int mState;

    /**
     * Associate an IMS feature tag with its current state. See {@link DelegateRegistrationState}
     * and {@link DelegateConnectionStateCallback#onFeatureTagStatusChanged(
     * DelegateRegistrationState, List)} for more information on how and when this is used.
     *
     * @param featureTag The IMS feature tag that is deregistered or in the process of
     *                   deregistering.
     * @param state The {@link DelegateRegistrationState.DeregisteredReason},
     *         {@link DelegateRegistrationState.DeregisteringReason}, or
     *         {@link SipDelegateManager.DeniedReason} associated with this feature tag.
     */
    public FeatureTagState(String featureTag, int state) {
        mFeatureTag = featureTag;
        mState = state;
    }

    /**
     * Used for constructing instances during un-parcelling.
     */
    private FeatureTagState(Parcel source) {
        mFeatureTag = source.readString();
        mState = source.readInt();
    }

    /**
     * @return The IMS feature tag string that is in the process of deregistering or is
     * deregistered.
     */
    public String getFeatureTag() {
        return mFeatureTag;
    }

    /**
     * @return The reason for why the feature tag is currently in the process of deregistering,
     * has been deregistered, or has been denied. See {@link DelegateRegistrationState} and
     * {@link DelegateConnectionStateCallback} for more information.
     */
    public int getState() {
        return mState;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mFeatureTag);
        dest.writeInt(mState);
    }

    public static final Creator<FeatureTagState> CREATOR = new Creator<FeatureTagState>() {
        @Override
        public FeatureTagState createFromParcel(Parcel source) {
            return new FeatureTagState(source);
        }

        @Override
        public FeatureTagState[] newArray(int size) {
            return new FeatureTagState[size];
        }
    };
}
