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
 * A Parcelable class Cell-Broadcast downloadable file information.
 */
public class FileServiceInfo extends ServiceInfo implements Parcelable {
    /**
     * Get the service class for this FileService.
     */
    public String getServiceClass();

    /**
     * Gets the list of files that could be downloaded for this service.
     */
    public List<FileInfo> getFileInfos();

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
