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

/**
 * The parameters to pass into
 * {@link RcsMessageStore#getRcsParticipants(RcsParticipantQueryParameters)} in order to select a
 * subset of {@link RcsThread}s present in the message store.
 *
 * @hide TODO - make the Builder and builder() public. The rest should stay internal only.
 */
public class RcsParticipantQueryParameters implements Parcelable {
    public static final @SortingProperty int PARTICIPANT_ID = 0;
    public static final @SortingProperty int ALIAS = 1;
    public static final @SortingProperty int CANONICAL_ADDRESS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PARTICIPANT_ID, ALIAS, CANONICAL_ADDRESS})
    public @interface SortingProperty {
    }

    // The SQL "like" statement to filter against participant aliases
    private String mAliasLike;
    // The SQL "like" statement to filter against canonical addresses
    private String mCanonicalAddressLike;
    // The property to sort the result against
    private @SortingProperty int mSortingProperty;
    // Whether to sort the result in ascending order
    private boolean mIsAscending;
    // The number of results to be returned from the query
    private int mLimit;
    // Used to limit the results to a participants of a single thread
    private int mThreadId;

    /**
     * @hide
     */
    public static final String PARTICIPANT_QUERY_PARAMETERS_KEY = "participant_query_parameters";

    RcsParticipantQueryParameters(int rcsThreadId, String aliasLike, String canonicalAddressLike,
            @SortingProperty int sortingProperty, boolean isAscending,
            int limit) {
        mThreadId = rcsThreadId;
        mAliasLike = aliasLike;
        mCanonicalAddressLike = canonicalAddressLike;
        mSortingProperty = sortingProperty;
        mIsAscending = isAscending;
        mLimit = limit;
    }

    /**
     * Returns a new {@link Builder} to build an {@link RcsParticipantQueryParameters} with.
     * TODO - make public
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to limit
     * the results to a single thread
     *
     * @hide
     */
    public int getThreadId() {
        return mThreadId;
    }


    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * how the aliases should be filtered.
     *
     * @hide
     */
    public String getAliasLike() {
        return mAliasLike;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * how the canonical addresses should be filtered.
     *
     * @hide
     */
    public String getCanonicalAddressLike() {
        return mCanonicalAddressLike;
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
     * against which property the participants should be sorted.
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
     * A helper class to build the {@link RcsParticipantQueryParameters}.
     */
    public static class Builder {
        private String mAliasLike;
        private String mCanonicalAddressLike;
        private @SortingProperty int mSortingProperty;
        private boolean mIsAscending;
        private int mLimit = 100;
        private int mThreadId;

        /**
         * Creates a new builder for {@link RcsParticipantQueryParameters} to be used in
         * {@link RcsMessageStore#getRcsParticipants(RcsParticipantQueryParameters)}
         *
         * @hide
         */
        public Builder() {
            // empty implementation
        }

        /**
         * Limits the resulting {@link RcsParticipant}s to only the given {@link RcsThread}
         *
         * @param rcsThread The thread that the participants should be searched in.
         * @return The same {@link Builder} to chain methods.
         */
        @CheckResult
        public Builder inThread(RcsThread rcsThread) {
            mThreadId = rcsThread.getThreadId();
            return this;
        }

        /**
         * Sets an SQL-inspired "like" clause to match with participant aliases. Using a percent
         * sign ('%') wildcard matches any sequence of zero or more characters. Using an underscore
         * ('_') wildcard matches any single character. Not using any wildcards would only perform a
         * string match.The input string is case-insensitive.
         *
         * The input "An%e" would match {@link RcsParticipant}s with names Anne, Annie, Antonie,
         * while the input "An_e" would only match Anne.
         *
         * @param likeClause The like clause to use for matching {@link RcsParticipant} aliases.
         * @return The same {@link Builder} to chain methods
         */
        @CheckResult
        public Builder setAliasLike(String likeClause) {
            mAliasLike = likeClause;
            return this;
        }

        /**
         * Sets an SQL-inspired "like" clause to match with participant addresses. Using a percent
         * sign ('%') wildcard matches any sequence of zero or more characters. Using an underscore
         * ('_') wildcard matches any single character. Not using any wildcards would only perform a
         * string match. The input string is case-insensitive.
         *
         * The input "+999%111" would match {@link RcsParticipant}s with addresses like "+9995111"
         * or "+99955555111", while the input "+999_111" would only match "+9995111".
         *
         * @param likeClause The like clause to use for matching {@link RcsParticipant} canonical
         *                   addresses.
         * @return The same {@link Builder} to chain methods
         */
        @CheckResult
        public Builder setCanonicalAddressLike(String likeClause) {
            mCanonicalAddressLike = likeClause;
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
         * {@link RcsParticipantQueryParameters.SortingProperty#PARTICIPANT_ID}
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
         * Builds the {@link RcsParticipantQueryParameters} to use in
         * {@link RcsMessageStore#getRcsParticipants(RcsParticipantQueryParameters)}
         *
         * @return An instance of {@link RcsParticipantQueryParameters} to use with the participant
         * query.
         */
        public RcsParticipantQueryParameters build() {
            return new RcsParticipantQueryParameters(mThreadId, mAliasLike, mCanonicalAddressLike,
                    mSortingProperty, mIsAscending, mLimit);
        }
    }

    /**
     * Parcelable boilerplate below.
     */
    protected RcsParticipantQueryParameters(Parcel in) {
        mAliasLike = in.readString();
        mCanonicalAddressLike = in.readString();
        mSortingProperty = in.readInt();
        mIsAscending = in.readByte() == 1;
        mLimit = in.readInt();
        mThreadId = in.readInt();
    }

    public static final Creator<RcsParticipantQueryParameters> CREATOR =
            new Creator<RcsParticipantQueryParameters>() {
                @Override
                public RcsParticipantQueryParameters createFromParcel(Parcel in) {
                    return new RcsParticipantQueryParameters(in);
                }

                @Override
                public RcsParticipantQueryParameters[] newArray(int size) {
                    return new RcsParticipantQueryParameters[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mAliasLike);
        dest.writeString(mCanonicalAddressLike);
        dest.writeInt(mSortingProperty);
        dest.writeByte((byte) (mIsAscending ? 1 : 0));
        dest.writeInt(mLimit);
        dest.writeInt(mThreadId);
    }

}
