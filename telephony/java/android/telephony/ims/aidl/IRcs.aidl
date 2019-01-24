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

package android.telephony.ims.aidl;

import android.net.Uri;
import android.telephony.ims.RcsEventQueryContinuationToken;
import android.telephony.ims.RcsEventQueryParameters;
import android.telephony.ims.RcsEventQueryResult;
import android.telephony.ims.RcsFileTransferCreationParameters;
import android.telephony.ims.RcsIncomingMessageCreationParameters;
import android.telephony.ims.RcsMessageQueryContinuationToken;
import android.telephony.ims.RcsMessageQueryParameters;
import android.telephony.ims.RcsMessageQueryResult;
import android.telephony.ims.RcsOutgoingMessageCreationParameters;
import android.telephony.ims.RcsParticipantQueryContinuationToken;
import android.telephony.ims.RcsParticipantQueryParameters;
import android.telephony.ims.RcsParticipantQueryResult;
import android.telephony.ims.RcsThreadQueryContinuationToken;
import android.telephony.ims.RcsThreadQueryParameters;
import android.telephony.ims.RcsThreadQueryResult;

/**
 * RPC definition between RCS storage APIs and phone process.
 * {@hide}
 */
interface IRcs {
    // RcsMessageStore APIs
    RcsThreadQueryResult getRcsThreads(in RcsThreadQueryParameters queryParameters);

    RcsThreadQueryResult getRcsThreadsWithToken(
        in RcsThreadQueryContinuationToken continuationToken);

    RcsParticipantQueryResult getParticipants(in RcsParticipantQueryParameters queryParameters);

    RcsParticipantQueryResult getParticipantsWithToken(
        in RcsParticipantQueryContinuationToken continuationToken);

    RcsMessageQueryResult getMessages(in RcsMessageQueryParameters queryParameters);

    RcsMessageQueryResult getMessagesWithToken(
        in RcsMessageQueryContinuationToken continuationToken);

    RcsEventQueryResult getEvents(in RcsEventQueryParameters queryParameters);

    RcsEventQueryResult getEventsWithToken(
        in RcsEventQueryContinuationToken continuationToken);

    void deleteThread(int threadId, int threadType);

    int createRcs1To1Thread(int participantId);

    int createGroupThread(in int[] participantIds, String groupName, in Uri groupIcon);

    // RcsThread APIs
    int addIncomingMessage(int rcsThreadId,
            in RcsIncomingMessageCreationParameters rcsIncomingMessageCreationParameters);

    int addOutgoingMessage(int rcsThreadId,
            in RcsOutgoingMessageCreationParameters rcsOutgoingMessageCreationParameters);

    void deleteMessage(int rcsMessageId, boolean isIncoming, int rcsThreadId, boolean isGroup);

    String getSnippetText(int rcsThreadId);

    int getSnippetStatus(int rcsThreadId);

    long getSnippetTimestamp(int rcsThreadId);

    // Rcs1To1Thread APIs
    void set1To1ThreadFallbackThreadId(int rcsThreadId, long fallbackId);

    long get1To1ThreadFallbackThreadId(int rcsThreadId);

    int get1To1ThreadOtherParticipantId(int rcsThreadId);

    // RcsGroupThread APIs
    void setGroupThreadName(int rcsThreadId, String groupName);

    String getGroupThreadName(int rcsThreadId);

    void setGroupThreadIcon(int rcsThreadId, in Uri groupIcon);

    Uri getGroupThreadIcon(int rcsThreadId);

    void setGroupThreadOwner(int rcsThreadId, int participantId);

    int getGroupThreadOwner(int rcsThreadId);

    void setGroupThreadConferenceUri(int rcsThreadId, in Uri conferenceUri);

    Uri getGroupThreadConferenceUri(int rcsThreadId);

    void addParticipantToGroupThread(int rcsThreadId, int participantId);

    void removeParticipantFromGroupThread(int rcsThreadId, int participantId);


    // RcsParticipant APIs
    int createRcsParticipant(String canonicalAddress, String alias);

    String getParticipantCanonicalAddress(int participantId);

    String getParticipantAlias(int participantId);

    void updateRcsParticipantAlias(int id, String alias);

    // RcsMessage APIs
    void setMessageSubId(int messageId, boolean isIncoming, int subId);

    int getMessageSubId(int messageId, boolean isIncoming);

    void setMessageStatus(int messageId, boolean isIncoming, int status);

    int getMessageStatus(int messageId, boolean isIncoming);

    void setMessageOriginationTimestamp(int messageId, boolean isIncoming, long originationTimestamp);

    long getMessageOriginationTimestamp(int messageId, boolean isIncoming);

    void setGlobalMessageIdForMessage(int messageId, boolean isIncoming, String globalId);

    String getGlobalMessageIdForMessage(int messageId, boolean isIncoming);

    void setMessageArrivalTimestamp(int messageId, boolean isIncoming, long arrivalTimestamp);

    long getMessageArrivalTimestamp(int messageId, boolean isIncoming);

    void setMessageNotifiedTimestamp(int messageId, boolean isIncoming, long notifiedTimestamp);

    long getMessageNotifiedTimestamp(int messageId, boolean isIncoming);

    void setTextForMessage(int messageId, boolean isIncoming, String text);

    String getTextForMessage(int messageId, boolean isIncoming);

    void setLatitudeForMessage(int messageId, boolean isIncoming, double latitude);

    double getLatitudeForMessage(int messageId, boolean isIncoming);

    void setLongitudeForMessage(int messageId, boolean isIncoming, double longitude);

    double getLongitudeForMessage(int messageId, boolean isIncoming);

    int[] getFileTransfersAttachedToMessage(int messageId, boolean isIncoming);

    int getSenderParticipant(int messageId);

    // RcsOutgoingMessageDelivery APIs
    void createOutgoingDelivery(int messageId, int participantId, long seenTimestamp, long deliveredTimestamp);

    int[] getOutgoingDeliveriesForMessage(int messageId);

    long getOutgoingDeliveryDeliveredTimestamp(int messageId, int participantId);

    void setOutgoingDeliveryDeliveredTimestamp(int messageId, int participantId, long deliveredTimestamp);

    long getOutgoingDeliverySeenTimestamp(int messageId, int participantId);

    void setOutgoingDeliverySeenTimestamp(int messageId, int participantId, long seenTimestamp);

    // RcsFileTransferPart APIs
    int storeFileTransfer(int messageId, boolean isIncoming,
            in RcsFileTransferCreationParameters fileTransferCreationParameters);

    void deleteFileTransfer(int partId);

    void setFileTransferSessionId(int partId, String sessionId);

    String getFileTransferSessionId(int partId);

    void setFileTransferContentUri(int partId, in Uri contentUri);

    Uri getFileTransferContentUri(int partId);

    void setFileTransferContentType(int partId, String contentType);

    String getFileTransferContentType(int partId);

    void setFileTransferFileSize(int partId, long fileSize);

    long getFileTransferFileSize(int partId);

    void setFileTransferTransferOffset(int partId, long transferOffset);

    long getFileTransferTransferOffset(int partId);

    void setFileTransferStatus(int partId, int transferStatus);

    int getFileTransferStatus(int partId);

    void setFileTransferWidth(int partId, int width);

    int getFileTransferWidth(int partId);

    void setFileTransferHeight(int partId, int height);

    int getFileTransferHeight(int partId);

    void setFileTransferLength(int partId, long length);

    long getFileTransferLength(int partId);

    void setFileTransferPreviewUri(int partId, in Uri uri);

    Uri getFileTransferPreviewUri(int partId);

    void setFileTransferPreviewType(int partId, String type);

    String getFileTransferPreviewType(int partId);

    // RcsEvent APIs
    int createGroupThreadNameChangedEvent(long timestamp, int threadId, int originationParticipantId, String oldName, String newName);

    int createGroupThreadIconChangedEvent(long timestamp, int threadId, int originationParticipantId, in Uri oldIcon, in Uri newIcon);

    int createGroupThreadParticipantJoinedEvent(long timestamp, int threadId, int originationParticipantId, int participantId);

    int createGroupThreadParticipantLeftEvent(long timestamp, int threadId, int originationParticipantId, int participantId);

    int createParticipantAliasChangedEvent(long timestamp, int participantId, String oldAlias, String newAlias);
}