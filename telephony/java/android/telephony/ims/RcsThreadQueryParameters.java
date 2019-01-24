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
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The parameters to pass into {@link RcsMessageStore#getRcsThreads(RcsThreadQueryParameters)} in
 * order to select a subset of {@link RcsThread}s present in the message store.
 *
 * @hide TODO - make the Builder and builder() public. The rest should stay internal only.
 */
public class RcsThreadQueryParameters implements Parcelable {
    /**
     * Flag to be used with {@link Builder#setThreadType(int)} to make
     * {@link RcsMessageStore#getRcsThreads(RcsThreadQueryParameters)} return both
     * {@link Rcs1To1Thread} and {@link RcsGroupThread}s.
     */
    public static final @ThreadType int ALL_THREADS = 0;

    /**
     * Flag to be used with {@link Builder#setThreadType(int)} to make
     * {@link RcsMessageStore#getRcsThreads(RcsThreadQueryParameters)} return only
     * {@link RcsGroupThread}s.
     */
    public static final @ThreadType int ONLY_GROUP_THREADS = 1;

    /**
     * Flag to be used with {@link Builder#setThreadType(int)} to make
     * {@link RcsMessageStore#getRcsThreads(RcsThreadQueryParameters)} return only
     * {@link Rcs1To1Thread}s.
     */
    public static final @ThreadType int ONLY_1_TO_1_THREADS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ALL_THREADS, ONLY_GROUP_THREADS, ONLY_1_TO_1_THREADS})
    public @interface ThreadType {
    }

    // The type of threads to be filtered with the query
    private final @ThreadType int mThreadType;
    // The list of participants that are expected in the resulting threads
    private final List<Integer> mRcsParticipantIds;
    // The number of RcsThread's that should be returned with this query
    private final int mLimit;
    // The property which the result of the query should be sorted against
    private final @SortingProperty int mSortingProperty;
    // Whether the sorting should be done in ascending
    private final boolean mIsAscending;

    public static final @SortingProperty int THREAD_ID = 0;
    public static final @SortingProperty int TIMESTAMP = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({THREAD_ID, TIMESTAMP})
    public @interface SortingProperty {
    }

    /**
     * @hide
     */
    public static final String THREAD_QUERY_PARAMETERS_KEY = "thread_query_parameters";

    RcsThreadQueryParameters(@ThreadType int threadType, Set<RcsParticipant> participants,
            int limit, int sortingProperty, boolean isAscending) {
        mThreadType = threadType;
        mRcsParticipantIds = convertParticipantSetToIdList(participants);
        mLimit = limit;
        mSortingProperty = sortingProperty;
        mIsAscending = isAscending;
    }

    private static List<Integer> convertParticipantSetToIdList(Set<RcsParticipant> participants) {
        List<Integer> ids = new ArrayList<Integer>(participants.size());
        for (RcsParticipant participant : participants) {
            ids.add(participant.getId());
        }
        return ids;
    }

    /**
     * Returns a new {@link Builder} to build an {@link RcsThreadQueryParameters}.
     * TODO - make public
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the list of participant IDs.
     *
     * @hide
     */
    public List<Integer> getRcsParticipantsIds() {
        return Collections.unmodifiableList(mRcsParticipantIds);
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * which kind of threads should be queried
     *
     * @hide
     */
    public @ThreadType int getThreadType() {
        return mThreadType;
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
     * against which property the threads should be sorted.
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
     * A helper class to build the {@link RcsThreadQueryParameters}.
     */
    public static class Builder {
        private @ThreadType int mThreadType;
        private Set<RcsParticipant> mParticipants;
        private int mLimit = 100;
        private @SortingProperty int mSortingProperty;
        private boolean mIsAscending;

        /**
         * Package private constructor for {@link RcsThreadQueryParameters.Builder}. To obtain this,
         * {@link RcsThreadQueryParameters#builder()} needs to be called.
         *
         * @hide
         */
        Builder() {
            mParticipants = new HashSet<>();
        }

        /**
         * Limits the query to only return group threads.
         *
         * @param threadType Whether to limit the query result to group threads.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder setThreadType(@ThreadType int threadType) {
            mThreadType = threadType;
            return this;
        }

        /**
         * Limits the query to only return threads that contain the given participant.
         *
         * @param participant The participant that must be included in all of the returned threads.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder withParticipant(RcsParticipant participant) {
            mParticipants.add(participant);
            return this;
        }

        /**
         * Limits the query to only return threads that contain the given list of participants.
         *
         * @param participants An iterable list of participants that must be included in all of the
         *                     returned threads.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder withParticipants(Iterable<RcsParticipant> participants) {
            for (RcsParticipant participant : participants) {
                mParticipants.add(participant);
            }
            return this;
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
         * Sets the property where the results should be sorted against. Defaults to
         * {@link SortingProperty#THREAD_ID}
         *
         * @param sortingProperty whether to sort in ascending order or not
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
         * Builds the {@link RcsThreadQueryParameters} to use in
         * {@link RcsMessageStore#getRcsThreads(RcsThreadQueryParameters)}
         *
         * @return An instance of {@link RcsThreadQueryParameters} to use with the thread query.
         */
        public RcsThreadQueryParameters build() {
            return new RcsThreadQueryParameters(mThreadType, mParticipants, mLimit,
                    mSortingProperty, mIsAscending);
        }
    }

    /**
     * Parcelable boilerplate below.
     */
    protected RcsThreadQueryParameters(Parcel in) {
        mThreadType = in.readInt();
        mRcsParticipantIds = new ArrayList<>();
        in.readList(mRcsParticipantIds, Integer.class.getClassLoader());
        mLimit = in.readInt();
        mSortingProperty = in.readInt();
        mIsAscending = in.readByte() == 1;
    }

    public static final Creator<RcsThreadQueryParameters> CREATOR =
            new Creator<RcsThreadQueryParameters>() {
                @Override
                public RcsThreadQueryParameters createFromParcel(Parcel in) {
                    return new RcsThreadQueryParameters(in);
                }

                @Override
                public RcsThreadQueryParameters[] newArray(int size) {
                    return new RcsThreadQueryParameters[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mThreadType);
        dest.writeList(mRcsParticipantIds);
        dest.writeInt(mLimit);
        dest.writeInt(mSortingProperty);
        dest.writeByte((byte) (mIsAscending ? 1 : 0));
    }
}
