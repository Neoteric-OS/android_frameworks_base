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

/**
 * A continuation token to provide for
 * {@link RcsMessageStore#getRcsEvents(RcsEventQueryContinuationToken)}. Use this token to
 * break large queries into manageable chunks.
 *
 * @hide - TODO make this public. This class should not have any public methods however.
 */
public class RcsEventQueryContinuationToken implements RcsQueryContinuationToken {
    /**
     * @hide
     */
    public static final String EVENT_QUERY_CONTINUATION_TOKEN =
            "event_query_continuation_token";

    // The raw query string for the initial query
    private final String mRawQuery;
    // The number of results that is returned with each query
    private final int mLimit;
    // The offset value that this query should start the query from
    private int mOffset;

    /**
     * Constructor for RcsProvider to create new instances of this token.
     *
     * @hide
     */
    public RcsEventQueryContinuationToken(String rawQuery, int limit, int offset) {
        mRawQuery = rawQuery;
        mLimit = limit;
        mOffset = offset;
    }

    /**
     * @hide
     */
    public String getRawQuery() {
        return mRawQuery;
    }

    /**
     * @hide
     */
    public int getOffset() {
        return mOffset;
    }

    /**
     * @hide
     */
    public void incrementOffset() {
        mOffset += mLimit;
    }

    /**
     * @hide
     */
    @Override
    public String getKey() {
        return EVENT_QUERY_CONTINUATION_TOKEN;
    }

    protected RcsEventQueryContinuationToken(Parcel in) {
        mRawQuery = in.readString();
        mLimit = in.readInt();
        mOffset = in.readInt();
    }

    public static final Creator<RcsEventQueryContinuationToken> CREATOR =
            new Creator<RcsEventQueryContinuationToken>() {
                @Override
                public RcsEventQueryContinuationToken createFromParcel(Parcel in) {
                    return new RcsEventQueryContinuationToken(in);
                }

                @Override
                public RcsEventQueryContinuationToken[] newArray(int size) {
                    return new RcsEventQueryContinuationToken[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRawQuery);
        dest.writeInt(mLimit);
        dest.writeInt(mOffset);
    }
}

