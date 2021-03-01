/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com.
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

package android.bluetooth;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * @hide
 */
@SystemApi
public final class BluetoothMcsSearchRequest implements Parcelable {
    private int mType;
    private String mStringArg;

    /**
     * Media search request types definition
     * @hide
     */
    @SystemApi
    public final static class Types {
        private Types() {
            // not called
        }
        public static final int TRACK_NAME = 0x01;
        public static final int ARTIST_NAME = 0x02;
        public static final int ALBUM_NAME = 0x03;
        public static final int GROUP_NAME = 0x04;
        public static final int EARLIEST_YEAR = 0x05;
        public static final int LATEST_YEAR = 0x06;
        public static final int GENRE = 0x07;
        public static final int ONLY_TRACKS = 0x08;
        public static final int ONLY_GROUPS = 0x09;
    }

    /**
     * Media search request results definition
     * @hide
     */
    public final static class Results {
        private Results() {
            // not called
        }
        public static final int SUCCESS = 0x01;
        public static final int FAILURE = 0x02;
    }

    /**
     * Media search request constructor
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     *
     * @param in search request parcel
     */
    BluetoothMcsSearchRequest(@NonNull Parcel in) {
        mType = in.readInt();
        switch (mType) {
            case Types.TRACK_NAME:
            case Types.ARTIST_NAME:
            case Types.ALBUM_NAME:
            case Types.GROUP_NAME:
            case Types.EARLIEST_YEAR:
            case Types.LATEST_YEAR:
            case Types.GENRE:
                mStringArg = in.readString();
                break;
            case Types.ONLY_TRACKS:
            case Types.ONLY_GROUPS:
            default:
                mStringArg = null;
                break;
        }
    }

    /**
     * Media search request type getter
     *
     * @return Search request type
     * @hide
     */
    @SystemApi
    public int getType() {
        return mType;
    }

    /**
     * Media search request string argument getter
     *
     * @return search request argument
     * @hide
     */
    @SystemApi
    public @NonNull String getStringArg() {
        return mStringArg;
    };

    /**
     * Media search request array creator
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} permission.
     */
    public static final @NonNull Creator<BluetoothMcsSearchRequest> CREATOR =
            new Parcelable.Creator<BluetoothMcsSearchRequest>() {
                public BluetoothMcsSearchRequest createFromParcel(@NonNull Parcel in) {
                    return new BluetoothMcsSearchRequest(in);
                }

                public BluetoothMcsSearchRequest[] newArray(int size) {
                    return new BluetoothMcsSearchRequest[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mType);
        if (mStringArg != null)
            dest.writeString(mStringArg);
    }
}
