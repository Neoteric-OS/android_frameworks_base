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

import android.app.ActivityManagerNative;
import android.content.Intent;
import android.os.Message;
import android.util.Log;
import com.android.internal.telephony.gsm.MSimGSMPhone;
import com.android.internal.telephony.cdma.MSimCDMAPhone;

public class MSimPhoneProxy extends PhoneProxy {

    private int mSubscription = 0;

    //***** Constructors
    public MSimPhoneProxy(Phone phone) {
        super(phone);

        mSubscription = phone.getSubscription();
    }
    // --msim--: TODO: override handleMessage / only the deleteAndCreatePhone object methods?
    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
        case EVENT_RADIO_TECHNOLOGY_CHANGED:
            //switch Phone from CDMA to GSM or vice versa
            mOutgoingPhone = mActivePhone.getPhoneName();
            logd("Switching phone from " + mOutgoingPhone + "Phone to " +
                    (mOutgoingPhone.equals("GSM") ? "CDMAPhone" : "GSMPhone") );
            boolean oldPowerState = false; // old power state to off
            if (mResetModemOnRadioTechnologyChange) {
                if (mCommandsInterface.getRadioState().isOn()) {
                    oldPowerState = true;
                    logd("Setting Radio Power to Off");
                    mCommandsInterface.setRadioPower(false, null);
                }
            }

            if(mOutgoingPhone.equals("GSM")) {
                logd("Make a new CDMAPhone and destroy the old GSMPhone.");

                ((MSimGSMPhone)mActivePhone).dispose();
                Phone oldPhone = mActivePhone;

                //Give the garbage collector a hint to start the garbage collection asap
                // NOTE this has been disabled since radio technology change could happen during
                //   e.g. a multimedia playing and could slow the system. Tests needs to be done
                //   to see the effects of the GC call here when system is busy.
                //System.gc();

                mActivePhone = MSimPhoneFactory.getMSimCdmaPhone(mSubscription);
                // --msim--: TODO: need this??
                ((MSimGSMPhone)oldPhone).removeReferences();
                oldPhone = null;
            } else {
                logd("Make a new GSMPhone and destroy the old CDMAPhone.");

                ((MSimCDMAPhone)mActivePhone).dispose();
                //mActivePhone = null;
                Phone oldPhone = mActivePhone;

                // Give the GC a hint to start the garbage collection asap
                // NOTE this has been disabled since radio technology change could happen during
                //   e.g. a multimedia playing and could slow the system. Tests needs to be done
                //   to see the effects of the GC call here when system is busy.
                //System.gc();

                mActivePhone = MSimPhoneFactory.getMSimGsmPhone(mSubscription);
                ((MSimCDMAPhone)oldPhone).removeReferences();
                oldPhone = null;
            }

            if (mResetModemOnRadioTechnologyChange) {
                logd("Resetting Radio");
                mCommandsInterface.setRadioPower(oldPowerState, null);
            }

            //Set the new interfaces in the proxy's
            mIccSmsInterfaceManagerProxy.setmIccSmsInterfaceManager(
                    mActivePhone.getIccSmsInterfaceManager());
            mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(
                    mActivePhone.getIccPhoneBookInterfaceManager());
            mPhoneSubInfoProxy.setmPhoneSubInfo(this.mActivePhone.getPhoneSubInfo());
            mCommandsInterface = ((PhoneBase)mActivePhone).mCM;

            sendBroadcastStickyIntent();
            break;
        default:
            super.handleMessage(msg);
            break;
        }
    }

    protected void sendBroadcastStickyIntent() {
        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(Phone.PHONE_NAME_KEY, mActivePhone.getPhoneName());
        intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, mSubscription);
        ActivityManagerNative.broadcastStickyIntent(intent, null);
    }

    public PhoneSubInfoProxy getPhoneSubInfoProxy(){
        return mPhoneSubInfoProxy;
    }

    public IccPhoneBookInterfaceManagerProxy getIccPhoneBookInterfaceManagerProxy() {
        return mIccPhoneBookInterfaceManagerProxy;
    }

    // --msim--: TODO: Need this?
    //public IccSmsInterfaceManager getIccSmsInterfaceManager() {
    //    return mIccSmsInterfaceManager;
    //}

    public IccFileHandler getIccFileHandler() {
        return ((MSimGSMPhone)mActivePhone).getIccFileHandler();
    }

    public int getSubscription() {
        return mSubscription;
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[MSimPhoneProxy(" + mSubscription + ")] " + msg);
    }
}
