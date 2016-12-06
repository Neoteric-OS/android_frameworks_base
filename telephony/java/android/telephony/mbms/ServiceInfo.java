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

package android.telephony.mbms;

/**
 * A Parcelable class with Cell-Broadcast service information.
 */
public class ServiceInfo implements Parcelable {

    /**
     * User displayable names listed by language.  Unmodifiable.
     */
    final Map<Locale, String> names;

    /**
     * The class name for this service - used to catagorize and filter
     */
    final String className;

    /**
     * The language for this service content
     */
    final Locale locale;

    /**
     * The carrier's identifier for the service.
     */
    final String serviceId;

    /**
     * The start time indicating when this service will be available.
     */
    final Date sessionStartTime;

    /**
     * The end time indicating when this sesion stops being available.
     */
    final Date sessionEndTime;

    public static final Parcelable.Creator<FileServiceInfo> CREATOR =
            new Parcelable.Creator<FileServiceInfo>() {
        @Override
        public SubscriptionInfo createFromParcel(Parcel source) {
            return new FileServiceInfo();
        }

        @Override
        public FileServiceInfo[] newArray(int size) {
            return new FileServiceInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
