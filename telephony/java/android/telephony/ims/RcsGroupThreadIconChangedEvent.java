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
import android.net.Uri;
import android.os.Parcel;

import com.android.internal.annotations.VisibleForTesting;

/**
 * An event that indicates an {@link RcsGroupThread}'s icon was changed. Please see R6-2-5 - GSMA
 * RCC.71 (RCS Universal Profile Service Definition Document)
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsGroupThreadIconChangedEvent extends RcsGroupThreadEvent {
    private final Uri mOldIcon;
    private final Uri mNewIcon;

    /**
     * @hide
     */
    public RcsGroupThreadIconChangedEvent(int id, long timestamp, int rcsGroupThreadId,
            int originatingParticipantId, Uri oldIcon, Uri newIcon) {
        super(id, timestamp, rcsGroupThreadId, originatingParticipantId);
        mOldIcon = oldIcon;
        mNewIcon = newIcon;
    }

    /**
     * Creates a new {@link Builder} to create an {@link RcsGroupThreadIconChangedEvent}
     * @param rcsGroupThread The {@link RcsGroupThread} that had its icon changed.
     * @param rcsParticipant The {@link RcsParticipant} that changed the icon.
     * @return an instance of {@link Builder}
     */
    public static Builder builder(@NonNull RcsGroupThread rcsGroupThread,
            @NonNull RcsParticipant rcsParticipant) {
        return new Builder(rcsGroupThread, rcsParticipant);
    }

    /**
     * @return Returns the {@link Uri} to the icon of the {@link RcsGroupThread} before this
     * {@link RcsGroupThreadIconChangedEvent} occured.
     */
    @Nullable
    public Uri getOldIcon() {
        return mOldIcon;
    }

    /**
     * @return Returns the {@link Uri} to the icon of the {@link RcsGroupThread} after this
     * {@link RcsGroupThreadIconChangedEvent} occured.
     */
    @Nullable
    public Uri getNewIcon() {
        return mNewIcon;
    }

    /**
     * A builder object to create and persist instances of {@link RcsGroupThreadIconChangedEvent}
     */
    public static class Builder extends RcsGroupThreadEvent.Builder<Builder> {
        private Uri mBuilderOldIcon;
        private Uri mBuilderNewIcon;

        /**
         * @hide
         */
        Builder(@NonNull RcsGroupThread rcsGroupThread,
                @NonNull RcsParticipant originatingParticipant) {
            super(rcsGroupThread, originatingParticipant);
        }

        /**
         * Sets the {@link Uri} to the old icon the associated {@link RcsGroupThread} should have
         * before this event occcured.
         * @param oldIcon The {@link Uri} to the icon to be set.
         * @return The same instance of {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder setOldIcon(Uri oldIcon) {
            mBuilderOldIcon = oldIcon;
            return this;
        }

        /**
         * Sets the {@link Uri} to the new icon the associated {@link RcsGroupThread} should have
         * after this event occcured.
         * @param newIcon The {@link Uri} to the icon to be set.
         * @return The same instance of {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder setNewIcon(Uri newIcon) {
            mBuilderNewIcon = newIcon;
            return this;
        }

        /**
         * Builds a new {@link RcsGroupThreadIconChangedEvent} and persists it into storage.
         * @return The new instance of {@link RcsGroupThreadIconChangedEvent}.
         * @throws RcsMessageStoreException if the event could not be persisted into storage.
         */
        @Override
        @WorkerThread
        @NonNull
        public RcsGroupThreadIconChangedEvent buildAndSave() throws RcsMessageStoreException {
            int id = RcsControllerCall.call(iRcs -> iRcs.createGroupThreadIconChangedEvent(
                    mBuilderTimestamp, mBuilderRcsGroupThread.getThreadId(),
                    mOriginatingParticipant.getId(), mBuilderOldIcon, mBuilderNewIcon));

            return new RcsGroupThreadIconChangedEvent(id, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderOldIcon, mBuilderNewIcon);
        }

        /**
         * @hide
         */
        @VisibleForTesting
        public RcsGroupThreadIconChangedEvent buildForTest() {
            return new RcsGroupThreadIconChangedEvent(0, mBuilderTimestamp,
                    mBuilderRcsGroupThread.getThreadId(), mOriginatingParticipant.getId(),
                    mBuilderOldIcon, mBuilderNewIcon);
        }

        @Override
        Builder self() {
            return this;
        }
    }

    public static final Creator<RcsGroupThreadIconChangedEvent> CREATOR =
            new Creator<RcsGroupThreadIconChangedEvent>() {
                @Override
                public RcsGroupThreadIconChangedEvent createFromParcel(Parcel in) {
                    return new RcsGroupThreadIconChangedEvent(in);
                }

                @Override
                public RcsGroupThreadIconChangedEvent[] newArray(int size) {
                    return new RcsGroupThreadIconChangedEvent[size];
                }
            };

    protected RcsGroupThreadIconChangedEvent(Parcel in) {
        super(in);
        mOldIcon = in.readParcelable(Uri.class.getClassLoader());
        mNewIcon = in.readParcelable(Uri.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mOldIcon, flags);
        dest.writeParcelable(mNewIcon, flags);
    }
}
