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
 * An event that indicates an RCS participant has left an {@link RcsThread}. Please see US6-23 -
 * GSMA RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsGroupThreadParticipantLeftEvent extends RcsGroupThreadEvent {
    private int mLeavingParticipantId;

    /**
     * @hide
     */
    public RcsGroupThreadParticipantLeftEvent(int id, long timestamp,
            int rcsGroupThreadId, int originatingParticipantId, int rcsParticipantId) {
        super(id, timestamp, rcsGroupThreadId, originatingParticipantId);
        mLeavingParticipantId = rcsParticipantId;
    }

    /**
     * Creates a new {@link Builder} to create an {@link RcsGroupThreadParticipantLeftEvent}
     *
     * @param rcsGroupThread The {@link RcsGroupThread} that had a participant leave.
     * @param rcsParticipant The {@link RcsParticipant} that added the new participant.
     * @return an instance of {@link Builder}
     */
    @NonNull
    public static Builder builder(@NonNull RcsGroupThread rcsGroupThread,
            @NonNull RcsParticipant rcsParticipant) {
        return new Builder(rcsGroupThread, rcsParticipant);
    }

    /**
     * @return Returns the {@link RcsParticipant} that left the associated {@link RcsGroupThread}
     * after this {@link RcsGroupThreadParticipantLeftEvent} happened.
     */
    @NonNull
    public RcsParticipant getLeavingParticipantId() {
        return new RcsParticipant(mLeavingParticipantId);
    }

    /**
     * A builder object to create and persist instances of
     * {@link RcsGroupThreadParticipantLeftEvent}
     */
    public static class Builder extends RcsGroupThreadEvent.Builder<Builder> {
        private RcsParticipant mBuilderLeavingRcsParticipant;

        /**
         * @hide
         */
        Builder(@NonNull RcsGroupThread rcsGroupThread,
                @NonNull RcsParticipant originatingParticipant) {
            super(rcsGroupThread, originatingParticipant);
        }

        /**
         * Sets the {@link RcsParticipant} that left the {@link RcsGroupThread} after this
         * {@link RcsGroupThreadParticipantLeftEvent} happened
         */
        @CheckResult
        public Builder setLeavingParticipant(RcsParticipant rcsParticipant) {
            mBuilderLeavingRcsParticipant = rcsParticipant;
            return this;
        }

        /**
         * Builds a new {@link RcsGroupThreadParticipantLeftEvent} and persists it into storage.
         *
         * @return The new instance of {@link RcsGroupThreadParticipantLeftEvent}.
         * @throws RcsMessageStoreException if the event could not be persisted into storage.
         */
        @Override
        @WorkerThread
        public RcsGroupThreadParticipantLeftEvent buildAndSave() throws RcsMessageStoreException {
            if (mBuilderLeavingRcsParticipant == null) {
                throw new RcsMessageStoreException("Cannot build an "
                        + "RcsGroupThreadParticipantLeftEvent without a leaving participant.");
            }

            int id = RcsControllerCall.call(iRcs -> iRcs.createGroupThreadParticipantLeftEvent(
                    mBuilderTimestamp, mBuilderRcsGroupThread.getThreadId(),
                    mOriginatingParticipant.getId(), mBuilderLeavingRcsParticipant.getId()));

            return new RcsGroupThreadParticipantLeftEvent(id, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderLeavingRcsParticipant.getId());
        }

        /**
         * @hide
         */
        @VisibleForTesting
        public RcsGroupThreadParticipantLeftEvent buildForTest() {
            return new RcsGroupThreadParticipantLeftEvent(0, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderLeavingRcsParticipant.getId());
        }

        @Override
        Builder self() {
            return this;
        }
    }

    public static final Creator<RcsGroupThreadParticipantLeftEvent> CREATOR =
            new Creator<RcsGroupThreadParticipantLeftEvent>() {
                @Override
                public RcsGroupThreadParticipantLeftEvent createFromParcel(Parcel in) {
                    return new RcsGroupThreadParticipantLeftEvent(in);
                }

                @Override
                public RcsGroupThreadParticipantLeftEvent[] newArray(int size) {
                    return new RcsGroupThreadParticipantLeftEvent[size];
                }
            };

    protected RcsGroupThreadParticipantLeftEvent(Parcel in) {
        super(in);
        mLeavingParticipantId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mLeavingParticipantId);
    }
}
