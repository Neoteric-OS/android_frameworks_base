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

import static android.telephony.ims.RcsMessageQueryParams.MESSAGE_TYPE_INCOMING;

import android.os.Parcel;
import android.os.Parcelable;

import com.android.ims.RcsTypeIdPair;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @hide - used only for internal communication with the ircs service
 */
public class RcsMessageQueryResultDescriptor implements Parcelable {
    // The token to continue the query to get the next batch of results
    RcsQueryContinuationToken mContinuationToken;
    // The message type and message ID pairs for all the messages in this query result
    List<RcsTypeIdPair> mMessageTypeIdPairs;

    public RcsMessageQueryResultDescriptor(
            RcsQueryContinuationToken continuationToken,
            List<RcsTypeIdPair> messageTypeIdPairs) {
        mContinuationToken = continuationToken;
        mMessageTypeIdPairs = messageTypeIdPairs;
    }

    RcsMessageQueryResult createRcsMessageQueryResult(RcsControllerCall rcsControllerCall) {
        List<RcsMessage> messages = mMessageTypeIdPairs.stream()
                .map(typeIdPair -> typeIdPair.getType() == MESSAGE_TYPE_INCOMING
                        ? new RcsIncomingMessage(rcsControllerCall, typeIdPair.getId())
                        : new RcsOutgoingMessage(rcsControllerCall, typeIdPair.getId()))
                .collect(Collectors.toList());

        return new RcsMessageQueryResult(mContinuationToken, messages);
    }

    private RcsMessageQueryResultDescriptor(Parcel in) {
        mContinuationToken = in.readParcelable(
                RcsQueryContinuationToken.class.getClassLoader());
        in.readTypedList(mMessageTypeIdPairs, RcsTypeIdPair.CREATOR);
    }

    public static final Creator<RcsMessageQueryResultDescriptor> CREATOR =
            new Creator<RcsMessageQueryResultDescriptor>() {
                @Override
                public RcsMessageQueryResultDescriptor createFromParcel(Parcel in) {
                    return new RcsMessageQueryResultDescriptor(in);
                }

                @Override
                public RcsMessageQueryResultDescriptor[] newArray(int size) {
                    return new RcsMessageQueryResultDescriptor[size];
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
