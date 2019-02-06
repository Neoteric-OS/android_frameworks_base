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

import android.annotation.Nullable;
import android.os.Parcel;

/**
 * An event that indicates an {@link RcsParticipant}'s alias was changed. Please see US18-2 - GSMA
 * RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsParticipantAliasChangedEventDescriptor extends RcsEventDescriptor {
    // The ID of the participant that changed their alias
    protected int mParticipantId;
    // The new alias of the above participant
    protected String mNewAlias;

    /**
     * @hide - internal constructor for queries
     */
    public RcsParticipantAliasChangedEventDescriptor(long timestamp, int participantId,
            @Nullable String newAlias) {
        super(timestamp);
        mParticipantId = participantId;
        mNewAlias = newAlias;
    }

    @Override
    protected RcsParticipantAliasChangedEvent createRcsEvent() {
        return new RcsParticipantAliasChangedEvent(
                mTimestamp, new RcsParticipant(mParticipantId), mNewAlias);
    }

    public static final Creator<RcsParticipantAliasChangedEventDescriptor> CREATOR =
            new Creator<RcsParticipantAliasChangedEventDescriptor>() {
                @Override
                public RcsParticipantAliasChangedEventDescriptor createFromParcel(Parcel in) {
                    return new RcsParticipantAliasChangedEventDescriptor(in);
                }

                @Override
                public RcsParticipantAliasChangedEventDescriptor[] newArray(int size) {
                    return new RcsParticipantAliasChangedEventDescriptor[size];
                }
            };

    protected RcsParticipantAliasChangedEventDescriptor(Parcel in) {
        super(in);
        mNewAlias = in.readString();
        mParticipantId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mNewAlias);
        dest.writeInt(mParticipantId);
    }
}
