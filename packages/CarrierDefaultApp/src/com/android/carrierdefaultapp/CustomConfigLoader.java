/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.carrierdefaultapp;

import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.ArrayUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Default carrier app allows carrier customization. Different carriers could configure a list
 * of supported carrier actions to act upon certain signal or even different values of the same
 * signal type. This helper class loads and parses the carrier configs, returning a list of
 * predefined carrier action idx for the given input signal.
 */
public class CustomConfigLoader {
    // map from intent to carrier config key
    private static final HashMap<String, String> mIntentToConfigKeyMap;
    // map from intent to arg pair for some intents which allow different actions on
    // different values/args of the same signal type. used for config matching
    private static final HashMap<String, Pair<String, String>> mIntentToArgMap;

    // delimiters used to parse carrier config
    private static final String CARRIER_ACTION_DELIMITER = "\\s*,\\s*";
    private static final String INTENT_ARG_DELIMITER = "\\s*:\\s*";

    private static final String TAG = "CustomConfigLoader";
    private static final boolean VDBG = Rlog.isLoggable(TAG, Log.VERBOSE);

    static {
        // initialize the mapping from intent to carrier config key
        mIntentToConfigKeyMap = new HashMap<>();
        mIntentToConfigKeyMap.put(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED,
                CarrierConfigManager.KEY_CARRIER_DEFAULT_ACTIONS_ON_REDIRECTION_STRING_ARRAY);
        mIntentToConfigKeyMap.put(TelephonyIntents.ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED,
                CarrierConfigManager.KEY_CARRIER_DEFAULT_ACTIONS_ON_DCFAILURE_STRING_ARRAY);

        // initialize the mapping from intent to intent args
        mIntentToArgMap = new HashMap<>();
        mIntentToArgMap.put(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED, null);
        mIntentToArgMap.put(TelephonyIntents.ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED,
                new Pair<>(TelephonyIntents.EXTRA_APN_TYPE_KEY,
                        TelephonyIntents.EXTRA_ERROR_CODE_KEY));
    }

    // return a list of carrier action idx for the given signal based on the carrier config
    public static List<Integer> loadCarrierActionList(Context context, Intent intent) {
        CarrierConfigManager carrierConfigManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        String configKey = mIntentToConfigKeyMap.get(intent.getAction());
        if (TextUtils.isEmpty(configKey)) {
            Rlog.e(TAG, "load carrier config failure with wrong config key: " + configKey);
            return null;
        }
        if (carrierConfigManager == null) {
            Rlog.e(TAG, "load carrier config failure with carrier config manager uninitialized");
            return null;
        }
        PersistableBundle b = carrierConfigManager.getConfig();
        if (b != null) {
            String[] configs = b.getStringArray(configKey);
            // get args from the intent if defined in the intentToArgMap
            String arg1 = (mIntentToArgMap.get(intent) != null) ?
                    intent.getStringExtra(mIntentToArgMap.get(intent).first) : null;
            String arg2 = (mIntentToArgMap.get(intent) != null) ?
                    intent.getStringExtra(mIntentToArgMap.get(intent).second) : null;
            if (!ArrayUtils.isEmpty(configs)) {
                for (String config : configs) {
                    // parse each entry until find the entry with matching signal & args
                    List<Integer> actionList = parseConfig(config, arg1, arg2);
                    if (!actionList.isEmpty()) {
                        if (VDBG) Rlog.d(TAG, "found match action list: " + actionList.toString());
                        return actionList;
                    }
                }
            }
            Rlog.d(TAG, "no matching entry for signal: " + intent.getAction() + "arg1: " + arg1
                    + "arg2: " + arg2);
        }
        return null;
    }

    // passing arg1, arg2 should match the format of the config
    // case 1: config {actionIdx1, actionIdx2...} both arg1 and arg2 should be null
    // case 2: config {arg1, arg2 : actionIdx1, actionIdx2...} requires full match of non-null args
    // case 3: config {arg1 : actionIdx1, actionIdx2...} only need to match arg1
    private static List<Integer> parseConfig(String config, String arg1, String arg2) {
        String[] splitStr = config.trim().split(INTENT_ARG_DELIMITER, 2);
        String actionStr = null;

        if (splitStr.length == 1 && arg1 == null && arg2 == null) {
            // case 1
            actionStr = splitStr[0];
        } else if (splitStr.length == 2 && arg1 != null && arg2 != null) {
            // case 2
            String[] args = splitStr[0].split(CARRIER_ACTION_DELIMITER);
            if (args.length == 2 && TextUtils.equals(arg1, args[0]) &&
                    TextUtils.equals(arg2, args[1])) {
                actionStr = splitStr[1];
            }
        } else if ((splitStr.length == 2) && (arg1 != null) && (arg2 == null)) {
            // case 3
            String[] args = splitStr[0].split(CARRIER_ACTION_DELIMITER);
            if (args.length == 1 && TextUtils.equals(arg1, args[0])) {
                actionStr = splitStr[1];
            }
        }
        // convert from string -> action idx list if found a matching entry
        String[] actions = null;
        if (!TextUtils.isEmpty(actionStr)) {
            actions = actionStr.split(CARRIER_ACTION_DELIMITER);
        }
        if (!ArrayUtils.isEmpty(actions)) {
            List<Integer> ret = new ArrayList<>();
            for (String idx : actions) {
                try {
                    ret.add(Integer.parseInt(idx));
                } catch (NumberFormatException e) {
                    Rlog.e(TAG, "NumberFormatException on " + idx);
                    break;
                }
            }
            return ret;
        }
        return null;
    }
}
