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

import static android.provider.Telephony.RcsColumns.RcsUnifiedMessageColumns.MESSAGE_TYPE_INCOMING;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.ims.RcsTypeIdPair;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of a {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParams)}
 * call. This class allows getting the token for querying the next batch of messages in order to
 * prevent handling large amounts of data at once.
 */
public final class RcsMessageQueryResult {
    private final RcsControllerCall mRcsControllerCall;
    private final RcsMessageQueryResultDescriptor mRcsMessageQueryResultDescriptor;

    /**
     * Internal constructor for {@link com.android.internal.telephony.ims.RcsMessageStoreController}
     * to create query results
     *
     * @hide
     */
    public RcsMessageQueryResult(
            RcsControllerCall rcsControllerCall,
            RcsMessageQueryResultDescriptor rcsMessageQueryResultDescriptor) {
        mRcsControllerCall = rcsControllerCall;
        mRcsMessageQueryResultDescriptor = rcsMessageQueryResultDescriptor;
    }

    /**
     * Returns a token to call
     * {@link RcsMessageStore#getRcsMessages(RcsQueryContinuationToken)}
     * to get the next batch of {@link RcsMessage}s.
     */
    @Nullable
    public RcsQueryContinuationToken getContinuationToken() {
        return mRcsMessageQueryResultDescriptor.mContinuationToken;
    }

    /**
     * Returns all the {@link RcsMessage}s in the current query result. Call {@link
     * RcsMessageStore#getRcsMessages(RcsQueryContinuationToken)} to get the next batch
     * of {@link RcsMessage}s.
     */
    @NonNull
    public List<RcsMessage> getMessages() {
        List<RcsMessage> messages = new ArrayList<>();
        for (RcsTypeIdPair typeIdPair : mRcsMessageQueryResultDescriptor.mMessageTypeIdPairs) {
            if (typeIdPair.getType() == MESSAGE_TYPE_INCOMING) {
                messages.add(new RcsIncomingMessage(mRcsControllerCall, typeIdPair.getId()));
            } else {
                messages.add(new RcsOutgoingMessage(mRcsControllerCall, typeIdPair.getId()));
            }
        }

        return messages;
    }
}
