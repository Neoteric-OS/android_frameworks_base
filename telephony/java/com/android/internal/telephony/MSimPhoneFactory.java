/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Context;
import android.net.LocalServerSocket;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.os.SystemProperties;

import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.cdma.CDMALTEPhone;
import com.android.internal.telephony.cdma.MSimCDMAPhone;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.gsm.MSimGSMPhone;
import com.android.internal.telephony.sip.SipPhone;
import com.android.internal.telephony.sip.SipPhoneFactory;

/**
 * {@hide}
 */
public class MSimPhoneFactory extends PhoneFactory {
    //***** Class Variables
    static private Phone []sProxyPhones = null;
    static private CommandsInterface []sCommandsInterfaces = null;

    static private boolean sMadeMultiSimDefaults = false;

    static private MSimProxyManager mMSimProxyManager;
    static private CardSubscriptionManager mCardSubscriptionManager;
    static private SubscriptionManager mSubscriptionManager;

    //***** Class Methods

    public static void makeMultiSimDefaultPhones(Context context) {
        makeMultiSimDefaultPhone(context);
    }

    /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeMultiSimDefaultPhone(Context context) {
        synchronized(Phone.class) {
            if (!sMadeMultiSimDefaults) {
                sLooper = Looper.myLooper();
                sContext = context;

                if (sLooper == null) {
                    throw new RuntimeException(
                        "MSimPhoneFactory.makeMultiSimDefaultPhone must be called from Looper thread");
                }

                int retryCount = 0;
                for(;;) {
                    boolean hasException = false;
                    retryCount ++;

                    try {
                        // use UNIX domain socket to
                        // prevent subsequent initialization
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (java.io.IOException ex) {
                        hasException = true;
                    }

                    if ( !hasException ) {
                        break;
                    } else if (retryCount > SOCKET_OPEN_MAX_RETRY) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    } else {
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                    }
                }

                sPhoneNotifier = new MSimDefaultPhoneNotifier();

                // Get preferred network mode
                int preferredNetworkMode = RILConstants.PREFERRED_NETWORK_MODE;
                if (BaseCommands.getLteOnCdmaModeStatic() == Phone.LTE_ON_CDMA_TRUE) {
                    preferredNetworkMode = Phone.NT_MODE_GLOBAL;
                }

                // TODO: for CDMA LTE and check the CDMA_SUBSCRIPTION_MODE
                //Get cdmaSubscription mode from Settings.System
                int cdmaSubscription = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.PREFERRED_CDMA_SUBSCRIPTION,
                        preferredCdmaSubscription);
                Log.i(LOG_TAG, "Cdma Subscription set to " + cdmaSubscription);

                /* In case of multi SIM mode two instances of PhoneProxy, RIL are created,
                   where as in single SIM mode only instance. isMultiSimEnabled() function checks
                   whether it is single SIM or multi SIM mode */
                int numPhones = TelephonyManager.getDefault().getPhoneCount();
                int[] networkModes = new int[numPhones];
                sProxyPhones = new MSimPhoneProxy[numPhones];
                sCommandsInterfaces = new RIL[numPhones];

                for (int i = 0; i < numPhones; i++) {
                    //reads the system properties and makes commandsinterface
                    try {
                        networkModes[i]  = Settings.Secure.getIntAtIndex(
                                context.getContentResolver(),
                                Settings.Secure.PREFERRED_NETWORK_MODE, i);
                    } catch (SettingNotFoundException snfe) {
                        Log.e(LOG_TAG, "Settings Exception Reading Value At Index", snfe);
                        networkModes[i] = preferredNetworkMode;
                    }
                    Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkModes[i]));
                    sCommandsInterfaces[i] = new RIL(context, networkModes[i],
                            cdmaSubscription, i);
                }

                mCardSubscriptionManager = CardSubscriptionManager.getInstance(context, sCommandsInterfaces);
                mSubscriptionManager = SubscriptionManager.getInstance(context, sCommandsInterfaces);

                for (int i = 0; i < numPhones; i++) {
                    int phoneType = getPhoneType(networkModes[i]);
                    if (phoneType == Phone.PHONE_TYPE_GSM) {
                        Log.i(LOG_TAG, "Creating MSimGSMPhone sub = " + i);
                        sProxyPhones[i] = new MSimPhoneProxy(new MSimGSMPhone(context,
                                sCommandsInterfaces[i], sPhoneNotifier, i));
                    } else if (phoneType == Phone.PHONE_TYPE_CDMA) {
                        Log.i(LOG_TAG, "Creating MSimCDMAPhone sub = " + i);
                        sProxyPhones[i] = new MSimPhoneProxy(new MSimCDMAPhone(context,
                                sCommandsInterfaces[i], sPhoneNotifier, i));
                    }
                }
                mMSimProxyManager = MSimProxyManager.getInstance(context, sProxyPhones, sCommandsInterfaces);

                sMadeMultiSimDefaults = true;

                // Set the default phone in base class
                sProxyPhone = sProxyPhones[MSimConstants.DEFAULT_SUBSCRIPTION];
                sCommandsInterface = sCommandsInterfaces[MSimConstants.DEFAULT_SUBSCRIPTION];
                sMadeDefaults = true;
            }
        }
    }

    public static Phone getMSimCdmaPhone(int subscription) {
        Phone phone;
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            phone = new MSimCDMAPhone(sContext, sCommandsInterfaces[subscription],
                    sPhoneNotifier, subscription);
        }
        return phone;
    }

    public static Phone getMSimGsmPhone(int subscription) {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            Phone phone = new MSimGSMPhone(sContext, sCommandsInterfaces[subscription],
                    sPhoneNotifier, subscription);
            return phone;
        }
    }

    public static Phone getPhone(int subscription) {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "MSimPhoneFactory.getPhone must be called from Looper thread");
        }
        if (!sMadeMultiSimDefaults) {
            if (!sMadeDefaults) {
                throw new IllegalStateException("Default phones haven't been made yet!");
            } else if (subscription == MSimConstants.DEFAULT_SUBSCRIPTION) {
                return sProxyPhone;
            }
        }
        return sProxyPhones[subscription];
    }

    public static int getDefaultSubscription() {
        // --msim--: TODO
        return 0;
    }

    public static void setDefaultSubscription(int sub) {
        // --msim--: TODO
    }

    public static int getDataSubscription() {
        // --msim--: TODO
        return 0;
    }

    public static void setDataSubscription(int sub) {
        // --msim--: TODO
    }

    public static int getVoiceSubscription() {
        // --msim--: TODO
        return 0;
    }

    public static void setVoiceSubscription(int sub) {
        // --msim--: TODO
    }

    public static int getSMSSubscription() {
        // --msim--: TODO
        return 0;
    }

    public static void setSMSSubscription(int sub) {
        // --msim--: TODO
    }


}
