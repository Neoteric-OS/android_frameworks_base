/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.telephony;

import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;

import com.android.internal.telephony.ISms;
import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.SmsManagerBase;
import com.android.internal.telephony.SmsRawData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.telephony.TelephonyManager.PHONE_TYPE_GSM;
import static android.telephony.TelephonyManager.PHONE_TYPE_CDMA;

/**
 * Manages SMS operations such as sending data, text, and pdu SMS messages.
 * Get this object by calling the static method SmsManager.getDefault().
 */
public final class SmsManager extends SmsManagerBase {
    private static SmsManager sInstance;
    private static SmsManagerBase sSupplementaryInstance;
    private static int sLastPhoneType;

    /** {@inheritDoc} */
    public void sendTextMessage(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sSupplementaryInstance.sendTextMessage(destinationAddress, scAddress, text,
                sentIntent, deliveryIntent);
    }

    /** {@inheritDoc} */
    public ArrayList<String> divideMessage(String text) {
        return sSupplementaryInstance.divideMessage(text);
    }

    /**
     * Send a multi-part text based SMS.  The callee should have already
     * divided the message into correctly sized parts by calling
     * <code>divideMessage</code>.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applicaitons,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendMultipartTextMessage(
            String destinationAddress, String scAddress, ArrayList<String> parts,
            ArrayList<PendingIntent> sentIntents, ArrayList<PendingIntent> deliveryIntents) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }

        if (parts.size() > 1) {
            try {
                ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
                if (iccISms != null) {
                    iccISms.sendMultipartText(destinationAddress, scAddress, parts,
                            sentIntents, deliveryIntents);
                }
            } catch (RemoteException ex) {
                // ignore it
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, parts.get(0),
                    sentIntent, deliveryIntent);
        }
    }

    /** {@inheritDoc} */
    public void sendDataMessage(
            String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sSupplementaryInstance.sendDataMessage(destinationAddress, scAddress, destinationPort,
                data, sentIntent, deliveryIntent);
    }

    /**
     * Get the default instance of the SmsManager
     *
     * @return the default instance of the SmsManager
     */
    public static SmsManager getDefault() {
        if (sInstance == null) {
            sInstance = new SmsManager();
        }
        sSupplementaryInstance = getSupplement();

        return sInstance;
    }

    private SmsManager() {
        //nothing
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     *
     * @param smsc the SMSC for this message, or NULL for the default SMSC
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return true for success
     *
     * {@hide}
     */
    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status) {
        boolean success = false;

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                success = iccISms.copyMessageToIccEf(status, pdu, smsc);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Delete the specified message from the ICC.
     *
     * @param messageIndex is the record index of the message on ICC
     * @return true for success
     *
     * {@hide}
     */
    public boolean
    deleteMessageFromIcc(int messageIndex) {
        boolean success = false;
        byte[] pdu = new byte[IccConstants.SMS_RECORD_LENGTH-1];
        Arrays.fill(pdu, (byte)0xff);

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(messageIndex, STATUS_ON_ICC_FREE, pdu);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Update the specified message on the ICC.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return true for success
     *
     * {@hide}
     */
    public boolean updateMessageOnIcc(int messageIndex, int newStatus, byte[] pdu) {
        boolean success = false;

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(messageIndex, newStatus, pdu);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Retrieves all messages currently stored on ICC.
     *
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects
     *
     * {@hide}
     */
    public ArrayList<SmsMessage> getAllMessagesFromIcc() {
        List<SmsRawData> records = null;

        try {
            ISms iccISms = ISms.Stub.asInterface(ServiceManager.getService("isms"));
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf();
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return createMessageListFromRawRecords(records);
   }

    /**
     * Create a list of <code>SmsMessage</code>s from a list of RawSmsData
     * records returned by <code>getAllMessagesFromIcc()</code>
     *
     * @param records SMS EF records, returned by
     *   <code>getAllMessagesFromIcc</code>
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects.
     */
    private ArrayList<SmsMessage> createMessageListFromRawRecords(List records) {
        ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = (SmsRawData)records.get(i);
                // List contains all records, including "free" records (null)
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i+1, data.getBytes());
                    messages.add(sms);
                }
            }
        }
        return messages;
    }

    /** This method returns the reference to a supplementary specific
     *  SmsManager object.
     * @return Supplementary specific SmsManager.
     */
    private static final SmsManagerBase getSupplement(){
        int currentPhoneType = TelephonyManager.getDefault().getPhoneType();
        if ((sLastPhoneType != currentPhoneType)|| null == sSupplementaryInstance){
            return getSupplement(currentPhoneType);
        }else{
            return sSupplementaryInstance;
        }
    }

    /** This method returns the reference to a supplementary specific
     *  SmsManager object.
     * @param phoneType code according to android.provider.Settings
     * @return Supplementary specific SmsManager.
     */
    private static final SmsManagerBase getSupplement(int phoneType){
        if (PHONE_TYPE_CDMA == phoneType) {
            return new com.android.internal.telephony.cdma.SmsManager();
        } else {
            return new com.android.internal.telephony.gsm.SmsManager();
        }
    }

    // see SmsMessage.getStatusOnIcc
    /** Free space (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_FREE      = 0;

    /** Received and read (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_READ      = 1;

    /** Received and unread (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNREAD    = 3;

    /** Stored and sent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_SENT      = 5;

    /** Stored and unsent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNSENT    = 7;

    // SMS send failure result codes

    /** Generic failure cause */
    static public final int RESULT_ERROR_GENERIC_FAILURE    = 1;
    /** Failed because radio was explicitly turned off */
    static public final int RESULT_ERROR_RADIO_OFF          = 2;
    /** Failed because no pdu provided */
    static public final int RESULT_ERROR_NULL_PDU           = 3;
    /** Failed because service is currently unavailable */
    static public final int RESULT_ERROR_NO_SERVICE         = 4;
}
