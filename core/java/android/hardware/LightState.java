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
 * limitations under the License.
 */

package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
public final class LightState implements Parcelable {
    public int brightness;
    public boolean isBlinking;
    public int onInterval;
    public int offInterval;

    public LightState() {
        brightness = 0;
        isBlinking = false;
        onInterval = 0;
        offInterval = 0;
    }

    public LightState(LightState state) {
        brightness = state.brightness;
        isBlinking = state.isBlinking;
        onInterval = state.onInterval;
        offInterval = state.offInterval;
    }

    /**
     * @hide
     */
    public static final Parcelable.Creator<LightState> CREATOR = new Parcelable.Creator() {
        public LightState[] newArray(int size) {
            return new LightState[size];
        }
        public LightState createFromParcel(Parcel in) {
            LightState state = new LightState();
            state.brightness = in.readInt();
            state.isBlinking = (Boolean) in.readValue(Boolean.class.getClassLoader());
            state.onInterval = in.readInt();
            state.offInterval = in.readInt();
            return state;
        }
    };

    /**
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(brightness);
        out.writeValue(isBlinking);
        out.writeInt(onInterval);
        out.writeInt(offInterval);
    }

    @Override
    public String toString() {
        return "[brightness: " + brightness + ", blinking: " + isBlinking
                + ", intervals = {" + onInterval + "," + offInterval + "}]";
    }
}
