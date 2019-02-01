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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @hide
 */
public final class RcsParticipantQueryResultDescriptor implements Parcelable{
    private final RcsQueryContinuationToken mContinuationToken;
    private final List<Integer> mParticipantIds;

    public RcsParticipantQueryResultDescriptor(
            RcsQueryContinuationToken continuationToken,
            List<Integer> participantIds) {
        mContinuationToken = continuationToken;
        mParticipantIds = participantIds;
    }

    RcsParticipantQueryResult createRcsParticipantQueryResult(RcsControllerCall rcsControllerCall) {
        List<RcsParticipant> participantList = mParticipantIds.stream()
                .map(participantId -> new RcsParticipant(rcsControllerCall, participantId))
                .collect(Collectors.toList());

        return new RcsParticipantQueryResult(mContinuationToken, participantList);
    }

    private RcsParticipantQueryResultDescriptor(Parcel in) {
        mContinuationToken = in.readParcelable(RcsQueryContinuationToken.class.getClassLoader());
        mParticipantIds = new ArrayList<>();
        in.readList(mParticipantIds, Integer.class.getClassLoader());
    }

    public static final Parcelable.Creator<RcsParticipantQueryResultDescriptor> CREATOR =
            new Parcelable.Creator<RcsParticipantQueryResultDescriptor>() {
                @Override
                public RcsParticipantQueryResultDescriptor createFromParcel(Parcel in) {
                    return new RcsParticipantQueryResultDescriptor(in);
                }

                @Override
                public RcsParticipantQueryResultDescriptor[] newArray(int size) {
                    return new RcsParticipantQueryResultDescriptor[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mContinuationToken, flags);
        dest.writeList(mParticipantIds);
    }
}
