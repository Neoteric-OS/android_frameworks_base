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

import com.android.ims.RcsTypeIdPair;

import java.util.List;

/**
 * The result of a {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)}
 * call. This class allows getting the token for querying the next batch of messages in order to
 * prevent handling large amounts of data at once.
 *
 * @hide
 */
public class RcsMessageQueryResultValues implements Parcelable {
    // The token to continue the query to get the next batch of results
    protected RcsQueryContinuationToken mContinuationToken;
    // The message type and message ID pairs for all the messages in this query result
    protected List<RcsTypeIdPair> mMessageTypeIdPairs;

    protected RcsMessageQueryResultValues(Parcel in) {
        mContinuationToken = in.readParcelable(
                RcsQueryContinuationToken.class.getClassLoader());
        in.readTypedList(mMessageTypeIdPairs, RcsTypeIdPair.CREATOR);
    }

    public static final Creator<RcsMessageQueryResultValues> CREATOR =
            new Creator<RcsMessageQueryResultValues>() {
                @Override
                public RcsMessageQueryResultValues createFromParcel(Parcel in) {
                    return new RcsMessageQueryResultValues(in);
                }

                @Override
                public RcsMessageQueryResultValues[] newArray(int size) {
                    return new RcsMessageQueryResultValues[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mContinuationToken, flags);
        dest.writeTypedList(mMessageTypeIdPairs);
    }
}
