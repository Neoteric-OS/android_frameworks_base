/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

import android.app.PendingIntent;
import android.util.Log;
import android.os.ServiceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * DSIccSmsInterfaceManagerProxy to provide an inter-process communication to
 * access Sms in Icc.
 */
public class DSIccSmsInterfaceManagerProxy extends ISms.Stub {
    static final String LOG_TAG = "RIL_DSIccSms";

    protected Phone[] mPhone;

    protected DSIccSmsInterfaceManagerProxy(Phone[] phone){
        mPhone = phone;

        if (ServiceManager.getService("isms") == null) {
            ServiceManager.addService("isms", this);
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription
    protected void enforceReceiveAndSend(String message) {
        enforceReceiveAndSendOnSubscription(message, getPreferredSmsSubscription());
    }

    protected void enforceReceiveAndSendOnSubscription(String message, int subscription) {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            iccSmsIntMng.enforceReceiveAndSend(message);
        } else {
            Log.e(LOG_TAG,"enforceReceiveAndSendOnSubscription iccSmsIntMng is null" +
                          " for Subscription:"+subscription);
        }
    } */

    /* Non DSDS function is routed through DSDS function with default subscription */
    public boolean
    updateMessageOnIccEf(int index, int status, byte[] pdu) throws android.os.RemoteException {
        return updateMessageOnIccEfOnSubscription(index, status, pdu, getPreferredSmsSubscription());
    }

    public boolean
    updateMessageOnIccEfOnSubscription(int index, int status, byte[] pdu, int subscription)
            throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            return iccSmsIntMng.updateMessageOnIccEf(index, status, pdu);
        } else {
            Log.e(LOG_TAG,"updateMessageOnIccEfOnSubscription iccSmsIntMng is null" +
                          " for Subscription:"+subscription);
            return false;
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public boolean copyMessageToIccEf(int status, byte[] pdu, byte[] smsc) throws android.os.RemoteException {
        return copyMessageToIccEfOnSubscription(status, pdu, smsc, getPreferredSmsSubscription());
    }

    public boolean copyMessageToIccEfOnSubscription(int status, byte[] pdu, byte[] smsc, int subscription)
            throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            return iccSmsIntMng.copyMessageToIccEf(status, pdu, smsc);
        } else {
            Log.e(LOG_TAG,"copyMessageToIccEfOnSubscription iccSmsIntMng is null" +
                          " for Subscription:"+subscription);
            return false;
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public List<SmsRawData> getAllMessagesFromIccEf() throws android.os.RemoteException {
        return getAllMessagesFromIccEfOnSubscription(getPreferredSmsSubscription());
    }

    public List<SmsRawData> getAllMessagesFromIccEfOnSubscription(int subscription) throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            return iccSmsIntMng.getAllMessagesFromIccEf();
        } else {
            Log.e(LOG_TAG,"getAllMessagesFromIccEfOnSubscription iccSmsIntMng is" +
                          " null for Subscription:"+subscription);
            return null;
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendDataOnSubscription(destAddr, scAddr, destPort, data, sentIntent,
            deliveryIntent, getPreferredSmsSubscription());
    }

    public void sendDataOnSubscription(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            iccSmsIntMng.sendData(destAddr, scAddr, destPort, data, sentIntent, deliveryIntent);
        } else {
            Log.e(LOG_TAG,"sendDataOnSubscription iccSmsIntMng is null for Subscription:"+subscription);
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public void sendText(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextOnSubscription(destAddr, scAddr, text, sentIntent, deliveryIntent,
            getPreferredSmsSubscription());
    }
    public void sendTextOnSubscription(String destAddr, String scAddr,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null) {
            iccSmsIntMng.sendText(destAddr, scAddr, text, sentIntent, deliveryIntent);
        } else {
            Log.e(LOG_TAG,"sendTextOnSubscription iccSmsIntMng is null for" +
                          " Subscription:"+subscription);
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public void sendMultipartText(String destAddr, String scAddr, List<String> parts,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
        sendMultipartTextOnSubscription(destAddr, scAddr, (ArrayList<String>) parts,
            (ArrayList<PendingIntent>) sentIntents, (ArrayList<PendingIntent>) deliveryIntents,
                getPreferredSmsSubscription());
    }
    public void sendMultipartTextOnSubscription(String destAddr, String scAddr, List<String> parts,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents, int subscription) throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null ) {
            iccSmsIntMng.sendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents);
        } else {
            Log.e(LOG_TAG,"sendMultipartTextOnSubscription iccSmsIntMng is null for" +
                          " Subscription:"+subscription);
        }
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public boolean enableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return enableCellBroadcastOnSubscription(messageIdentifier, getPreferredSmsSubscription());
    }
    public boolean enableCellBroadcastOnSubscription(int messageIdentifier, int subscription)
            throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null ) {
            return iccSmsIntMng.enableCellBroadcast(messageIdentifier);
        } else {
            Log.e(LOG_TAG,"enableCellBroadcastOnSubscription iccSmsIntMng is null for" +
                          " Subscription:"+subscription);
        }
        return false;
    }

    /* Non DSDS function is routed through DSDS function with default subscription */
    public boolean disableCellBroadcast(int messageIdentifier) throws android.os.RemoteException {
        return disableCellBroadcastOnSubscription(messageIdentifier, getPreferredSmsSubscription());
    }
    public boolean disableCellBroadcastOnSubscription(int messageIdentifier, int subscription)
            throws android.os.RemoteException {
        IccSmsInterfaceManagerProxy iccSmsIntMng = getIccSmsInterfaceManagerProxy(subscription);
        if (iccSmsIntMng != null ) {
            return iccSmsIntMng.disableCellBroadcast(messageIdentifier);
        } else {
            Log.e(LOG_TAG,"disableCellBroadcastOnSubscription iccSmsIntMng is null for" +
                          " Subscription:"+subscription);
        }
        return false;
    }

    /**
     * get sms interface manager object based on subscription.
     **/
    private IccSmsInterfaceManagerProxy getIccSmsInterfaceManagerProxy(int subscription) {
        try {
            return ((PhoneProxy)mPhone[subscription]).getIccSmsInterfaceManagerProxy();
        } catch (NullPointerException e) {
            Log.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subscription );
            e.printStackTrace(); //This will print stact trace
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(LOG_TAG, "Exception is :"+e.toString()+" For subscription :"+subscription );
            e.printStackTrace(); //This will print stack trace
            return null;
        }
    }

    /**
       Gets User preferred SMS subscription */
    public int getPreferredSmsSubscription() {
        return PhoneFactory.getSMSSubscription();
    }
}
