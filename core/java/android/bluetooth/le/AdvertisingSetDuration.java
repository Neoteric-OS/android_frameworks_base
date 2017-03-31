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

package android.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * The {@link AdvertisingSetDuration} provide a way to adjust advertising
 * duration for Bluetooth LE advertising set. Use
 * {@link AdvertisingSetDuration.Builder} to create an instance of this class.
 */
public final class AdvertisingSetDuration implements Parcelable {
    private final int duration;
    private final int maxExtendedAdvertisingEvents;

    private AdvertisingSetDuration(int duration, int maxExtendedAdvertisingEvents) {
        this.duration = duration;
        this.maxExtendedAdvertisingEvents = maxExtendedAdvertisingEvents;
    }

    private AdvertisingSetDuration(Parcel in) {
        duration = in.readInt();
        maxExtendedAdvertisingEvents = in.readInt();
    }

    /**
     * Returns the duration.
     */
    public int getDuration() { return duration; }

    /**
     * Returns the maximum number of extended advertising events the Controller
	 * shall attempt to send prior to terminating the extended advertising.
     */
    public int getMaxExtendedAdvertisingEvents() { return maxExtendedAdvertisingEvents; }

    @Override
    public String toString() {
        return "AdvertisingSetDuration [duration=" + duration
             + ", maxExtendedAdvertisingEvents=" + maxExtendedAdvertisingEvents + "]";
    }

    @Override
    public int describeContents() {
       return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(duration);
        dest.writeInt(maxExtendedAdvertisingEvents);
    }

    public static final Parcelable.Creator<AdvertisingSetDuration> CREATOR =
        new Creator<AdvertisingSetDuration>() {
          @Override
          public AdvertisingSetDuration[] newArray(int size) {
            return new AdvertisingSetDuration[size];
          }

          @Override
          public AdvertisingSetDuration createFromParcel(Parcel in) {
            return new AdvertisingSetDuration(in);
          }
        };

    /**
     * Builder class for {@link AdvertisingSetDuration}.
     */
    public static final class Builder {

        private int duration = 0;
        private int maxExtendedAdvertisingEvents = 0;

        /**
         * @param duration advertising duration, in 10ms unit. Valid range is
         * from 1 (10ms) to 65535 (655,350 ms)
         */
        public Builder setDuration(int duration) {
            this.duration = duration;
            return this;
        }

        /**
         * @param maxExtendedAdvertisingEvents maximum number of extended 
         * advertising events the controller shall attempt to send prior to
         * terminating the extended advertising, even if the duration has not expired.
         * Valid range is from 1 to 255.
         *
         * @throws IllegalArgumentException If the maxExtendedAdvertisingEvents is invalid.
         */
        public Builder setMaxExtendedAdvertisingEvents(int maxExtendedAdvertisingEvents) {
            if (maxExtendedAdvertisingEvents > 255 || maxExtendedAdvertisingEvents < 0) {
                throw new IllegalArgumentException("bad maxExtendedAdvertisingEvents "
               	        + maxExtendedAdvertisingEvents);
            }
            this.maxExtendedAdvertisingEvents = maxExtendedAdvertisingEvents;
            return this;
        }

        /**
         * Build the {@link AdvertisingSetDuration} object.
         */
        public AdvertisingSetDuration build() {
            return new AdvertisingSetDuration(duration, maxExtendedAdvertisingEvents);
        }
    }
}