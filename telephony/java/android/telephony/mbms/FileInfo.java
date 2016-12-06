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
public class FileInfo implements Parcelable {

    /**
     * The URI into the carriers infrastructure which points to this file.
     * This is used internally but is also one of the few pieces of data about the content that is
     * exposed and may be needed for disambiguation by the application.
     */
    final Uri uri;

    /**
     * The mime type of the content.
     */
    final String mimeType;

    /**
     * The size of the file in bytes.
     */
    final long size;

    /**
     * The MD5 hash of the file.
     */
    final byte md5Hash[16];

    /**
     * Gets the parent service for this file.
     */
    public FileServiceInfo getFileServiceInfo();

    public static final Parcelable.Creator<FileInfo> CREATOR =
            new Parcelable.Creator<FileInfo>() {
        @Override
        public SubscriptionInfo createFromParcel(Parcel source) {
            return new FileInfo();
        }

        @Override
        public FileInfo[] newArray(int size) {
            return new FileInfo[size];
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
