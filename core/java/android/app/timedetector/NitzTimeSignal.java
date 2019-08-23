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

package android.app.timedetector;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TimestampedValue;

import java.util.Objects;

/**
 * A time signal from NITZ. The value consists of the number of milliseconds elapsed since
 * 1/1/1970 00:00:00 UTC and the time according to the elapsed realtime clock when that number was
 * established. The elapsed realtime clock is considered accurate but volatile, so time signals
 * must not be persisted across device resets.
 *
 * @hide
 */
public final class NitzTimeSignal implements Parcelable {

    public static final Parcelable.Creator<NitzTimeSignal> CREATOR =
            new Parcelable.Creator<NitzTimeSignal>() {
                public NitzTimeSignal createFromParcel(Parcel in) {
                    return NitzTimeSignal.createFromParcel(in);
                }

                public NitzTimeSignal[] newArray(int size) {
                    return new NitzTimeSignal[size];
                }
            };

    private final String mNitzDebugString;
    private final TimestampedValue<Long> mUtcTime;

    public NitzTimeSignal(String nitzDebugString, TimestampedValue<Long> utcTime) {
        mNitzDebugString = Objects.requireNonNull(nitzDebugString);
        mUtcTime = Objects.requireNonNull(utcTime);
    }

    private static NitzTimeSignal createFromParcel(Parcel in) {
        String nitzDebugString = in.readString();
        TimestampedValue<Long> utcTime =
                TimestampedValue.readFromParcel(in, null /* classLoader */, Long.class);
        return new NitzTimeSignal(nitzDebugString, utcTime);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mNitzDebugString);
        TimestampedValue.writeToParcel(dest, mUtcTime);
    }

    @NonNull
    public String getNitzDebugString() {
        return mNitzDebugString;
    }

    @NonNull
    public TimestampedValue<Long> getUtcTime() {
        return mUtcTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NitzTimeSignal that = (NitzTimeSignal) o;
        return Objects.equals(mNitzDebugString, that.mNitzDebugString)
                && Objects.equals(mUtcTime, that.mUtcTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNitzDebugString, mUtcTime);
    }

    @Override
    public String toString() {
        return "TimeSignal{"
                + "mNitzDebugString='" + mNitzDebugString + '\''
                + ", mUtcTime=" + mUtcTime
                + '}';
    }
}
