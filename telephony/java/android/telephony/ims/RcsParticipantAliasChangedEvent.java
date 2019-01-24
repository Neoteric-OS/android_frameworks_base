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

import android.annotation.CheckResult;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.os.Parcel;

import com.android.internal.annotations.VisibleForTesting;

/**
 * An event that indicates an {@link RcsParticipant}'s alias was changed. Please see US18-2 - GSMA
 * RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsParticipantAliasChangedEvent extends RcsEvent {
    // The ID of the participant that changed their alias
    private int mParticipantId;
    // The old alias of the above participant
    private String mOldAlias;
    // The new alias of the above participant
    private String mNewAlias;

    /**
     * @hide
     */
    public RcsParticipantAliasChangedEvent(int id, long timestamp, int participantId,
            String oldAlias, String newAlias) {
        super(id, timestamp);
        mParticipantId = participantId;
        mOldAlias = oldAlias;
        mNewAlias = newAlias;
    }

    /**
     * Returns a {@link Builder} to create an instance of {@link RcsParticipantAliasChangedEvent}
     * @param participant The participant whose name was changed
     * @return An instance of {@link Builder}
     */
    public static Builder builder(@NonNull RcsParticipant participant) {
        return new Builder(participant);
    }

    /**
     * @return Returns the {@link RcsParticipant} whose alias was changed.
     */
    @NonNull
    public RcsParticipant getParticipantId() {
        return new RcsParticipant(mParticipantId);
    }

    /**
     * @return Returns the alias of the associated {@link RcsParticipant} before this event happened
     */
    @Nullable
    public String getOldAlias() {
        return mOldAlias;
    }

    /**
     * @return Returns the alias of the associated {@link RcsParticipant} after this event happened
     */
    @Nullable
    public String getNewAlias() {
        return mNewAlias;
    }

    /**
     * A builder to create and persist instances of {@link RcsParticipantAliasChangedEvent}
     */
    public static class Builder extends RcsEvent.Builder<Builder> {
        private RcsParticipant mBuilderParticipant;
        private String mBuilderOldAlias;
        private String mBuilderNewAlias;

        /**
         * @hide
         */
        Builder(@NonNull RcsParticipant participant) {
            mBuilderParticipant = participant;
        }

        /**
         * Sets the alias of the associated {@link RcsParticipant} before this event happened
         *
         * @return The same instance of {@link Builder} to chain methods
         */
        @CheckResult
        public Builder setOldAlias(String alias) {
            mBuilderOldAlias = alias;
            return this;
        }

        /**
         * Sets the alias of the associated {@link RcsParticipant} after this event happened
         *
         * @return The same instance of {@link Builder} to chain methods
         */
        @CheckResult
        public Builder setNewAlias(String alias) {
            mBuilderNewAlias = alias;
            return this;
        }

        /**
         * Creates a new {@link RcsParticipantAliasChangedEvent} and persists into storage.
         *
         * @return The newly created instance of {@link RcsParticipantAliasChangedEvent}
         * @throws RcsMessageStoreException if the event could not be saved into storage.
         */
        @WorkerThread
        @NonNull
        public RcsParticipantAliasChangedEvent buildAndSave() throws RcsMessageStoreException {
            int id = RcsControllerCall.call(iRcs -> iRcs.createParticipantAliasChangedEvent(
                    mBuilderTimestamp, mBuilderParticipant.getId(), mBuilderOldAlias,
                    mBuilderNewAlias));

            return new RcsParticipantAliasChangedEvent(id, mBuilderTimestamp,
                    mBuilderParticipant.getId(), mBuilderOldAlias, mBuilderNewAlias);
        }

        /**
         * @hide
         */
        @VisibleForTesting
        public RcsParticipantAliasChangedEvent buildForTest() {
            return new RcsParticipantAliasChangedEvent(0, mBuilderTimestamp,
                    mBuilderParticipant.getId(), mBuilderOldAlias, mBuilderNewAlias);
        }

        @Override
        Builder self() {
            return this;
        }
    }

    public static final Creator<RcsParticipantAliasChangedEvent> CREATOR =
            new Creator<RcsParticipantAliasChangedEvent>() {
                @Override
                public RcsParticipantAliasChangedEvent createFromParcel(Parcel in) {
                    return new RcsParticipantAliasChangedEvent(in);
                }

                @Override
                public RcsParticipantAliasChangedEvent[] newArray(int size) {
                    return new RcsParticipantAliasChangedEvent[size];
                }
            };

    protected RcsParticipantAliasChangedEvent(Parcel in) {
        super(in);
        mOldAlias = in.readString();
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
        dest.writeString(mOldAlias);
        dest.writeString(mNewAlias);
        dest.writeInt(mParticipantId);
    }
}
