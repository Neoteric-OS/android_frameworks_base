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

package com.android.internal.telephony.gsm;

import android.app.PendingIntent;
import android.text.TextUtils;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsManagerBase;
import com.android.internal.telephony.gsm.SmsMessage;

import java.util.ArrayList;

import static android.telephony.SmsMessage.ENCODING_7BIT;
import static android.telephony.SmsMessage.ENCODING_8BIT;
import static android.telephony.SmsMessage.ENCODING_16BIT;
import static android.telephony.SmsMessage.ENCODING_UNKNOWN;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES_WITH_HEADER;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER;

/**
 * Manages specific CDMA SMS operations. 
 */
public class SmsManager extends SmsManagerBase {
    /** Specific SmsMessage object for referencing static methods */
    private static SmsMessage sSms;

    public SmsManager(){
        sSms = new SmsMessage();
    }

    /** {@inheritDoc} */
    public void sendTextMessage(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }

        SmsMessage.SubmitPdu pdus = sSms.doGetSubmitPdu(
                scAddress, destinationAddress, text, (deliveryIntent != null));
        sendRawPdu(pdus.encodedScAddress, pdus.encodedMessage, sentIntent, deliveryIntent);
    }

    /** {@inheritDoc} */
    public ArrayList<String> divideMessage(String text) {
        int size = text.length();
        int[] params = sSms.doCalculateLength(text, false);
            /* SmsMessage.calculateLength returns an int[4] with:
             *   int[0] being the number of SMS's required,
             *   int[1] the number of code units used,
             *   int[2] is the number of code units remaining until the next message.
             *   int[3] is the encoding type that should be used for the message.
             */
        int messageCount = params[0];
        int encodingType = params[3];
        ArrayList<String> result = new ArrayList<String>(messageCount);

        int start = 0;
        int limit;

        if (messageCount > 1) {
            limit = (encodingType == ENCODING_7BIT)?
                MAX_USER_DATA_SEPTETS_WITH_HEADER: MAX_USER_DATA_BYTES_WITH_HEADER;
        } else {
            limit = (encodingType == ENCODING_7BIT)?
                MAX_USER_DATA_SEPTETS: MAX_USER_DATA_BYTES;
        }

        try {
            while (start < size) {
                int end = GsmAlphabet.findLimitIndex(text, start, limit, encodingType);
                result.add(text.substring(start, end));
                start = end;
            }
        }
        catch (EncodeException e) {
            // ignore it.
        }
        return result;
    }

    /** {@inheritDoc} */
    public void sendDataMessage(
            String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }

        SmsMessage.SubmitPdu pdus = sSms.doGetSubmitPdu(
                scAddress, destinationAddress,
                destinationPort, data, (deliveryIntent != null));
        sendRawPdu(pdus.encodedScAddress, pdus.encodedMessage, sentIntent, deliveryIntent);
    }

}
