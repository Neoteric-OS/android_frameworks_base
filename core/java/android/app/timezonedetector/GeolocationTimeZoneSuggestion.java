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

package android.app.timezonedetector;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A time zone suggestion from a geolocation source.
 *
 * <p>{@code zoneId}. When not {@code null}, {@code zoneId} contains the suggested time zone ID,
 * e.g. "America/Los_Angeles". {@code zoneId} can be {@code null} to indicate that the geolocation
 * source has entered an "un-opinionated" state and any previous suggestion is being withdrawn. A
 * geolocation source may become un-opinionated if the device's location is no longer known with
 * sufficient accuracy, or if the location is known but no time zone can be determined. For example,
 * the client may only have cached a subset of the global time zone mapping data and the device has
 * moved outside of the cached data before new data can be loaded, or there may be areas without a
 * reliable time zone mapping such as oceans or disputed areas.
 *
 * <p>{@code debugInfo} contains debugging metadata associated with the suggestion. This is used to
 * record why the suggestion exists and how it was entered. This information exists only to aid in
 * debugging and therefore is used by {@link #toString()}, but it is not for use in detection logic
 * and is not considered in {@link #hashCode()} or {@link #equals(Object)}.
 *
 * @hide
 */
public final class GeolocationTimeZoneSuggestion implements Parcelable {

    public static final @NonNull Creator<GeolocationTimeZoneSuggestion> CREATOR =
            new Creator<GeolocationTimeZoneSuggestion>() {
                public GeolocationTimeZoneSuggestion createFromParcel(Parcel in) {
                    return GeolocationTimeZoneSuggestion.createFromParcel(in);
                }

                public GeolocationTimeZoneSuggestion[] newArray(int size) {
                    return new GeolocationTimeZoneSuggestion[size];
                }
            };

    @NonNull private final String mZoneId;
    @Nullable private ArrayList<String> mDebugInfo;

    public GeolocationTimeZoneSuggestion(@Nullable String zoneId) {
        mZoneId = zoneId;
    }

    private static GeolocationTimeZoneSuggestion createFromParcel(Parcel in) {
        String zoneId = in.readString();
        GeolocationTimeZoneSuggestion suggestion = new GeolocationTimeZoneSuggestion(zoneId);
        @SuppressWarnings("unchecked")
        ArrayList<String> debugInfo = (ArrayList<String>) in.readArrayList(null /* classLoader */);
        suggestion.mDebugInfo = debugInfo;
        return suggestion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mZoneId);
        dest.writeList(mDebugInfo);
    }

    @NonNull
    public String getZoneId() {
        return mZoneId;
    }

    @NonNull
    public List<String> getDebugInfo() {
        return mDebugInfo == null
                ? Collections.emptyList() : Collections.unmodifiableList(mDebugInfo);
    }

    /**
     * Associates information with the instance that can be useful for debugging / logging. The
     * information is present in {@link #toString()} but is not considered for
     * {@link #equals(Object)} and {@link #hashCode()}.
     */
    public void addDebugInfo(String... debugInfos) {
        if (mDebugInfo == null) {
            mDebugInfo = new ArrayList<>();
        }
        mDebugInfo.addAll(Arrays.asList(debugInfos));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GeolocationTimeZoneSuggestion
                that = (GeolocationTimeZoneSuggestion) o;
        return Objects.equals(mZoneId, that.mZoneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mZoneId);
    }

    @Override
    public String toString() {
        return "GeolocationTimeZoneSuggestion{"
                + "mZoneId=" + mZoneId
                + ", mDebugInfo=" + mDebugInfo
                + '}';
    }
}
