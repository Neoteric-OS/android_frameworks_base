/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.LinkedList;
import java.util.List;

/**
 * Contains the raw data backing an RCS message store query.
 *
 * @param <T> the type of the row
 * @hide - used only for internal communication with the ircs service
 */
public class RcsQueryResultDescriptor<T> implements Parcelable {
    private final RcsQueryContinuationToken mContinuationToken;
    private final List<T> mRows;

    public RcsQueryResultDescriptor(
            RcsQueryContinuationToken continuationToken,
            List<T> rows) {
        mContinuationToken = continuationToken;
        mRows = rows;
    }

    public RcsQueryContinuationToken getContinuationToken() {
        return mContinuationToken;
    }

    public List<T> getRows() {
        return mRows;
    }

    protected RcsQueryResultDescriptor(Parcel in) {
        mContinuationToken = in.readParcelable(RcsQueryContinuationToken.class.getClassLoader());
        mRows = new LinkedList<>();
        in.readList(mRows, null);
    }

    public static final Creator<RcsQueryResultDescriptor> CREATOR =
            new Creator<RcsQueryResultDescriptor>() {
        @Override
        public RcsQueryResultDescriptor createFromParcel(Parcel in) {
            return new RcsQueryResultDescriptor(in);
        }

        @Override
        public RcsQueryResultDescriptor[] newArray(int size) {
            return new RcsQueryResultDescriptor[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mContinuationToken, flags);
        dest.writeList(mRows);
    }
}
