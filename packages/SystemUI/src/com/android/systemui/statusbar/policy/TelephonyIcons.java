/*
 * Copyright (C) 2008 The Android Open Source Project
 * Portions Copyright (C) 2012-2013 Motorola Mobility LLC All Rights Reserved.
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

package com.android.systemui.statusbar.policy;

import com.android.systemui.R;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Config;
import android.util.Log;
import android.util.Xml;
import com.android.internal.util.XmlUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

class TelephonyIcons {

    static private String  TAG = "TelephonyIcons";
    static final boolean CHATTY =  Log.isLoggable( TAG, Log.VERBOSE ); // additional diagnostics, but not logspew
    static final boolean DEBUG = ( Log.isLoggable( TAG, Log.DEBUG ) || CHATTY );
    static final boolean INFO = ( true || DEBUG );

    private Context mContext;

    public TelephonyIcons( Context context ) {
        mContext = context;
    }

    protected String getResourceName(int resId) {
        if (resId == (-1) ) {
            return "__unset__";
        } else if (resId != 0) {
            final Resources res = mContext.getResources();
            try {
                // retain only the id portion (remove "com.android.systemui:drawable/")
                String var = res.getResourceName(resId);
                int delimIndex = var.indexOf('/');
                if(
                    delimIndex > 0
                    &&
                    delimIndex < var.length()
                ) {
                    var = var.substring(
                        delimIndex + 1,
                        var.length()
                    );
                }
                return var;
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(none)";
        }
    }


    /** @hide */
    public int getSignalStrengthIconId(
        int numBarsInSignalIcon,
        int iconLevel,
        int mobileInetCondition
    ) {
        if( numBarsInSignalIcon < TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( numBarsInSignalIcon > TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }
        if( iconLevel < 0 )
        {
            switch (numBarsInSignalIcon) {
                default:
                    return R.drawable.stat_sys_signal_null;
                case 5:
                    return  R.drawable.stat_sys_signal_5bar_null;
                case 6:
                    return  R.drawable.stat_sys_signal_6bar_null;
            }
        } else {

            int[] iconList;

            switch (numBarsInSignalIcon) {
                default:
                    iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH[mobileInetCondition];
                    break;
                case 5:
                    iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_5_BAR[mobileInetCondition];
                    break;
                case 6:
                    iconList = TelephonyIcons.TELEPHONY_SIGNAL_STRENGTH_6_BAR[mobileInetCondition];
                    break;
            }

            if( iconLevel > numBarsInSignalIcon ) iconLevel = numBarsInSignalIcon;
            if( iconLevel >= iconList.length ) iconLevel = iconList.length-1;

            return iconList[iconLevel];
        }
     }

    /** @hide */
    public int getSignalStrengthDescriptionId(
        int numBarsInSignalIcon,
        int iconLevel,
        int mobileInetCondition
    ) {
        if( numBarsInSignalIcon < TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( numBarsInSignalIcon > TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }
        if( iconLevel < 0 )
        {
            return 0;
        } else {
            if( iconLevel > numBarsInSignalIcon ) iconLevel = numBarsInSignalIcon;
            if( iconLevel < numBarsInSignalIcon )
            {
                if( iconLevel >= AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH.length ) {
                    iconLevel = AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH.length-1;
                }
                return AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[iconLevel];
            } else {
                return AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH[
                    (AccessibilityContentDescriptions.PHONE_SIGNAL_STRENGTH.length-1) ]
                ; // (not numBarsInSignalIcon) Last entry is "full".
            }
        }
     }

    /** @hide */
    public int getSignalStrengthNullIconId(
        int numBarsInSignalIcon
    ) {
        if( numBarsInSignalIcon < TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( numBarsInSignalIcon > TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }
        switch (numBarsInSignalIcon) {
            default:
                return R.drawable.stat_sys_signal_null;
            case 5:
                return R.drawable.stat_sys_signal_5bar_null;
            case 6:
                return R.drawable.stat_sys_signal_6bar_null;
        }
     }

    /** @hide */
    public int getSignalStrengthNullDescriptionId(
        int numBarsInSignalIcon
    ) {
        return 0;
    }

    /** @hide */
    public int getAirplaneModeIconId(
        int numBarsInSignalIcon
    ) {
        if( numBarsInSignalIcon < TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( numBarsInSignalIcon > TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }
        switch (numBarsInSignalIcon) {
                default:
                    return R.drawable.stat_sys_signal_flightmode;
                case 5:
                    return R.drawable.stat_sys_signal_5bar_flightmode;
                case 6:
                    return R.drawable.stat_sys_signal_6bar_flightmode;
        }
     }

    /** @hide */
    public int getAirplaneModeDescriptionId(
        int numBarsInSignalIcon
    ) {
        return R.string.accessibility_airplane_mode;
    }

    /** @hide */
    public int getEmergencyModeIconId(
        int numBarsInSignalIcon
    ) {
        if( numBarsInSignalIcon < TELEPHONY_SIGNAL_STRENGTH_MIN_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MIN_BARS;
        }
        if( numBarsInSignalIcon > TELEPHONY_SIGNAL_STRENGTH_MAX_BARS ) {
            numBarsInSignalIcon = TELEPHONY_SIGNAL_STRENGTH_MAX_BARS;
        }
        switch (numBarsInSignalIcon) {
                default:
                    return R.drawable.stat_sys_signal_emergency_only;
                case 5:
                    return R.drawable.stat_sys_signal_emergency_only;
                case 6:
                    return R.drawable.stat_sys_signal_emergency_only;
        }
     }

    /** @hide */
    public int getEmergencyModeDescriptionId(
        int numBarsInSignalIcon
    ) {
        return R.string.accessibility_emergency_calls_only;
    }


    static final int TELEPHONY_SIGNAL_STRENGTH_MIN_BARS = 4;
    static final int TELEPHONY_SIGNAL_STRENGTH_MAX_BARS = 6;

    // All radio types (4-bar icons)
    static final int[][] TELEPHONY_SIGNAL_STRENGTH = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4,
          R.drawable.stat_sys_signal_4, // Pad to 7 entries
          R.drawable.stat_sys_signal_4  // Pad to 7 entries
        },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully,
          R.drawable.stat_sys_signal_4_fully, // Pad to 7 entries
          R.drawable.stat_sys_signal_4_fully  // Pad to 7 entries
        }
    };

    //* @hide
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_5_BAR = {
        { R.drawable.stat_sys_signal_5bar_0,
          R.drawable.stat_sys_signal_5bar_1,
          R.drawable.stat_sys_signal_5bar_2,
          R.drawable.stat_sys_signal_5bar_3,
          R.drawable.stat_sys_signal_5bar_4,
          R.drawable.stat_sys_signal_5bar_5,
          R.drawable.stat_sys_signal_5bar_5 }, // Pad to 7 entries
        { R.drawable.stat_sys_signal_5bar_0_fully,
          R.drawable.stat_sys_signal_5bar_1_fully,
          R.drawable.stat_sys_signal_5bar_2_fully,
          R.drawable.stat_sys_signal_5bar_3_fully,
          R.drawable.stat_sys_signal_5bar_4_fully,
          R.drawable.stat_sys_signal_5bar_5_fully,
          R.drawable.stat_sys_signal_5bar_5_fully } // Pad to 7 entries
    };

    //* @hide
    static final int[][] TELEPHONY_SIGNAL_STRENGTH_6_BAR = {
        { R.drawable.stat_sys_signal_6bar_0,
          R.drawable.stat_sys_signal_6bar_1,
          R.drawable.stat_sys_signal_6bar_2,
          R.drawable.stat_sys_signal_6bar_3,
          R.drawable.stat_sys_signal_6bar_4,
          R.drawable.stat_sys_signal_6bar_5,
          R.drawable.stat_sys_signal_6bar_6 },
        { R.drawable.stat_sys_signal_6bar_0_fully,
          R.drawable.stat_sys_signal_6bar_1_fully,
          R.drawable.stat_sys_signal_6bar_2_fully,
          R.drawable.stat_sys_signal_6bar_3_fully,
          R.drawable.stat_sys_signal_6bar_4_fully,
          R.drawable.stat_sys_signal_6bar_5_fully,
          R.drawable.stat_sys_signal_6bar_6_fully }
    };

    //***** CDMA ERI icons

    static final int[] TELEPHONY_ROAMING_INDICATOR_CDMA = new int[] {
        // 0 is Standard Roaming Indicator
        R.drawable.stat_sys_roaming_cdma_0, //Standard Roaming Indicator

        // 1 is Standard Roaming Indicator OFF
        // TODO T: image never used, remove and put 0 instead?
        R.drawable.stat_sys_roaming_cdma_0,

        // 2 is Standard Roaming Indicator FLASHING
        // TODO T: image never used, remove and put 0 instead?
        R.drawable.stat_sys_roaming_cdma_flash,

        // 3 is Custom Roaming Indicator STATIC
        R.drawable.stat_sys_roaming_cdma_custom3
    };

    static final int[] TELEPHONY_ROAMING_INDICATOR_CDMA_FLASH = new int[] {
        // 0 is Standard Roaming Indicator
        R.drawable.stat_sys_roaming_cdma_0,

        // 1 is Standard Roaming Indicator OFF
        // TODO T: image never used, remove and put 0 instead?
        R.drawable.stat_sys_roaming_cdma_0,

        // 2 is Standard Roaming Indicator FLASHING
        R.drawable.stat_sys_roaming_cdma_flash,

        // 3 is Custom Roaming Indicator FLASHING
        R.drawable.stat_sys_roaming_cdma_custom3_flash
    };

    static final int[][] DATA_SIGNAL_STRENGTH = TELEPHONY_SIGNAL_STRENGTH;

    //***** Data connection icons

    //GSM/UMTS
    static final int[][] DATA_G = {
            { R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_connected_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_connected_g }
        };

    static final int[][] DATA_3G = {
            { R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_connected_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_connected_3g }
        };

    static final int[][] DATA_E = {
            { R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_connected_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_connected_e }
        };

    //3.5G
    static final int[][] DATA_H = {
            { R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_connected_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_connected_h }
    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    static final int[][] DATA_1X = {
            { R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_connected_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_connected_1x }
            };

    // LTE and eHRPD
    static final int[][] DATA_4G = {
            { R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g,
              R.drawable.stat_sys_data_connected_4g },
            { R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g,
              R.drawable.stat_sys_data_fully_connected_4g }
        };


    private TelephonyIcons() {
        // Initialize the lookup tables from XML data.
    }
}
