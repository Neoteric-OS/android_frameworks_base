/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contains voice and data activation state information.
 *
 * The following information is included in returned SimActivationState:
 *
 * <ul>
 *   <li>voice activation state: STATE_UNKNOWN, STATE_ACTIVATED, STATE_DEACTIVATED
 *   <li>data activation state: STATE_UNKNOWN, STATE_ACTIVATED, STATE_DEACTIVATED, STATE_RESTRICTED
 * </ul>
 */
public class SimActivationState implements Parcelable {

    private static final boolean DBG = true;
    private static final String LOG_TAG = "SimActivationState";
    
    /**
     * Initial activation state, unknown.
     */
    public static final int STATE_UNKNOWN = 0;

    /**
     * Indicate SIM has been successfully activated.
     */
    public static final int STATE_ACTIVATED = 1;

    /**
     * Indicate SIM has been deactivated by the carrier so that service is not available
     * and requires activation service to enable services.
     * Carrier apps could be signalled to set activation state to deactivated if detected abnormal
     * sim state and set it back to activated after successfully run activation service.
     */
    public static final int STATE_DEACTIVATED = 2;

    /**
     * Restricted state indicate SIM has been activated but service are restricted.
     * note this is only available for data activation state. for example out of byte sim state.
     */
    public static final int STATE_RESTRICTED = 3;

    private AtomicInteger mVoiceActivationState = new AtomicInteger(STATE_UNKNOWN);
    private AtomicInteger mDataActivationState = new AtomicInteger(STATE_UNKNOWN);

    private static String convertToStr(int state) {
        switch (state) {
            case STATE_UNKNOWN:
                return "unknown";
            case STATE_ACTIVATED:
                return "activated";
            case STATE_DEACTIVATED:
                return "deactivated";
            case STATE_RESTRICTED:
                return "restricted";
            default:
                return "invalid/unknown state";
        }
    }

    public static boolean isValidActivationState(int state, boolean isData) {
        switch (state) {
            case STATE_UNKNOWN:
            case STATE_ACTIVATED:
            case STATE_DEACTIVATED:
                return true;
            case STATE_RESTRICTED:
                return isData;
            default:
                return false;
        }
    }

    public int getVoiceActivationState() {
        return mVoiceActivationState.get();
    }

    public int getDataActivationState() {
        return mDataActivationState.get();
    }

    public String getVoiceActivationStateStr() {
        return convertToStr(mVoiceActivationState.get());
    }

    public String getDataActivationStateStr() {
        return convertToStr(mDataActivationState.get());
    }

    /**
     * @hide
     */
    public boolean setVoiceActivationState(int state) {
        boolean updated = mVoiceActivationState.compareAndSet(
                mVoiceActivationState.get(),
                state);
        if (updated) {
            if (DBG) log("setVoiceActivationState = " + mVoiceActivationState);
        }
        return updated;
    }

    /**
     * @hide
     */
    public boolean setDataActivationState(int state) {
        boolean updated = mDataActivationState.compareAndSet(
                mDataActivationState.get(),
                state);
        if (updated) {
            Rlog.d(LOG_TAG, "[SimActivationState] setDataActivationState="
                    + mDataActivationState);
        }
        return updated;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mVoiceActivationState.get());
        out.writeInt(mDataActivationState.get());
    }

    public SimActivationState(SimActivationState s) {
        copyFrom(s);
    }

    public SimActivationState() {
    }

    public SimActivationState(Parcel in) {
        mVoiceActivationState.set(in.readInt());
        mDataActivationState.set(in.readInt());
    }

    protected void copyFrom(SimActivationState s) {
        mVoiceActivationState = s.mVoiceActivationState;
        mDataActivationState = s.mDataActivationState;
    }

    public int describeContents() {
        return 0;
    }

    public static final Creator<SimActivationState> CREATOR =
            new Creator<SimActivationState>() {
                public SimActivationState createFromParcel(Parcel in) {
                    return new SimActivationState(in);
                }

                public SimActivationState[] newArray(int size) {
                    return new SimActivationState[size];
                }
            };

    private static void log(String s) {
        Rlog.d(LOG_TAG, s);
    }
}
