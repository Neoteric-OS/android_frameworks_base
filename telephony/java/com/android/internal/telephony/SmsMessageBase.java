/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.telephony;

import android.os.Parcel;
import android.util.Log;
import com.android.internal.telephony.SmsHeader;
import java.util.Arrays;

import static android.telephony.SmsMessage.ENCODING_7BIT;
import static android.telephony.SmsMessage.ENCODING_16BIT;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES_WITH_HEADER;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER;
import static android.telephony.SmsMessage.MessageClass;
import static com.android.internal.telephony.SmsAddress.TON_ABBREVIATED;
import static com.android.internal.telephony.SmsAddress.TON_ALPHANUMERIC;
import static com.android.internal.telephony.SmsAddress.TON_INTERNATIONAL;
import static com.android.internal.telephony.SmsAddress.TON_NATIONAL;
import static com.android.internal.telephony.SmsAddress.TON_NETWORK;
import static com.android.internal.telephony.SmsAddress.TON_SUBSCRIBER;
import static com.android.internal.telephony.SmsAddress.TON_UNKNOWN;

/**
 * Base class declaring the specific methods and members for SmsMessage.
 */
public abstract class SmsMessageBase {
    private static final String LOG_TAG = "SMS";

    /** {@hide} The address of the SMSC. May be null */
    protected String scAddress;

    /** {@hide} The address of the sender */
    protected SmsAddress originatingAddress;

    /** {@hide} The message body as a string. May be null if the message isn't text */
    protected String messageBody;

    /** {@hide} */
    protected String pseudoSubject;

    /** {@hide} Non-null if this is an email gateway message */
    protected String emailFrom;

    /** {@hide} Non-null if this is an email gateway message */
    protected String emailBody;

    /** {@hide} */
    protected boolean isEmail;

    /** {@hide} */
    protected long scTimeMillis;

    /** {@hide} The raw PDU of the message */
    protected byte[] mPdu;

    /** {@hide} The raw bytes for the user data section of the message */
    protected byte[] userData;

    /** {@hide} */
    protected SmsHeader userDataHeader;

    // "Message Waiting Indication Group"
    // 23.038 Section 4
    /** {@hide} */
    protected boolean isMwi;

    /** {@hide} */
    protected boolean mwiSense;

    /** {@hide} */
    protected boolean mwiDontStore;

    /**
     * Indicates status for messages stored on the ICC.
     */
    protected int statusOnIcc = -1;

    /**
     * Record index of message in the EF.
     */
    protected int indexOnIcc = -1;

    /** TP-Message-Reference - Message Reference of sent message. @hide */
    public int messageRef;

    public static abstract class SubmitPduBase  {
        public byte[] encodedScAddress; // Null if not applicable.
        public byte[] encodedMessage;

        public String toString() {
            return "SubmitPdu: encodedScAddress = "
                    + Arrays.toString(encodedScAddress)
                    + ", encodedMessage = "
                    + Arrays.toString(encodedMessage);
        }
    }

    /**
     * Returns the address of the SMS service center that relayed this message
     * or null if there is none.
     */
    public String getServiceCenterAddress() {
        return scAddress;
    }

    /**
     * Returns the originating address (sender) of this SMS message in String
     * form or null if unavailable
     */
    public String getOriginatingAddress() {
        if (originatingAddress == null) {
            return null;
        }

        return originatingAddress.getAddressString();
    }

    /**
     * Returns the originating address, or email from address if this message
     * was from an email gateway. Returns null if originating address
     * unavailable.
     */
    public String getDisplayOriginatingAddress() {
        if (isEmail) {
            return emailFrom;
        } else {
            return getOriginatingAddress();
        }
    }

    /**
     * Returns the message body as a String, if it exists and is text based.
     * @return message body is there is one, otherwise null
     */
    public String getMessageBody() {
        return messageBody;
    }

    /**
     * Returns the ordinal number of the class of this message.
     * {@hide}
     */
    public abstract MessageClass getMessageClass();

    /**
     * Returns the message body, or email message body if this message was from
     * an email gateway. Returns null if message body unavailable.
     */
    public String getDisplayMessageBody() {
        if (isEmail) {
            return emailBody;
        } else {
            return getMessageBody();
        }
    }

    /**
     * Unofficial convention of a subject line enclosed in parens empty string
     * if not present
     */
    public String getPseudoSubject() {
        return pseudoSubject == null ? "" : pseudoSubject;
    }

    /**
     * Returns the service centre timestamp in currentTimeMillis() format
     */
    public long getTimestampMillis() {
        return scTimeMillis;
    }

    /**
     * Returns true if message is an email.
     *
     * @return true if this message came through an email gateway and email
     *         sender / subject / parsed body are available
     */
    public boolean isEmail() {
        return isEmail;
    }

    /**
     * @return if isEmail() is true, body of the email sent through the gateway.
     *         null otherwise
     */
    public String getEmailBody() {
        return emailBody;
    }

    /**
     * @return if isEmail() is true, email from address of email sent through
     *         the gateway. null otherwise
     */
    public String getEmailFrom() {
        return emailFrom;
    }

    /**
     * Get protocol identifier.
     */
    public abstract int getProtocolIdentifier();

    /**
     * See TS 23.040 9.2.3.9 returns true if this is a "replace short message"
     * SMS
     */
    public abstract boolean isReplace();

    /**
     * Returns true for CPHS MWI toggle message.
     *
     * @return true if this is a CPHS MWI toggle message See CPHS 4.2 section
     *         B.4.2
     */
    public abstract boolean isCphsMwiMessage();

    /**
     * returns true if this message is a CPHS voicemail / message waiting
     * indicator (MWI) clear message
     */
    public abstract boolean isMWIClearMessage();

    /**
     * returns true if this message is a CPHS voicemail / message waiting
     * indicator (MWI) set message
     */
    public abstract boolean isMWISetMessage();

    /**
     * returns true if this message is a "Message Waiting Indication Group:
     * Discard Message" notification and should not be stored.
     */
    public abstract boolean isMwiDontStore();

    /**
     * returns the user data section minus the user data header if one was
     * present.
     */
    public byte[] getUserData() {
        return userData;
    }

    /**
     * Returns an object representing the user data header
     *
     * @return an object representing the user data header
     *
     * {@hide}
     */
    public SmsHeader getUserDataHeader() {
        return userDataHeader;
    }

    /**
     * Returns the raw PDU for the message.
     *
     * @return the raw PDU for the message.
     */
    public byte[] getPdu() {
        return mPdu;
    }

    /**
     * For an SMS-STATUS-REPORT message, this returns the status field from
     * the status report.  This field indicates the status of a previously
     * submitted SMS, if requested.  See TS 23.040, 9.2.3.15 TP-Status for a
     * description of values.
     *
     * @return 0 indicates the previously sent message was received.
     *         See TS 23.040, 9.9.2.3.15 for a description of other possible
     *         values.
     */
    public abstract int getStatus();

    /**
     * Return true iff the message is a SMS-STATUS-REPORT message.
     */
    public abstract boolean isStatusReportMessage();

    /**
     * Returns true iff the <code>TP-Reply-Path</code> bit is set in
     * this message.
     */
    public abstract boolean isReplyPathPresent();

    /* do-methods:
     * GSM:     intermediate methods for calling legacy static methods by proxy.
     * CDMA:    final methods, static methods already provided by proxy.
     */
    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doCreateFromPdu(byte[] pdu);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doNewFromCMT(String[] lines);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doNewFromCMTI(String line);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doNewFromCDS(String line);

    /** meta method for calling static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doNewFromParcel(Parcel p);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SmsMessageBase doCreateFromEfRecord(int index,
            byte[] data);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract int doGetTPLayerLengthForPDU(String pdu);

    /**
     * Calculates the number of SMS's required to encode the message body and
     * the number of characters remaining until the next message, given the
     * current encoding.
     *
     * @param messageBody the message to encode
     * @param use7bitOnly if true, characters that are not part of the GSM
     *         alphabet are counted as a single space char.  If false, a
     *         messageBody containing non-GSM alphabet characters is calculated
     *         for 16-bit encoding.
     * @return an int[4] with int[0] being the number of SMS's required, int[1]
     *         the number of code units used, and int[2] is the number of code
     *         units remaining until the next message. int[3] is the encoding
     *         type that should be used for the message.
     */
    public int[] doCalculateLength(String messageBody, boolean use7bitOnly) {
        int ret[] = new int[4];

        try {
            // Try GSM alphabet
            int septets = GsmAlphabet.countGsmSeptets(messageBody, !use7bitOnly);
            ret[1] = septets;
            if (septets > MAX_USER_DATA_SEPTETS) {
                ret[0] = (septets / MAX_USER_DATA_SEPTETS_WITH_HEADER) + 1;
                ret[2] = septets % MAX_USER_DATA_SEPTETS_WITH_HEADER;
            } else {
                ret[0] = 1;
                ret[2] = MAX_USER_DATA_SEPTETS - septets;
            }
            ret[3] = ENCODING_7BIT;
        } catch (EncodeException ex) {
            // fall back to USC-2
            int octets = messageBody.length() * 2;
            ret[1] = octets;
            if (octets > MAX_USER_DATA_BYTES) {
                // 6 is the size of the user data header
                ret[0] = (octets / MAX_USER_DATA_BYTES_WITH_HEADER) + 1;
                ret[2] = octets % MAX_USER_DATA_BYTES_WITH_HEADER;
            } else {
                ret[0] = 1;
                ret[2] = MAX_USER_DATA_BYTES - octets;
            }
            ret[3] = ENCODING_16BIT;
        }

        return ret;
    }

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SubmitPduBase doGetSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested, byte[] header);

    /** meta method for calling legacy static method by proxy.
     * {@hide}
     */
    public abstract SubmitPduBase doGetSubmitPdu(String scAddress,
            String destinationAddress, String message,
            boolean statusReportRequested);

    /** meta method for calling legacy static method by proxy.
     * @hide
     */
    public abstract SubmitPduBase doGetSubmitPdu(String scAddress,
            String destinationAddress, short destinationPort, byte[] data,
            boolean statusReportRequested);
    /* end of do-methods */

    /**
     * Returns the status of the message on the ICC (read, unread, sent, unsent).
     *
     * @return the status of the message on the ICC.  These are:
     *         SmsManager.STATUS_ON_ICC_FREE
     *         SmsManager.STATUS_ON_ICC_READ
     *         SmsManager.STATUS_ON_ICC_UNREAD
     *         SmsManager.STATUS_ON_ICC_SEND
     *         SmsManager.STATUS_ON_ICC_UNSENT
     */
    public int getStatusOnIcc() {
        return statusOnIcc;
    }

    /**
     * Returns the record index of the message on the ICC (1-based index).
     * @return the record index of the message on the ICC, or -1 if this
     *         SmsMessage was not created from a ICC SMS EF record.
     */
    public int getIndexOnIcc() {
        return indexOnIcc;
    }

    protected void parseMessageBody() {
        if (originatingAddress.couldBeEmailGateway()) {
            extractEmailAddressFromMessageBody();
        }
    }

    /**
     * Try to parse this message as an email gateway message -> Neither
     * of the standard ways are currently supported: There are two ways
     * specified in TS 23.040 Section 3.8 (not supported via this mechanism) -
     * SMS message "may have its TP-PID set for internet electronic mail - MT
     * SMS format: [<from-address><space>]<message> - "Depending on the
     * nature of the gateway, the destination/origination address is either
     * derived from the content of the SMS TP-OA or TP-DA field, or the
     * TP-OA/TP-DA field contains a generic gateway address and the to/from
     * address is added at the beginning as shown above." - multiple addreses
     * separated by commas, no spaces - subject field delimited by '()' or '##'
     * and '#' Section 9.2.3.24.11
     */
    protected void extractEmailAddressFromMessageBody() {

        /*
         * a little guesswork here. I haven't found doc for this.
         * the format could be either
         *
         * 1. [x@y][ ]/[subject][ ]/[body]
         * -or-
         * 2. [x@y][ ]/[body]
         */
        int slash = 0, slash2 = 0, atSymbol = 0;

        try {
            slash = messageBody.indexOf(" /");
            if (slash == -1) {
                return;
            }

            atSymbol = messageBody.indexOf('@');
            if (atSymbol == -1 || atSymbol > slash) {
                return;
            }

            emailFrom = messageBody.substring(0, slash);

            slash2 = messageBody.indexOf(" /", slash + 2);

            if (slash2 == -1) {
                pseudoSubject = null;
                emailBody = messageBody.substring(slash + 2);
            } else {
                pseudoSubject = messageBody.substring(slash + 2, slash2);
                emailBody = messageBody.substring(slash2 + 2);
            }

            isEmail = true;
        } catch (Exception ex) {
            Log.w(LOG_TAG,
                    "extractEmailAddressFromMessageBody: exception slash="
                    + slash + ", atSymbol=" + atSymbol + ", slash2="
                    + slash2, ex);
        }
    }

}

