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

import static android.provider.Telephony.RcsColumns.RcsUnifiedThreadColumns.THREAD_TYPE_1_TO_1;
import static android.provider.Telephony.RcsColumns.RcsUnifiedThreadColumns.THREAD_TYPE_GROUP;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.WorkerThread;

import com.android.internal.annotations.VisibleForTesting;

/**
 * RcsThread represents a single RCS conversation thread. It holds messages that were sent and
 * received and events that occurred on that thread.
 *
 * @hide - TODO(109759350) make this public
 */
public abstract class RcsThread {
    // The rcs_participant_thread_id that represents this thread in the database
    protected int mThreadId;

    /**
     * @hide
     */
    protected RcsThread(int threadId) {
        mThreadId = threadId;
    }

    /**
     * @return Returns the text of the {@link RcsMessage} with highest origination timestamp value
     * (i.e. latest) in this thread
     * @throws RcsMessageStoreException if the value could not be read from the storage
     */
    @Nullable
    @WorkerThread
    public String getSnippetText() throws RcsMessageStoreException {
        return RcsControllerCall.call(iRcs -> iRcs.getSnippetText(mThreadId));
    }

    /**
     * @return Returns the status of the {@link RcsMessage} with highest origination timestamp value
     * (i.e. latest) in this thread
     * @throws RcsMessageStoreException if the value could not be read from the storage
     */
    @WorkerThread
    public @RcsMessage.RcsMessageStatus int getSnippetStatus() throws RcsMessageStoreException {
        return RcsControllerCall.call(iRcs -> iRcs.getSnippetStatus(mThreadId));
    }

    /**
     * @return Returns the timestamp of the {@link RcsMessage} with highest origination timestamp
     * value (i.e. latest) in this thread
     * @throws RcsMessageStoreException if the value could not be read from the storage
     */
    @WorkerThread
    public long getSnippetTimestamp() throws RcsMessageStoreException {
        return RcsControllerCall.call(iRcs -> iRcs.getSnippetTimestamp(mThreadId));
    }

    /**
     * Adds a new empty {@link RcsIncomingMessage} to this RcsThread and persists it in storage.
     *
     * @throws RcsMessageStoreException if the message could not be persisted into storage.
     */
    @WorkerThread
    @NonNull
    public RcsIncomingMessage addIncomingMessage(
            RcsIncomingMessageCreationParameters rcsIncomingMessageCreationParameters)
            throws RcsMessageStoreException {
        return new RcsIncomingMessage(RcsControllerCall.call(iRcs -> iRcs.addIncomingMessage(
                mThreadId, rcsIncomingMessageCreationParameters)));
    }

    /**
     * Adds a new empty {@link RcsOutgoingMessage} to this RcsThread and persists it in storage.
     *
     * @throws RcsMessageStoreException if the message could not be persisted into storage.
     */
    @WorkerThread
    @NonNull
    public RcsOutgoingMessage addOutgoingMessage(
            RcsOutgoingMessageCreationParameters rcsOutgoingMessageCreationParameters)
            throws RcsMessageStoreException {
        int messageId = RcsControllerCall.call(iRcs -> iRcs.addOutgoingMessage(
                mThreadId, rcsOutgoingMessageCreationParameters));

        return new RcsOutgoingMessage(messageId);
    }

    /**
     * Deletes an {@link RcsMessage} from this RcsThread and updates the storage.
     *
     * @param rcsMessage The message to delete from the thread
     * @throws RcsMessageStoreException if the message could not be deleted
     */
    @WorkerThread
    public void deleteMessage(RcsMessage rcsMessage) throws RcsMessageStoreException {
        RcsControllerCall.callWithNoReturn(
                iRcs -> iRcs.deleteMessage(rcsMessage.getId(), rcsMessage.isIncoming(), mThreadId,
                        isGroup()));
    }

    /**
     * Convenience function for loading all the {@link RcsMessage}s in this {@link RcsThread}. For
     * a more detailed and paginated query, please use
     * {@link RcsMessageStore#getRcsMessages(RcsMessageQueryParameters)}
     *
     * @return Loads the {@link RcsMessage}s in this thread and returns them in an immutable list.
     * @throws RcsMessageStoreException if the messages could not be read from the storage
     */
    @WorkerThread
    public RcsMessageQueryResult getMessages() throws RcsMessageStoreException {
        RcsMessageQueryParameters queryParameters =
                RcsMessageQueryParameters.builder().limitToThread(this).build();
        return RcsControllerCall.call(iRcs -> iRcs.getMessages(queryParameters));
    }

    /**
     * @return Returns whether this is a group thread or not
     */
    public abstract boolean isGroup();

    /**
     * @hide
     */
    @VisibleForTesting
    public int getThreadId() {
        return mThreadId;
    }

    /**
     * @hide
     */
    public int getThreadType() {
        return isGroup() ? THREAD_TYPE_GROUP : THREAD_TYPE_1_TO_1;
    }
}
