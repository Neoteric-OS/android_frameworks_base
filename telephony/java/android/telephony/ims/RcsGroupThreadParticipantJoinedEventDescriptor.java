/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * An event that indicates an RCS participant has joined an {@link RcsThread}. Please see US6-3 -
 * GSMA RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsGroupThreadParticipantJoinedEventDescriptor extends RcsGroupThreadEventDescriptor {
    private int mJoinedParticipantId;

    /**
     * @hide - internal constructor for queries
     */
    public RcsGroupThreadParticipantJoinedEventDescriptor(long timestamp, int rcsGroupThreadId,
            int originatingParticipantId, int joinedParticipantId) {
        super(timestamp, rcsGroupThreadId, originatingParticipantId);
        mJoinedParticipantId = joinedParticipantId;
    }

    @Override
    protected RcsGroupThreadParticipantJoinedEvent createRcsEvent() {
        return new RcsGroupThreadParticipantJoinedEvent(
                mTimestamp,
                new RcsGroupThread(mRcsGroupThreadId),
                new RcsParticipant(mOriginatingParticipantId),
                new RcsParticipant(mJoinedParticipantId));
    }

    public static final Creator<RcsGroupThreadParticipantJoinedEventDescriptor> CREATOR =
            new Creator<RcsGroupThreadParticipantJoinedEventDescriptor>() {
                @Override
                public RcsGroupThreadParticipantJoinedEventDescriptor createFromParcel(Parcel in) {
                    return new RcsGroupThreadParticipantJoinedEventDescriptor(in);
                }

                @Override
                public RcsGroupThreadParticipantJoinedEventDescriptor[] newArray(int size) {
                    return new RcsGroupThreadParticipantJoinedEventDescriptor[size];
                }
            };

    protected RcsGroupThreadParticipantJoinedEventDescriptor(Parcel in) {
        super(in);
        mJoinedParticipantId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mJoinedParticipantId);
    }
}
