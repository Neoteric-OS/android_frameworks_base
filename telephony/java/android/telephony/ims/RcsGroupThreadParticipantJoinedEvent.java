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

import android.annotation.CheckResult;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.os.Parcel;

import com.android.internal.annotations.VisibleForTesting;

/**
 * An event that indicates an RCS participant has joined an {@link RcsThread}. Please see US6-3 -
 * GSMA RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsGroupThreadParticipantJoinedEvent extends RcsGroupThreadEvent {
    private int mJoinedParticipantId;

    /**
     * @hide
     */
    public RcsGroupThreadParticipantJoinedEvent(int id, long timestamp,
            int rcsGroupThreadId, int originatingParticipantId, int rcsParticipantId) {
        super(id, timestamp, rcsGroupThreadId, originatingParticipantId);
        mJoinedParticipantId = rcsParticipantId;
    }

    /**
     * Creates a new {@link Builder} to create an {@link RcsGroupThreadParticipantJoinedEvent}
     *
     * @param rcsGroupThread The {@link RcsGroupThread} that had a new participant joined.
     * @param rcsParticipant The {@link RcsParticipant} that added the new participant.
     * @return an instance of {@link Builder}
     */
    public static Builder builder(@NonNull RcsGroupThread rcsGroupThread,
            @NonNull RcsParticipant rcsParticipant) {
        return new Builder(rcsGroupThread, rcsParticipant);
    }

    /**
     * @return Returns the {@link RcsParticipant} that joined the associated {@link RcsGroupThread}
     */
    public RcsParticipant getJoinedParticipant() {
        return new RcsParticipant(mJoinedParticipantId);
    }

    /**
     * A builder object to create and persist instances of
     * {@link RcsGroupThreadParticipantJoinedEvent}
     */
    public static class Builder extends RcsGroupThreadEvent.Builder<Builder> {
        private RcsParticipant mBuilderJoinedRcsParticipant;

        /**
         * @hide
         */
        Builder(@NonNull RcsGroupThread rcsGroupThread,
                @NonNull RcsParticipant originatingParticipant) {
            super(rcsGroupThread, originatingParticipant);
        }

        /**
         * Sets the {@link RcsParticipant} that joined the associated {@link RcsGroupThread} after
         * this {@link RcsGroupThreadParticipantJoinedEvent} occured.
         *
         * @param rcsParticipant The {@link RcsParticipant} that joined the {@link RcsGroupThread}
         * @return The same instance of {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder setJoinedParticipant(RcsParticipant rcsParticipant) {
            mBuilderJoinedRcsParticipant = rcsParticipant;
            return self();
        }

        /**
         * Builds a new {@link RcsGroupThreadParticipantJoinedEvent} and persists it into storage.
         *
         * @return The new instance of {@link RcsGroupThreadParticipantJoinedEvent}.
         */
        @Override
        @WorkerThread
        public RcsGroupThreadParticipantJoinedEvent buildAndSave() throws RcsMessageStoreException {
            if (mBuilderJoinedRcsParticipant == null) {
                throw new RcsMessageStoreException("Cannot create "
                        + "RcsGroupThreadParticipantJoinedEvent without a joined participant");
            }

            int id = RcsControllerCall.call(
                    iRcs -> iRcs.createGroupThreadParticipantJoinedEvent(mBuilderTimestamp,
                            mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                            mBuilderJoinedRcsParticipant.getId()));

            return new RcsGroupThreadParticipantJoinedEvent(id, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderJoinedRcsParticipant.getId());
        }

        /**
         * @hide
         */
        @VisibleForTesting
        public RcsGroupThreadParticipantJoinedEvent buildForTest() {
            return new RcsGroupThreadParticipantJoinedEvent(0, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderJoinedRcsParticipant.getId());
        }

        @Override
        Builder self() {
            return this;
        }
    }

    public static final Creator<RcsGroupThreadParticipantJoinedEvent> CREATOR =
            new Creator<RcsGroupThreadParticipantJoinedEvent>() {
                @Override
                public RcsGroupThreadParticipantJoinedEvent createFromParcel(Parcel in) {
                    return new RcsGroupThreadParticipantJoinedEvent(in);
                }

                @Override
                public RcsGroupThreadParticipantJoinedEvent[] newArray(int size) {
                    return new RcsGroupThreadParticipantJoinedEvent[size];
                }
            };

    protected RcsGroupThreadParticipantJoinedEvent(Parcel in) {
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
