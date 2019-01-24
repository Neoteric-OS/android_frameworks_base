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

import android.annotation.NonNull;
import android.annotation.WorkerThread;

/**
 * This class holds the delivery information of an {@link RcsOutgoingMessage} for each
 * {@link RcsParticipant} that the message was intended for.
 *
 * @hide - TODO(109759350) make this public
 */
public class RcsOutgoingMessageDelivery {
    // Temporary value to be held for delivered timestamp until this object is persisted
    private long mTemporaryDeliveredTimestamp;
    // Temporary value to be held for seen timestamp until this object is persisted
    private long mTemporarySeenTimestamp;
    // The participant that this delivery is intended for
    private final int mRecipientId;
    // The message this delivery is associated with
    private int mRcsOutgoingMessageId;

    /**
     * Constructor to be used with RcsOutgoingMessage.Builder
     *
     * @hide
     */
    RcsOutgoingMessageDelivery(int recipientId) {
        mRecipientId = recipientId;
    }

    /**
     * Constructor to be used with RcsOutgoingMessage.Builder
     *
     * @hide
     */
    RcsOutgoingMessageDelivery(int recipientId, long deliveredTimestamp, long seenTimestamp) {
        mRecipientId = recipientId;
        mTemporaryDeliveredTimestamp = deliveredTimestamp;
        mTemporarySeenTimestamp = seenTimestamp;
    }

    /**
     * Constructor to be used with RcsOutgoingMessage.getDelivery()
     *
     * @hide
     */
    RcsOutgoingMessageDelivery(int recipientId, int messageId) {
        mRecipientId = recipientId;
        mRcsOutgoingMessageId = messageId;
    }

    /**
     * Sets the {@link RcsOutgoingMessage} associated with this delivery. Not meant for public use.
     * This should only be called after creating this object but before persisting into storage.
     *
     * @hide
     */
    void setRcsOutgoingMessageId(int rcsOutgoingMessageId) {
        mRcsOutgoingMessageId = rcsOutgoingMessageId;
    }

    /**
     * Writes this object into storage after initialization.
     *
     * @hide
     */
    void initialSaveToStorage() throws RcsMessageStoreException {
        RcsControllerCall.callWithNoReturn(iRcs -> iRcs.createOutgoingDelivery(
                mRcsOutgoingMessageId, mRecipientId, mTemporarySeenTimestamp,
                mTemporaryDeliveredTimestamp));
    }

    /**
     * Sets the delivery time of this outgoing delivery and persists into storage.
     *
     * @param deliveredTimestamp The timestamp to set to delivery. It is defined as milliseconds
     *                           passed after midnight, January 1, 1970 UTC
     * @throws RcsMessageStoreException if the value could not be persisted into storage
     */
    @WorkerThread
    public void setDeliveredTimestamp(long deliveredTimestamp) throws RcsMessageStoreException {
        RcsControllerCall.callWithNoReturn(iRcs -> iRcs.setOutgoingDeliveryDeliveredTimestamp(
                mRcsOutgoingMessageId, mRecipientId, deliveredTimestamp));
    }

    /**
     * @return Returns the delivered timestamp of the associated message to the associated
     * participant. Timestamp is defined as milliseconds passed after midnight, January 1, 1970 UTC
     * @throws RcsMessageStoreException if the value could not be read from the storage
     */
    @WorkerThread
    public long getDeliveredTimestamp() throws RcsMessageStoreException {
        return RcsControllerCall.call(iRcs -> iRcs.getOutgoingDeliveryDeliveredTimestamp(
                mRcsOutgoingMessageId, mRecipientId));
    }

    /**
     * Sets the seen time of this outgoing delivery and persists into storage.
     *
     * @param seenTimestamp The timestamp to set to delivery. It is defined as milliseconds
     *                      passed after midnight, January 1, 1970 UTC
     * @throws RcsMessageStoreException if the value could not be persisted into storage
     */
    @WorkerThread
    public void setSeenTimestamp(long seenTimestamp) throws RcsMessageStoreException {
        RcsControllerCall.callWithNoReturn(iRcs -> iRcs.setOutgoingDeliverySeenTimestamp(
                mRcsOutgoingMessageId, mRecipientId, seenTimestamp));
    }

    /**
     * @return Returns the seen timestamp of the associated message by the associated
     * participant. Timestamp is defined as milliseconds passed after midnight, January 1, 1970 UTC
     * @throws RcsMessageStoreException if the value could not be read from the storage
     */
    public long getSeenTimestamp() throws RcsMessageStoreException {
        return RcsControllerCall.call(
                iRcs -> iRcs.getOutgoingDeliverySeenTimestamp(mRcsOutgoingMessageId, mRecipientId));
    }

    /**
     * @return Returns the recipient associated with this delivery.
     */
    @NonNull
    public RcsParticipant getRecipient() {
        return new RcsParticipant(mRecipientId);
    }

    /**
     * @return Returns the {@link RcsOutgoingMessage} associated with this delivery.
     */
    @NonNull
    public RcsOutgoingMessage getMessage() {
        return new RcsOutgoingMessage(mRcsOutgoingMessageId);
    }
}
