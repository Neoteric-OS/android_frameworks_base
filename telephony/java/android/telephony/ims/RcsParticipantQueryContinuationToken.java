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
 * {@link RcsMessageStore#getRcsParticipants(RcsParticipantQueryContinuationToken)}. Use this token
 * to break large queries into manageable chunks.
 *
 * @hide - TODO make this public. This class should not have any public methods however.
 */
public class RcsParticipantQueryContinuationToken implements RcsQueryContinuationToken {
    /**
     * @hide
     */
    public static final String PARTICIPANT_QUERY_CONTINUATION_TOKEN =
            "participant_query_continuation_token";

    // The raw query that was executed on the provider
    private final String mRawQuery;
    // The number of RcsParticipants that should be included in the result query
    private final int mLimit;
    // The offset to continue the next batch of RcsThreads from
    private int mOffset;

    /**
     * Constructor for RcsProvider to create new instances of this token.
     * @hide
     */
    public RcsParticipantQueryContinuationToken(String rawQuery, int limit, int offset) {
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

    @Override
    public String getKey() {
        return PARTICIPANT_QUERY_CONTINUATION_TOKEN;
    }

    protected RcsParticipantQueryContinuationToken(Parcel in) {
        mRawQuery = in.readString();
        mLimit = in.readInt();
        mOffset = in.readInt();
    }

    public static final Creator<RcsParticipantQueryContinuationToken> CREATOR =
            new Creator<RcsParticipantQueryContinuationToken>() {
                @Override
                public RcsParticipantQueryContinuationToken createFromParcel(Parcel in) {
                    return new RcsParticipantQueryContinuationToken(in);
                }

                @Override
                public RcsParticipantQueryContinuationToken[] newArray(int size) {
                    return new RcsParticipantQueryContinuationToken[size];
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
