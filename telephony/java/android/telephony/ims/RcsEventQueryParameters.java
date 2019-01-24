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

import static android.provider.Telephony.RcsColumns.RcsParticipantEventColumns.PARTICIPANT_ALIAS_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.ICON_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.NAME_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_JOINED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_LEFT_EVENT_TYPE;

import android.annotation.CheckResult;
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.InvalidParameterException;

/**
 * The parameters to pass into
 * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} in order to select a
 * subset of {@link RcsEvent}s present in the message store.
 *
 * @hide TODO - make the Builder and builder() public. The rest should stay internal only.
 */
public class RcsEventQueryParameters implements Parcelable {
    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return all types of
     * {@link RcsEvent}s
     */
    public static final @EventType int ALL_EVENTS = -1;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return sub-types of
     * {@link RcsGroupThreadEvent}s
     */
    public static final @EventType int ALL_GROUP_THREAD_EVENTS = 0;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return only
     * {@link RcsParticipantAliasChangedEvent}s
     */
    public static final @EventType int PARTICIPANT_ALIAS_CHANGED_EVENT =
            PARTICIPANT_ALIAS_CHANGED_EVENT_TYPE;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return only
     * {@link RcsGroupThreadParticipantJoinedEvent}s
     */
    public static final @EventType int GROUP_THREAD_PARTICIPANT_JOINED_EVENT =
            PARTICIPANT_JOINED_EVENT_TYPE;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return only
     * {@link RcsGroupThreadParticipantLeftEvent}s
     */
    public static final @EventType int GROUP_THREAD_PARTICIPANT_LEFT_EVENT =
            PARTICIPANT_LEFT_EVENT_TYPE;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return only
     * {@link RcsGroupThreadNameChangedEvent}s
     */
    public static final @EventType int GROUP_THREAD_NAME_CHANGED_EVENT = NAME_CHANGED_EVENT_TYPE;

    /**
     * Flag to be used with {@link Builder#setEventType(int)} to make
     * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)} return only
     * {@link RcsGroupThreadIconChangedEvent}s
     */
    public static final @EventType int GROUP_THREAD_ICON_CHANGED_EVENT = ICON_CHANGED_EVENT_TYPE;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ALL_EVENTS, ALL_GROUP_THREAD_EVENTS, PARTICIPANT_ALIAS_CHANGED_EVENT,
            GROUP_THREAD_PARTICIPANT_JOINED_EVENT, GROUP_THREAD_PARTICIPANT_LEFT_EVENT,
            GROUP_THREAD_NAME_CHANGED_EVENT, GROUP_THREAD_ICON_CHANGED_EVENT})
    public @interface EventType {
    }

    public static final @SortingProperty int EVENT_ID = 0;
    public static final @SortingProperty int TIMESTAMP = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({EVENT_ID, TIMESTAMP})
    public @interface SortingProperty {
    }

    /**
     * @hide
     */
    public static final String EVENT_QUERY_PARAMETERS_KEY = "event_query_parameters";

    // Which types of events the results should be limited to
    private @EventType int mEventType;
    // The property which the results should be sorted against
    private int mSortingProperty;
    // Whether the results should be sorted in ascending order
    private boolean mIsAscending;
    // The number of results that should be returned with this query
    private int mLimit;

    RcsEventQueryParameters(@EventType int eventType, @SortingProperty int sortingProperty,
            boolean isAscending, int limit) {
        mEventType = eventType;
        mSortingProperty = sortingProperty;
        mIsAscending = isAscending;
        mLimit = limit;
    }

    /**
     * Returns a new {@link Builder} to build an {@link RcsEventQueryParameters} with.
     * TODO - make public
     *
     * @hide
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the type of events that should be returned.
     *
     * @hide
     */
    public @EventType int getEventType() {
        return mEventType;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the number of tuples the result query should be limited to.
     *
     * @hide
     */
    public int getLimit() {
        return mLimit;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * against which property the events should be sorted.
     *
     * @hide
     */
    public int getSortingProperty() {
        return mSortingProperty;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to
     * determine the sort order.
     *
     * @hide
     */
    public boolean isAscending() {
        return mIsAscending;
    }

    /**
     * A helper class to build the {@link RcsEventQueryParameters}.
     */
    public static class Builder {
        private @EventType int mEventType;
        private @SortingProperty int mSortingProperty;
        private boolean mIsAscending;
        private int mLimit = 100;

        /**
         * Creates a new builder for {@link RcsEventQueryParameters} to be used in
         * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)}
         *
         * @hide
         */
        Builder() {
            // empty implementation
        }

        /**
         * Desired number of threads to be returned from the query. Passing in 0 will return all
         * existing threads at once. The limit defaults to 100.
         *
         * @param limit The number to limit the query result to.
         * @return The same instance of the builder to chain parameters.
         * @throws InvalidParameterException If the given limit is negative.
         */
        @CheckResult
        public Builder limitResultsTo(int limit) throws InvalidParameterException {
            if (limit < 0) {
                throw new InvalidParameterException("The query limit must be non-negative");
            }

            mLimit = limit;
            return this;
        }

        /**
         * Sets the type of events to be returned from the query.
         *
         * @param eventType The type of event to be returned.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder setEventType(@EventType int eventType) {
            mEventType = eventType;
            return this;
        }

        /**
         * Sets the property where the results should be sorted against. Defaults to
         * {@link RcsEventQueryParameters.SortingProperty#EVENT_ID}
         *
         * @param sortingProperty against which property the results should be sorted
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder sortBy(@SortingProperty int sortingProperty) {
            mSortingProperty = sortingProperty;
            return this;
        }

        /**
         * Sets whether the results should be sorted ascending or descending
         *
         * @param isAscending whether the results should be sorted ascending
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder sortAscending(boolean isAscending) {
            mIsAscending = isAscending;
            return this;
        }

        /**
         * Builds the {@link RcsEventQueryParameters} to use in
         * {@link RcsMessageStore#getRcsEvents(RcsEventQueryParameters)}
         *
         * @return An instance of {@link RcsEventQueryParameters} to use with the event query.
         */
        public RcsEventQueryParameters build() {
            return new RcsEventQueryParameters(mEventType, mSortingProperty, mIsAscending, mLimit);
        }
    }

    protected RcsEventQueryParameters(Parcel in) {
        mEventType = in.readInt();
        mSortingProperty = in.readInt();
        mIsAscending = in.readBoolean();
        mLimit = in.readInt();
    }

    public static final Creator<RcsEventQueryParameters> CREATOR =
            new Creator<RcsEventQueryParameters>() {
                @Override
                public RcsEventQueryParameters createFromParcel(Parcel in) {
                    return new RcsEventQueryParameters(in);
                }

                @Override
                public RcsEventQueryParameters[] newArray(int size) {
                    return new RcsEventQueryParameters[size];
                }
            };


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
        dest.writeInt(mSortingProperty);
        dest.writeBoolean(mIsAscending);
        dest.writeInt(mLimit);
    }
}
