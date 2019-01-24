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
 * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} in order to select a
 * subset of {@link RcsMessage}s present in the message store.
 *
 * @hide TODO - make the Builder and builder() public. The rest should stay internal only.
 */
public class RcsMessageQueryParameters implements Parcelable {
    /**
     * @hide
     */
    public static final int THREAD_ID_NOT_SET = -1;

    public static final @SortingProperty int MESSAGE_ID = 0;
    public static final @SortingProperty int TIMESTAMP = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MESSAGE_ID, TIMESTAMP})
    public @interface SortingProperty {
    }

    /**
     * Flag to be used with {@link Builder#setMessageType(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return both
     * {@link RcsIncomingMessage} and {@link RcsOutgoingMessage}s.
     */
    public static final @MessageType int INCOMING_AND_OUTGOING = 0;

    /**
     * Flag to be used with {@link Builder#setMessageType(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return only
     * {@link RcsIncomingMessage}s.
     */
    public static final @MessageType int INCOMING_ONLY = 1;

    /**
     * Flag to be used with {@link Builder#setMessageType(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return only
     * {@link RcsOutgoingMessage}s.
     */
    public static final @MessageType int OUTGOING_ONLY = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({INCOMING_AND_OUTGOING, INCOMING_ONLY, OUTGOING_ONLY})
    public @interface MessageType {
    }

    /**
     * Flag to be used with {@link Builder#setFileTransferPresence(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return {@link RcsMessage}s
     * regardless of whether they have an {@link RcsFileTransferPart} attached or not.
     */
    public static final @FileTransferPresence int ALL_MESSAGES = 0;

    /**
     * Flag to be used with {@link Builder#setFileTransferPresence(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return {@link RcsMessage}s
     * that have an {@link RcsFileTransferPart} attached.
     */
    public static final @FileTransferPresence int FILE_TRANSFERS_ONLY = 1;

    /**
     * Flag to be used with {@link Builder#setFileTransferPresence(int)} to make
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)} return {@link RcsMessage}s
     * that don't have an {@link RcsFileTransferPart} attached.
     */
    public static final @FileTransferPresence int NO_FILE_TRANSFERS = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ALL_MESSAGES, FILE_TRANSFERS_ONLY, NO_FILE_TRANSFERS})
    public @interface FileTransferPresence {
    }

    /**
     * @hide
     */
    public static final String MESSAGE_QUERY_PARAMETERS_KEY = "message_query_parameters";

    // Whether the result should be filtered against incoming or outgoing messages
    private @MessageType int mMessageType;
    // Whether the result should have file transfer messages attached or not
    private @FileTransferPresence int mFileTransferPresence;
    // The SQL "Like" clause to filter messages
    private String mMessageLike;
    // The property the messages should be sorted against
    private @SortingProperty int mSortingProperty;
    // Whether the messages should be sorted in ascending order
    private boolean mIsAscending;
    // The number of results that should be returned with this query
    private int mLimit;
    // The thread that the results should be limited to
    private int mThreadId;

    RcsMessageQueryParameters(@MessageType int messageType,
            @FileTransferPresence int fileTransferPresence, String messageLike, int threadId,
            @SortingProperty int sortingProperty, boolean isAscending,
            int limit) {
        mMessageType = messageType;
        mFileTransferPresence = fileTransferPresence;
        mMessageLike = messageLike;
        mSortingProperty = sortingProperty;
        mIsAscending = isAscending;
        mLimit = limit;
        mThreadId = threadId;
    }

    /**
     * Returns a new {@link Builder} to build an {@link RcsMessageQueryParameters} with.
     * TODO - make public
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the type of messages should be returned.
     *
     * @hide
     */
    public @MessageType int getMessageType() {
        return mMessageType;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * whether file transfers should be included in the result.
     *
     * @hide
     */
    public int getFileTransferPresence() {
        return mFileTransferPresence;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the filter we should use on message texts.
     *
     * @hide
     */
    public String getMessageLike() {
        return mMessageLike;
    }

    /**
     * This is used in {@link com.android.internal.telephony.ims.RcsMessageStoreController} to get
     * the thread that the result query should be limited to.
     *
     * @hide
     */
    public int getThreadId() {
        return mThreadId;
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
     * against which property the messages should be sorted.
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
     * A helper class to build the {@link RcsMessageQueryParameters}.
     */
    public static class Builder {
        private @SortingProperty int mSortingProperty;
        private @MessageType int mMessageType;
        private @FileTransferPresence int mFileTransferPresence;
        private String mMessageLike;
        private boolean mIsAscending;
        private int mLimit = 100;
        private int mThreadId = THREAD_ID_NOT_SET;

        /**
         * Creates a new builder for {@link RcsMessageQueryParameters} to be used in
         * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)}
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
         * Sets the type of messages to be returned from the query.
         *
         * @param messageType The type of message to be returned.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder setMessageType(@MessageType int messageType) {
            mMessageType = messageType;
            return this;
        }

        /**
         * Sets whether file transfer messages should be included in the query result or not.
         *
         * @param fileTransferPresence Whether file transfers should be included in the result
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder setFileTransferPresence(@FileTransferPresence int fileTransferPresence) {
            mFileTransferPresence = fileTransferPresence;
            return this;
        }

        /**
         * Sets an SQL-inspired "like" clause to match with messages. Using a percent sign ('%')
         * wildcard matches any sequence of zero or more characters. Using an underscore ('_')
         * wildcard matches any single character. Not using any wildcards would only perform a
         * string match. The input string is case-insensitive.
         *
         * The input "Wh%" would match messages "who", "where" and "what", while the input "Wh_"
         * would only match "who"
         *
         * @param messageLike The "like" clause for matching {@link RcsMessage}s.
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder setMessageLike(String messageLike) {
            mMessageLike = messageLike;
            return this;
        }

        /**
         * Sets the property where the results should be sorted against. Defaults to
         * {@link RcsMessageQueryParameters.SortingProperty#MESSAGE_ID}
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
         * Limits the results to the given thread.
         *
         * @param thread the {@link RcsThread} that results should be limited to. If set to
         *               {@code null}, messages on all threads will be queried
         * @return The same instance of the builder to chain parameters.
         */
        @CheckResult
        public Builder limitToThread(RcsThread thread) {
            if (thread == null) {
                mThreadId = THREAD_ID_NOT_SET;
            } else {
                mThreadId = thread.getThreadId();
            }
            return this;
        }

        /**
         * Builds the {@link RcsMessageQueryParameters} to use in
         * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)}
         *
         * @return An instance of {@link RcsMessageQueryParameters} to use with the message
         * query.
         */
        public RcsMessageQueryParameters build() {
            return new RcsMessageQueryParameters(mMessageType, mFileTransferPresence, mMessageLike,
                    mThreadId, mSortingProperty, mIsAscending, mLimit);
        }
    }

    /**
     * Parcelable boilerplate below.
     */
    protected RcsMessageQueryParameters(Parcel in) {
        mMessageType = in.readInt();
        mFileTransferPresence = in.readInt();
        mMessageLike = in.readString();
        mSortingProperty = in.readInt();
        mIsAscending = in.readBoolean();
        mLimit = in.readInt();
        mThreadId = in.readInt();
    }

    public static final Creator<RcsMessageQueryParameters> CREATOR =
            new Creator<RcsMessageQueryParameters>() {
                @Override
                public RcsMessageQueryParameters createFromParcel(Parcel in) {
                    return new RcsMessageQueryParameters(in);
                }

                @Override
                public RcsMessageQueryParameters[] newArray(int size) {
                    return new RcsMessageQueryParameters[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMessageType);
        dest.writeInt(mFileTransferPresence);
        dest.writeString(mMessageLike);
        dest.writeInt(mSortingProperty);
        dest.writeBoolean(mIsAscending);
        dest.writeInt(mLimit);
        dest.writeInt(mThreadId);
    }
}
