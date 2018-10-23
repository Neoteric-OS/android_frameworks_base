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

package android.telephony;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.ServiceState.RilRadioTechnology;

import java.util.Objects;

/**
 * Contains information about a call's attributes as passed up from the HAL.
 * @hide
 */
@SystemApi
public class CallAttributes implements Parcelable {
    private PreciseCallState mPreciseCallState;
    @RilRadioTechnology
    private int mRilRadioTech; // ServiceState RIL Radio tech: LTE, IWLAN, etc
    private CallQuality mCallQuality;


    public CallAttributes(PreciseCallState state, @RilRadioTechnology int callMode,
            CallQuality callQuality) {
        this.mPreciseCallState = state;
        this.mRilRadioTech = callMode;
        this.mCallQuality = callQuality;
    }

    @Override
    public String toString() {
        return "mPreciseCallState=" + mPreciseCallState + " mRilRadioTech=" + mRilRadioTech
                + " mCallQuality=" + mCallQuality;
    }

    private CallAttributes(Parcel in) {
        mPreciseCallState = (PreciseCallState) in.readValue(mPreciseCallState.getClass()
                .getClassLoader());
        mRilRadioTech = in.readInt();
        mCallQuality = (CallQuality) in.readValue(mCallQuality.getClass().getClassLoader());
    }

    // getters and setters
    public PreciseCallState getPreciseCallState() {
        return mPreciseCallState;
    }

    @RilRadioTechnology
    public int getCallMode() {
        return mRilRadioTech;
    }

    public CallQuality getCallQuality() {
        return mCallQuality;
    }

    public void setPreciseCallState(PreciseCallState s) {
        mPreciseCallState = s;
    }

    public void setCallMode(@RilRadioTechnology int m) {
        mRilRadioTech = m;
    }

    public void setCallQuality(CallQuality q) {
        mCallQuality = q;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPreciseCallState, mRilRadioTech, mCallQuality);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof CallAttributes) || hashCode() != o.hashCode()) {
            return false;
        }

        if (this == o) {
            return true;
        }

        CallAttributes s = (CallAttributes) o;

        return (mPreciseCallState == s.mPreciseCallState
                && mRilRadioTech == s.mRilRadioTech
                && mCallQuality == s.mCallQuality);
    }

    /**
     * {@link Parcelable#describeContents}
     */
    public @Parcelable.ContentsFlags int describeContents() {
        return 0;
    }

    /**
     * {@link Parcelable#writeToParcel}
     */
    public void writeToParcel(Parcel dest, @Parcelable.WriteFlags int flags) {
        mPreciseCallState.writeToParcel(dest, flags);
        dest.writeInt(mRilRadioTech);
        mCallQuality.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<CallAttributes> CREATOR = new Parcelable.Creator() {
        public CallAttributes createFromParcel(Parcel in) {
            return new CallAttributes(in);
        }

        public CallAttributes[] newArray(int size) {
            return new CallAttributes[size];
        }
    };
}
