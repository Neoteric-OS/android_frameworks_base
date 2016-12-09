package com.android.carrierdefaultreceivers;


import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.util.Log;

import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.telephony.CarrierConfigManager.KEY_CARRIER_APP_NO_WAKE_SIGNAL_CONFIG_STRING_ARRAY;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_APP_WAKE_SIGNAL_CONFIG_STRING_ARRAY;

public class CustomConfigManager {
    private static final String TAG = "CustomConfigLoader";
    // TODO should we save to shared preference?

    // A list of supported actions from default carrier apps
    private static final String ACTION_SHOW_PORTAL_NOTIFICATION = "show_portal_notification";
    private static final String ACTION_SHOW_NO_DATA_NOTIFICATION = "show_no_data_notification";
    private static final String ACTION_DISABLE_METERED_APNS = "disable_metered_apn";
    private static final String ACTION_ENABLE_METERED_APNS = "enable_metered_apn";
    private static final String ACTION_DISABLE_RADIO = "disable_radio";
    private static final String ACTION_ENABLE_RADIO = "enable_radio";
    private static final String ACTION_LAUNCH_PORTAL = "launch_portal";
    private static final String ACTION_DISMISS_ALL_NOTIFICATIONS = "dismiss_all_notifications";

    // intent to carrier config key
    private static HashMap<String, String> sIntentToConfigKey = new HashMap<String, String>(){
        {
            put(TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED,
                    CarrierConfigManager.KEY_DEFAULT_APP_ACTIONS_ON_REDIRECTION_STRINGS);
            put(TelephonyIntents.ACTION_CARRIER_SIGNAL_PCO_VALUE,
                    CarrierConfigManager.KEY_DEFAULT_APP_ACTION_ON_PCO_STRINGS);
        }
    };

    public static List<String> loadCustomActions(Intent intent, Context context) {
        String configKey = sIntentToConfigKey.get(intent.getAction());
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        List<String> actionLists = new ArrayList<>();

        PersistableBundle b = null;
        if (configManager != null) {
            b = configManager.getConfig();
        }
        if (b != null) {
            String[] configs = b.getStringArray(configKey);


        }
        return actionLists;
    }

/*    public static String getValueFromIntent(Intent intent) {
        switch(intent.getAction()) {
            case TelephonyIntents.ACTION_CARRIER_SIGNAL_REDIRECTED:

        }
    }*/

    // static functions? everything is static??

    private static void logd(String s) {
        Log.d(TAG, s);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }
}

