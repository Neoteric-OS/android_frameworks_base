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
import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.os.Parcel;

import com.android.internal.annotations.VisibleForTesting;

/**
 * An event that indicates an {@link RcsGroupThread}'s name was changed. Please see R6-2-5 - GSMA
 * RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsGroupThreadNameChangedEvent extends RcsGroupThreadEvent {
    private String mOldName;
    private String mNewName;

    /**
     * @hide
     */
    public RcsGroupThreadNameChangedEvent(int id, long timestamp, int rcsGroupThreadId,
            int originatingParticipantId, String oldName, String newName) {
        super(id, timestamp, rcsGroupThreadId, originatingParticipantId);
        mOldName = oldName;
        mNewName = newName;
    }

    /**
     * Creates a new {@link Builder} to create an {@link RcsGroupThreadNameChangedEvent}
     * @param rcsGroupThread The {@link RcsGroupThread} that had its name changed.
     * @param rcsParticipant The {@link RcsParticipant} that changed the name.
     * @return an instance of {@link Builder}
     */
    public static Builder builder(@NonNull RcsGroupThread rcsGroupThread,
            @NonNull RcsParticipant rcsParticipant) {
        return new Builder(rcsGroupThread, rcsParticipant);
    }

    /**
     * @return Returns the name of this {@link RcsGroupThread} before this
     * {@link RcsGroupThreadNameChangedEvent} happened.
     */
    @Nullable
    public String getOldName() {
        return mOldName;
    }

    /**
     * @return Returns the name of this {@link RcsGroupThread} after this
     * {@link RcsGroupThreadNameChangedEvent} happened.
     */
    @Nullable
    public String getNewName() {
        return mNewName;
    }

    /**
     * A builder object to create and persist instances of {@link RcsGroupThreadNameChangedEvent}
     */
    public static class Builder extends RcsGroupThreadEvent.Builder<Builder> {
        private String mBuilderOldName;
        private String mBuilderNewName;

        /**
         * @hide
         */
        Builder(@NonNull RcsGroupThread rcsGroupThread,
                @NonNull RcsParticipant originatingParticipant) {
            super(rcsGroupThread, originatingParticipant);
        }

        /**
         * Sets the name that {@link RcsGroupThread} had before this event occurred.
         *
         * @param oldName The name to be set.
         * @return The same instance of {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder setOldName(String oldName) {
            mBuilderOldName = oldName;
            return this;
        }

        /**
         * Sets the name that {@link RcsGroupThread} had after this event occurred.
         * @param newName The name to be set.
         * @return The same instance of {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder setNewName(String newName) {
            mBuilderNewName = newName;
            return this;
        }

        /**
         * Builds a new {@link RcsGroupThreadNameChangedEvent} and persists it into storage.
         * @return The new instance of {@link RcsGroupThreadNameChangedEvent}.
         * @throws RcsMessageStoreException if the event could not be persisted into storage.
         */
        @Override
        @WorkerThread
        public RcsGroupThreadNameChangedEvent buildAndSave() throws RcsMessageStoreException {
            int id = RcsControllerCall.call(iRcs -> iRcs.createGroupThreadNameChangedEvent(
                    mBuilderTimestamp, mBuilderRcsGroupThread.getThreadId(),
                    mOriginatingParticipant.getId(), mBuilderOldName, mBuilderNewName));

            return new RcsGroupThreadNameChangedEvent(id, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderOldName, mBuilderNewName);
        }

        /**
         * @hide
         */
        @VisibleForTesting
        public RcsGroupThreadNameChangedEvent buildForTest() {
            return new RcsGroupThreadNameChangedEvent(0, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderOldName, mBuilderNewName);
        }

        @Override
        Builder self() {
            return this;
        }
    }

    public static final Creator<RcsGroupThreadNameChangedEvent> CREATOR =
            new Creator<RcsGroupThreadNameChangedEvent>() {
                @Override
                public RcsGroupThreadNameChangedEvent createFromParcel(Parcel in) {
                    return new RcsGroupThreadNameChangedEvent(in);
                }

                @Override
                public RcsGroupThreadNameChangedEvent[] newArray(int size) {
                    return new RcsGroupThreadNameChangedEvent[size];
                }
            };

    protected RcsGroupThreadNameChangedEvent(Parcel in) {
        super(in);
        mOldName = in.readString();
        mNewName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mOldName);
        dest.writeString(mNewName);
    }
}
