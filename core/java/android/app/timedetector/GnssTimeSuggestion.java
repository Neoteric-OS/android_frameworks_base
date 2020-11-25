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

package android.app.timedetector;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.TimestampedValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A time signal from a GNSS time source like GPS.
 *
 * <p>{@code utcTime} is the suggested time. The {@code utcTime.value} is the number of milliseconds
 * elapsed since 1/1/1970 00:00:00 UTC. The {@code utcTime.referenceTimeMillis} is the value of the
 * elapsed realtime clock when the {@code utcTime.value} was established.
 * Note that the elapsed realtime clock is considered accurate but it is volatile, so time
 * suggestions cannot be persisted across device resets.
 *
 * <p>{@code debugInfo} contains debugging metadata associated with the suggestion. This is used to
 * record why the suggestion exists and how it was entered. This information exists only to aid in
 * debugging and therefore is used by {@link #toString()}, but it is not for use in detection
 * logic and is not considered in {@link #hashCode()} or {@link #equals(Object)}.

 * @hide
 */
public final class GnssTimeSuggestion implements Parcelable {

    public static final @NonNull Creator<GnssTimeSuggestion> CREATOR =
            new Creator<GnssTimeSuggestion>() {
                @Override
                public GnssTimeSuggestion createFromParcel(Parcel source) {
                    return GnssTimeSuggestion.createFromParcel(source);
                }

                @Override
                public GnssTimeSuggestion[] newArray(int size) {
                    return new GnssTimeSuggestion[size];
                }
            };

    @NonNull private final TimestampedValue<Long> mUtcTime;
    @Nullable
    private List<String> mDebugInfo;

    public GnssTimeSuggestion(@NonNull TimestampedValue<Long> utcTime) {
        mUtcTime = Objects.requireNonNull(utcTime);
        Objects.requireNonNull(utcTime.getValue());
    }

    private static GnssTimeSuggestion createFromParcel(Parcel in) {
        TimestampedValue<Long> utcTime = in.readParcelable(null /* classLoader */);
        GnssTimeSuggestion suggestion = new GnssTimeSuggestion(utcTime);
        @SuppressWarnings("unchecked")
        List<String> debugInfos = (ArrayList<String>) in.readArrayList(null /* classLoader */);
        suggestion.mDebugInfo = debugInfos;

        return suggestion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mUtcTime, 0);
        dest.writeList(mDebugInfo);
    }

    @NonNull
    public TimestampedValue<Long> getUtcTime() {
        return mUtcTime;
    }

    /** Returns previously recorded debug messages. */
    @NonNull
    public List<String> getDebugInfo() {
        List<String> debugInfo = mDebugInfo;
        if (debugInfo == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(debugInfo);
        }
    }

    /**
     * Adds information what may be useful in debuggin / logging.
     * Messages will be added to {@link #toString()} output, but ignored in {@link #hashCode()}
     * and {@link #equals(Object)}.
     */
    public void addDebugInfo(String... debugInfos) {
        List<String> debugInfo = mDebugInfo;
        if (debugInfo == null) {
            debugInfo = new ArrayList<>();
        }

        debugInfo.addAll(Arrays.asList(debugInfos));
        mDebugInfo = debugInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GnssTimeSuggestion that = (GnssTimeSuggestion) o;
        return mUtcTime.equals(that.mUtcTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUtcTime);
    }

    @Override
    public String toString() {
        return "GnssTimeSuggestion{"
                + "mUtcTime=" + mUtcTime
                + ", mDebugInfo=" + mDebugInfo
                + '}';
    }
}
