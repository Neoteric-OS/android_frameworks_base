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

 // -----Carrier+Type+Roaming to IconSuite subtable-----
 // 1.) Lookup table by carrier:
 // "@null"; no string
 // "att"
 // "bellca"
 // "fido"
 // "kt"
 // "orange"
 // "rogers"
 // "sfr"
 // "skt"
 // "telus"
 // "tmo"
 // "tmo-intl"
 // "vzw"

 // 2.) within carrier: index by lookupMobileDataTypeKey to get IconSuite subtable:
    private final int  LOOKUP_DATA_TYPE_UNKNOWN                    = 0;
    private final int  LOOKUP_DATA_TYPE_GPRS                       = 1;
    private final int  LOOKUP_DATA_TYPE_EDGE                       = 2;
    private final int  LOOKUP_DATA_TYPE_UMTS                       = 3;
    private final int  LOOKUP_DATA_TYPE_H                          = 4;
    private final int  LOOKUP_DATA_TYPE_H__H_DISTINGUISHED         = 5;
    private final int  LOOKUP_DATA_TYPE_HPLUS                      = 6;
    private final int  LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED     = 7;
    private final int  LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED = 8;
    private final int  LOOKUP_DATA_TYPE_CDMA                       = 9;
    private final int  LOOKUP_DATA_TYPE_1xRTT                      = 10;
    private final int  LOOKUP_DATA_TYPE_EVDO                       = 11;
    private final int  LOOKUP_DATA_TYPE_EHRPD                      = 12;
    private final int  LOOKUP_DATA_TYPE_LTE                        = 13;
    private final int  LOOKUP_DATA_TYPE_IDEN                       = 14;
    private final int  LOOKUP_DATA_TYPE_INVALID                    = 15;

    private final int  LOOKUP_ROAMING_OFFSET = LOOKUP_DATA_TYPE_INVALID + 1;

    private final int  LOOKUP_DATA_TYPE_UNKNOWN__ROAMING                    = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_UNKNOWN;
    private final int  LOOKUP_DATA_TYPE_GPRS__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_GPRS;
    private final int  LOOKUP_DATA_TYPE_EDGE__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_EDGE;
    private final int  LOOKUP_DATA_TYPE_UMTS__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_UMTS;
    private final int  LOOKUP_DATA_TYPE_H__ROAMING                          = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_H;
    private final int  LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING         = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_H__H_DISTINGUISHED;
    private final int  LOOKUP_DATA_TYPE_HPLUS__ROAMING                      = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_HPLUS;
    private final int  LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING     = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED;
    private final int  LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED;
    private final int  LOOKUP_DATA_TYPE_CDMA__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_CDMA;
    private final int  LOOKUP_DATA_TYPE_1xRTT__ROAMING                      = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_1xRTT;
    private final int  LOOKUP_DATA_TYPE_EVDO__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_EVDO;
    private final int  LOOKUP_DATA_TYPE_EHRPD__ROAMING                      = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_EHRPD;
    private final int  LOOKUP_DATA_TYPE_LTE__ROAMING                        = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_LTE;
    private final int  LOOKUP_DATA_TYPE_IDEN__ROAMING                       = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_IDEN;
    private final int  LOOKUP_DATA_TYPE_INVALID__ROAMING                    = LOOKUP_ROAMING_OFFSET + LOOKUP_DATA_TYPE_INVALID;

    private final int  LOOKUP_DATA_TYPE_ARRAY_SIZE  = LOOKUP_DATA_TYPE_INVALID__ROAMING + 1;



 // ------IconSet subtables------
 // 3.) vector to MobileDataTypeIcon & MobileDataTypeDescription based on following dataState key
 // ---networkStateKey---
 // generic/vzw/sprint ; att_waves
    private final int  LOOKUP_DATA_STATE_UNKNOWN = 0; // Unknown connection state or not connected
    private final int  LOOKUP_DATA_STATE_DISABLED = 1; // Show circle/slash "Disabled" icon; (same).
    private final int  LOOKUP_DATA_STATE_DISCONNECTED = 2; // Show "no icon"; (same).
    private final int  LOOKUP_DATA_STATE_ATTACHED = 3; // Show dimmed "Attached" icon; no waves_base icon.
    private final int  LOOKUP_DATA_STATE_CONNECTING = 4; // Show dimmed "Connecting" icon; dimmed waves_base icon.
    private final int  LOOKUP_DATA_STATE_SUSPENDED = 5; // Show dimmed "Suspended" icon (Wifi active, Data suspended); no waves_base icon.
    private final int  LOOKUP_DATA_STATE_CONNECTED = 6; // Show near-white "Connected" icon; near-with waves_base icon.
    private final int  LOOKUP_DATA_STATE_FULLY_CONNECTED = 7; // Show aqua "Fully-connected" icon; aqua waves_base icon.

    private final int  LOOKUP_DATA_STATE_ARRAY_SIZE = 8; // Show aqua "Fully-connected" icon; aqua waves_base icon.

 // 4.) vector to MobileDataActivityIcon & MobileDataActivityDescription based on following dataActivity key
 // ---mobileDataActivityKey---
    private final int  LOOKUP_DATA_ACTIVITY_UNKNOWN = 0;
    private final int  LOOKUP_DATA_ACTIVITY_DISABLED = 1; // Show circle/slash "Disabled" icon; (same).
    private final int  LOOKUP_DATA_ACTIVITY_DISCONNECTED = 2; // show "no icon"
    private final int  LOOKUP_DATA_ACTIVITY_ATTACHED = 3; // Show dimmed "Attached" icon; no waves_base icon.
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTING = 4; // Show dimmed "Connecting" icon; dimmed waves_base icon.
    private final int  LOOKUP_DATA_ACTIVITY_SUSPENDED = 5; // Show dimmed "Suspended" icon (Wifi active, Data suspended); no waves_base icon.
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTED_DORMANT = 6; // Show both dimmed icon; near-white "cloud" waves
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTED_IDLE = 7; // show both dimmed icon; near-white "cloud" waves
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTED_IN = 8; // Show near-white in, dimmed out icon; near-white waves in
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTED_OUT = 9; // Show dimmed-in, near-white out icon; near-white waves out
    private final int  LOOKUP_DATA_ACTIVITY_CONNECTED_INOUT = 10; // Show near-white in, near-white out icon; near-white waves in/out
    private final int  LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_DORMANT = 11; // Show both dimmed icon; near-white "cloud" waves
    private final int  LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IDLE = 12; // Show both dimmed icon; near-white "cloud" waves
    private final int  LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IN = 13; // Show aqua in, dimmed out icon; aqua waves in
    private final int  LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_OUT = 14; // Show dimmed-in, aqua out icon; aqua waves out
    private final int  LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_INOUT = 15; // Show aqua in, aqua out icon; aqua waves in/out

    private final int  LOOKUP_DATA_ACTIVITY_ARRAY_SIZE = 16; // Show both dimmed icon; near-white "cloud" waves


    private final String XML_TAG__TELEPHONY_ICON_MAPPINGS                     = "TelephonyIconMappings";
    private final String XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS
        = "http://schemas.android.com/apk/res/com.android.systemui";

    private final String XML_TAG__ICON_SUITE                                  = "IconSuite";
    private final String XML_ATTR__ICON_SUITE__CARRIER_NAME                   = "CarrierName";
    private final String XML_ATTR_VALUE__ICON_SUITE__CARRIER_NAME__DEFAULT    = "_DEFAULT_GENERIC_";

    private final String XML_TAG__ICON_SET                                    = "IconSet";
    private final String XML_ATTR__ICON_SET__DATA_TYPE_KEY                 = "LookupMobileDataTypeKey";

    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN
        = "LOOKUP_DATA_TYPE_UNKNOWN";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS
        = "LOOKUP_DATA_TYPE_GPRS";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE
        = "LOOKUP_DATA_TYPE_EDGE";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS
        = "LOOKUP_DATA_TYPE_UMTS";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H
        = "LOOKUP_DATA_TYPE_H";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED
        = "LOOKUP_DATA_TYPE_H__H_DISTINGUISHED";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS
        = "LOOKUP_DATA_TYPE_HPLUS";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED
        = "LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED
        = "LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA
        = "LOOKUP_DATA_TYPE_CDMA";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT
        = "LOOKUP_DATA_TYPE_1xRTT";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO
        = "LOOKUP_DATA_TYPE_EVDO";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD
        = "LOOKUP_DATA_TYPE_EHRPD";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE
        = "LOOKUP_DATA_TYPE_LTE";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN
        = "LOOKUP_DATA_TYPE_IDEN";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID
        = "LOOKUP_DATA_TYPE_INVALID";

    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN__ROAMING
        = "LOOKUP_DATA_TYPE_UNKNOWN__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS__ROAMING
        = "LOOKUP_DATA_TYPE_GPRS__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE__ROAMING
        = "LOOKUP_DATA_TYPE_EDGE__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS__ROAMING
        = "LOOKUP_DATA_TYPE_UMTS__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__ROAMING
        = "LOOKUP_DATA_TYPE_H__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED__ROAMING
        = "LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__ROAMING
        = "LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED__ROAMING
        = "LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED__ROAMING
        = "LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA__ROAMING
        = "LOOKUP_DATA_TYPE_CDMA__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT__ROAMING
        = "LOOKUP_DATA_TYPE_1xRTT__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO__ROAMING
        = "LOOKUP_DATA_TYPE_EVDO__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD__ROAMING
        = "LOOKUP_DATA_TYPE_EHRPD__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE__ROAMING
        = "LOOKUP_DATA_TYPE_LTE__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN__ROAMING
        = "LOOKUP_DATA_TYPE_IDEN__ROAMING";
    private final String  XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID__ROAMING
        = "LOOKUP_DATA_TYPE_INVALID__ROAMING";

    private final String XML_ATTR__ICON_SET__ICON_UNKNOWN                     = "IconDataStateUnknown";
    private final String XML_ATTR__ICON_SET__ICON_DISABLED                    = "IconDataStateDisabled";
    private final String XML_ATTR__ICON_SET__ICON_DISCONNECTED                = "IconDataStateDisconnected";
    private final String XML_ATTR__ICON_SET__ICON_ATTACHED                    = "IconDataStateAttached";
    private final String XML_ATTR__ICON_SET__ICON_CONNECTING                  = "IconDataStateConnecting";
    private final String XML_ATTR__ICON_SET__ICON_SUSPENDED                   = "IconDataStateSuspended";
    private final String XML_ATTR__ICON_SET__ICON_CONNECTED                   = "IconDataStateConnected";
    private final String XML_ATTR__ICON_SET__ICON_FULLY_CONNECTED             = "IconDataStateFullyConnected";

    private final String XML_ATTR__ICON_SET__DESC_UNKNOWN                     = "DescriptionDataStateUnknown";
    private final String XML_ATTR__ICON_SET__DESC_DISABLED                    = "DescriptionDataStateDisabled";
    private final String XML_ATTR__ICON_SET__DESC_DISCONNECTED                = "DescriptionDataStateDisconnected";
    private final String XML_ATTR__ICON_SET__DESC_ATTACHED                    = "DescriptionDataStateAttached";
    private final String XML_ATTR__ICON_SET__DESC_CONNECTING                  = "DescriptionDataStateConnecting";
    private final String XML_ATTR__ICON_SET__DESC_SUSPENDED                   = "DescriptionDataStateSuspended";
    private final String XML_ATTR__ICON_SET__DESC_CONNECTED                   = "DescriptionDataStateConnected";
    private final String XML_ATTR__ICON_SET__DESC_FULLY_CONNECTED             = "DescriptionDataStateFullyConnected";

    private final String XML_ATTR__ICON_SET__ACTIVITY_UNKNOWN                 = "IconDataActivityUnknown";
    private final String XML_ATTR__ICON_SET__ACTIVITY_DISABLED                = "IconDataActivityDisabled";
    private final String XML_ATTR__ICON_SET__ACTIVITY_DISCONNECTED            = "IconDataActivityDisconnected";
    private final String XML_ATTR__ICON_SET__ACTIVITY_ATTACHED                = "IconDataActivityAttached";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTING              = "IconDataActivityConnecting";
    private final String XML_ATTR__ICON_SET__ACTIVITY_SUSPENDED               = "IconDataActivitySuspended";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IDLE          = "IconDataActivityConnectedIdle";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IN            = "IconDataActivityConnectedIn";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_OUT           = "IconDataActivityConnectedOut";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_INOUT         = "IconDataActivityConnectedInOut";
    private final String XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_DORMANT       = "IconDataActivityConnectedDormant";
    private final String XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IDLE    = "IconDataActivityFullyConnectedIdle";
    private final String XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IN      = "IconDataActivityFullyConnectedIn";
    private final String XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_OUT     = "IconDataActivityFullyConnectedOut";
    private final String XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_INOUT   = "IconDataActivityFullyConnectedInOut";
    private final String XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_DORMANT = "IconDataActivityFullyConnectedDormant";

    private Context mContext;

    private IconSuiteArray mIconSuiteInfo = null;
    private IconSet mChosenIconSet = null;

    public TelephonyIcons( Context context ) {
        mContext = context;
        mIconSuiteInfo = loadXMLDataFromFile();
    }

    private IconSuiteArray loadXMLDataFromFile() {
        FileInputStream stream = null;
        Resources res = mContext.getResources();
        XmlResourceParser parser= res.getXml(R.xml.telephony_icon_mappings);
        String varCarrierNameString = null;
        int varMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID;
        int varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_INVALID;
        IconSuite varCurrentIconSuite = null;

        IconSuiteArray varIconSuiteArray = null;

        try {
            XmlUtils.beginDocument(
                parser,
                XML_TAG__TELEPHONY_ICON_MAPPINGS
            );

            varIconSuiteArray = new IconSuiteArray();

            while (true) {
                XmlUtils.nextElement(parser);
                String elementName = parser.getName();
                if( CHATTY ) {
                    Log.v( TAG, "loadXMLDataFromFile: Element=\"" + elementName + "\"" );
                }
                if (elementName == null) {
                    break;
                } else if (elementName.equals(XML_TAG__ICON_SUITE)) {
                    if( varCurrentIconSuite != null ) {
                        if( CHATTY ) {
                            Log.v( TAG, "loadXMLDataFromFile: dump previously completed IconSuite" );
                            varCurrentIconSuite.debugPrint( "loadXMLDataFromFile: -> " );
                        }
                    } else {
                        if( CHATTY ) {
                            Log.v( TAG, "loadXMLDataFromFile: first pass, no prior IconSuite to dump" );
                        }
                    }
                    varCarrierNameString = parser.getAttributeValue(
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SUITE__CARRIER_NAME
                    );
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SUITE__CARRIER_NAME + "\""
                            + " value=\"" + varCarrierNameString + "\""
                            );
                    }
                    varCurrentIconSuite = varIconSuiteArray.createDefaultSuite( varCarrierNameString );
                } else if (elementName.equals(XML_TAG__ICON_SET)) {

                    varMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID;
                    varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_INVALID__ROAMING;

                    String mobileDataTypeKeyString = parser.getAttributeValue(
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DATA_TYPE_KEY
                    );

                    if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_UNKNOWN;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_UNKNOWN__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_GPRS;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_GPRS__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EDGE;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_EDGE__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_H;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_H__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_HPLUS__ROAMING;
                   } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_CDMA;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_CDMA__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_1xRTT;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_1xRTT__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_EVDO__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EHRPD;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_EHRPD__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_LTE;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_LTE__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_IDEN;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_IDEN__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID;
                        varMobileDataTypeRoamingKey = LOOKUP_DATA_TYPE_INVALID__ROAMING;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_UNKNOWN__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_GPRS__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                   } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EDGE__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_H__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_CDMA__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_1xRTT__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_EHRPD__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_LTE__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_IDEN__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    } else if (
                        mobileDataTypeKeyString.equals(
                            XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID__ROAMING
                        )
                    ) {
                        varMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID__ROAMING;
                        varMobileDataTypeRoamingKey = varMobileDataTypeKey;
                    }

                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DATA_TYPE_KEY + "\""
                            + " value=\"" + mobileDataTypeKeyString + "\""
                            + " key (int)=" + varMobileDataTypeKey
                            );
                    }

                    varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setCarrierNameString(
                        varCarrierNameString
                    );
                    if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setCarrierNameString(
                            varCarrierNameString
                        );
                    }

                    //  --- process other attributes of IconSet element ---
                    int iconUnknown = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_UNKNOWN
                    );
                    if( iconUnknown != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_UNKNOWN,
                            iconUnknown
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_UNKNOWN,
                                iconUnknown
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_UNKNOWN + "\""
                            + " resourceId=0x" + Integer.toHexString( iconUnknown )
                            + "/" + getResourceName( iconUnknown )
                           );
                    }

                    int iconDisabled = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_DISABLED
                    );
                    if( iconDisabled != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_DISABLED,
                            iconDisabled
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_DISABLED,
                                iconDisabled
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_DISABLED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconDisabled )
                            + "/" + getResourceName( iconDisabled )
                            );
                    }

                    int iconDisconnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_DISCONNECTED
                    );
                    if( iconDisconnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_DISCONNECTED,
                            iconDisconnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_DISCONNECTED,
                                iconDisconnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_DISCONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconDisconnected )
                            + "/" + getResourceName( iconDisconnected )
                            );
                    }

                    int iconConnecting = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_CONNECTING
                    );
                    if( iconConnecting != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_CONNECTING,
                            iconConnecting
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_CONNECTING,
                                iconConnecting
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_CONNECTING + "\""
                            + " resourceId=0x" + Integer.toHexString( iconConnecting )
                            + "/" + getResourceName( iconConnecting )
                            );
                    }

                    int iconAttached = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_ATTACHED
                    );
                    if( iconAttached != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_ATTACHED,
                            iconAttached
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_ATTACHED,
                                iconAttached
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_ATTACHED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconAttached )
                            + "/" + getResourceName( iconAttached )
                            );
                    }

                    int iconSuspended = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_SUSPENDED
                    );
                    if( iconSuspended != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_SUSPENDED,
                            iconSuspended
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_SUSPENDED,
                                iconSuspended
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_SUSPENDED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconSuspended )
                            + "/" + getResourceName( iconSuspended )
                            );
                    }

                    int iconConnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_CONNECTED
                    );
                    if( iconConnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_CONNECTED,
                            iconConnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_CONNECTED,
                                iconConnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_CONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconConnected )
                            + "/" + getResourceName( iconConnected )
                            );
                    }

                    int iconFullyConnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ICON_FULLY_CONNECTED
                    );
                    if( iconFullyConnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeIconId(
                            LOOKUP_DATA_STATE_FULLY_CONNECTED,
                            iconFullyConnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeIconId(
                                LOOKUP_DATA_STATE_FULLY_CONNECTED,
                                iconFullyConnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ICON_FULLY_CONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( iconFullyConnected )
                            + "/" + getResourceName( iconFullyConnected )
                            );
                    }


                    int descriptionUnknown = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_UNKNOWN
                    );
                    if( descriptionUnknown != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_UNKNOWN,
                            descriptionUnknown
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_UNKNOWN,
                                descriptionUnknown
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_UNKNOWN + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionUnknown )
                            + "/" + getResourceName( descriptionUnknown )
                            );
                    }

                    int descriptionDisabled = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_DISABLED
                    );
                    if( descriptionDisabled != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_DISABLED,
                            descriptionDisabled
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_DISABLED,
                                descriptionDisabled
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_DISABLED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionDisabled )
                            + "/" + getResourceName( descriptionDisabled )
                            );
                    }

                    int descriptionDisconnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_DISCONNECTED
                    );
                    if( descriptionDisconnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_DISCONNECTED,
                            descriptionDisconnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_DISCONNECTED,
                                descriptionDisconnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_DISCONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionDisconnected )
                            + "/" + getResourceName( descriptionDisconnected )
                            );
                    }

                    int descriptionConnecting = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_CONNECTING
                    );
                    if( descriptionConnecting != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_CONNECTING,
                            descriptionConnecting
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_CONNECTING,
                                descriptionConnecting
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_CONNECTING + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionConnecting )
                            + "/" + getResourceName( descriptionConnecting )
                            );
                    }

                    int descriptionAttached = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_ATTACHED
                    );
                    if( descriptionAttached != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_ATTACHED,
                            descriptionAttached
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_ATTACHED,
                                descriptionAttached
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_ATTACHED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionAttached )
                            + "/" + getResourceName( descriptionAttached )
                            );
                    }

                    int descriptionSuspended = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_SUSPENDED
                    );
                    if( descriptionSuspended != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_SUSPENDED,
                            descriptionSuspended
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_SUSPENDED,
                                descriptionSuspended
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_SUSPENDED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionSuspended )
                            + "/" + getResourceName( descriptionSuspended )
                            );
                    }

                    int descriptionConnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_CONNECTED
                    );
                    if( descriptionConnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_CONNECTED,
                            descriptionConnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_CONNECTED,
                                descriptionConnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_CONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionConnected )
                            + "/" + getResourceName( descriptionConnected )
                            );
                    }

                    int descriptionFullyConnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__DESC_FULLY_CONNECTED
                    );
                    if( descriptionFullyConnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataTypeDescriptionId(
                            LOOKUP_DATA_STATE_FULLY_CONNECTED,
                            descriptionFullyConnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataTypeDescriptionId(
                                LOOKUP_DATA_STATE_FULLY_CONNECTED,
                                descriptionFullyConnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__DESC_FULLY_CONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( descriptionFullyConnected )
                            + "/" + getResourceName( descriptionFullyConnected )
                            );
                    }

                    int activityUnknown = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_UNKNOWN
                    );
                    if( activityUnknown != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_UNKNOWN,
                            activityUnknown
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_UNKNOWN,
                                activityUnknown
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_UNKNOWN + "\""
                            + " resourceId=0x" + Integer.toHexString( activityUnknown )
                            + "/" + getResourceName( activityUnknown )
                            );
                    }

                    int activityDisabled = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_DISABLED
                    );
                    if( activityDisabled != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_DISABLED,
                            activityDisabled
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_DISABLED,
                                activityDisabled
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_DISABLED + "\""
                            + " resourceId=0x" + Integer.toHexString( activityDisabled )
                            + "/" + getResourceName( activityDisabled )
                            );
                    }

                    int activityDisconnected = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_DISCONNECTED
                    );
                    if( activityDisconnected != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_DISCONNECTED,
                            activityDisconnected
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_DISCONNECTED,
                                activityDisconnected
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_DISCONNECTED + "\""
                            + " resourceId=0x" + Integer.toHexString( activityDisconnected )
                            + "/" + getResourceName( activityDisconnected )
                            );
                    }

                    int activityConnecting = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTING
                    );
                    if( activityConnecting != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTING,
                            activityConnecting
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTING,
                                activityConnecting
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTING + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnecting )
                            + "/" + getResourceName( activityConnecting )
                            );
                    }

                    int activityAttached = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_ATTACHED
                    );
                    if( activityAttached != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_ATTACHED,
                            activityAttached
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_ATTACHED,
                                activityAttached
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_ATTACHED + "\""
                            + " resourceId=0x" + Integer.toHexString( activityAttached )
                            + "/" + getResourceName( activityAttached )
                            );
                    }

                    int activitySuspended = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_SUSPENDED
                    );
                    if( activitySuspended != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_SUSPENDED,
                            activitySuspended
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_SUSPENDED,
                                activitySuspended
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_SUSPENDED + "\""
                            + " resourceId=0x" + Integer.toHexString( activitySuspended )
                            + "/" + getResourceName( activitySuspended )
                            );
                    }

                    int activityConnectedIdle = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IDLE
                    );
                    if( activityConnectedIdle != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTED_IDLE,
                            activityConnectedIdle
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTED_IDLE,
                                activityConnectedIdle
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IDLE + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnectedIdle )
                            + "/" + getResourceName( activityConnectedIdle )
                            );
                    }

                    int activityConnectedIn = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IN
                    );
                    if( activityConnectedIn != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTED_IN,
                            activityConnectedIn
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTED_IN,
                                activityConnectedIn
                            );
                            }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_IN + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnectedIn )
                            + "/" + getResourceName( activityConnectedIn )
                            );
                    }

                    int activityConnectedOut = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_OUT
                    );
                    if( activityConnectedOut != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTED_OUT,
                            activityConnectedOut
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTED_OUT,
                                activityConnectedOut
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_OUT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnectedOut )
                            + "/" + getResourceName( activityConnectedOut )
                            );
                    }

                    int activityConnectedInOut = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_INOUT
                    );
                    if( activityConnectedInOut != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTED_INOUT,
                            activityConnectedInOut
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTED_INOUT,
                                activityConnectedInOut
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_INOUT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnectedInOut )
                            + "/" + getResourceName( activityConnectedInOut )
                            );
                    }

                    int activityConnectedDormant = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_DORMANT
                    );
                    if( activityConnectedDormant != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_CONNECTED_DORMANT,
                            activityConnectedDormant
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_CONNECTED_DORMANT,
                                activityConnectedDormant
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_CONNECTED_DORMANT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityConnectedDormant )
                            + "/" + getResourceName( activityConnectedDormant )
                            );
                    }

                    int activityFullyConnectedIdle = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IDLE
                    );
                    if( activityFullyConnectedIdle != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IDLE,
                            activityFullyConnectedIdle
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IDLE,
                                activityFullyConnectedIdle
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IDLE + "\""
                            + " resourceId=0x" + Integer.toHexString( activityFullyConnectedIdle )
                            + "/" + getResourceName( activityFullyConnectedIdle )
                            );
                    }

                    int activityFullyConnectedIn = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IN
                    );
                    if( activityFullyConnectedIn != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IN,
                            activityFullyConnectedIn
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                        }
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IN,
                                activityFullyConnectedIn
                            );
                     }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_IN + "\""
                            + " resourceId=0x" + Integer.toHexString( activityFullyConnectedIn )
                            + "/" + getResourceName( activityFullyConnectedIn )
                            );
                    }

                    int activityFullyConnectedOut = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_OUT
                    );
                    if( activityFullyConnectedOut != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_OUT,
                            activityFullyConnectedOut
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_OUT,
                                activityFullyConnectedOut
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_OUT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityFullyConnectedOut )
                            + "/" + getResourceName( activityFullyConnectedOut )
                            );
                    }

                    int activityFullyConnectedInOut = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_INOUT
                    );
                    if( activityFullyConnectedInOut != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_INOUT,
                            activityFullyConnectedInOut
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_INOUT,
                                activityFullyConnectedInOut
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_INOUT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityFullyConnectedInOut )
                            + "/" + getResourceName( activityFullyConnectedInOut )
                            );
                    }

                    int activityFullyConnectedDormant = parseResourceId(
                        parser,
                        XML_NAMESPACE__TELEPHONY_ICON_MAPPINGS,
                        XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_DORMANT
                    );
                    if( activityFullyConnectedDormant != (-1) ) {
                        varCurrentIconSuite.getIconSet( varMobileDataTypeKey ).setMobileDataActivityIconId(
                            LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_DORMANT,
                            activityFullyConnectedDormant
                        );
                        if( varMobileDataTypeKey != varMobileDataTypeRoamingKey ) {
                            varCurrentIconSuite.getIconSet( varMobileDataTypeRoamingKey ).setMobileDataActivityIconId(
                                LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_DORMANT,
                                activityFullyConnectedDormant
                            );
                        }
                    }
                    if( CHATTY ) {
                        Log.v( TAG,
                            "loadXMLDataFromFile:   "
                            + "Attr=\"" + XML_ATTR__ICON_SET__ACTIVITY_FULLY_CONNECTED_DORMANT + "\""
                            + " resourceId=0x" + Integer.toHexString( activityFullyConnectedDormant )
                            + "/" + getResourceName( activityFullyConnectedDormant )
                            );
                    }

                }
            }
            if( varCurrentIconSuite != null ) {
                if( CHATTY ) {
                    Log.v( TAG,
                        "loadXMLDataFromFile: dump the final completed IconSuite"
                    );
                    varCurrentIconSuite.debugPrint( "loadXMLDataFromFile: -> " );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Got exception while loading TelephonyIconsMapping file.", e);
        } finally {
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser)parser).close();
            }
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }

        return varIconSuiteArray;
    }

    private int parseResourceId(
        XmlResourceParser parser,
        String namespace,
        String attributeName
    ) {
        int returnValue = (-1);
        String attrString = parser.getAttributeValue(
            namespace,
            attributeName
        );
        if ( attrString == null ) {
            returnValue = (-1);
        } else if ( attrString.equals("0") ) {
            returnValue = 0;
        } else {
            returnValue = parser.getAttributeResourceValue(
                namespace,
                attributeName,
                (-1)
            );
        }
        return returnValue;
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

    /** @hide */
    public IconSet chooseActiveIconSet(
        String requestedCarrierName,
        int connectionStateMobileNetType,
        boolean roaming,
        boolean hspaDistinguishable,
        boolean hspapDistinguishable,
        boolean showAtLeast3G
    ) {
        if( mIconSuiteInfo == null ) {
            return null;
        }

        mChosenIconSet = mIconSuiteInfo.chooseActiveIconSet(
            requestedCarrierName,
            connectionStateMobileNetType,
            roaming,
            hspaDistinguishable,
            hspapDistinguishable,
            showAtLeast3G
        );
        if( DEBUG ) {
            Log.d( TAG,
                "chooseActiveIconSet:"
                + " requestedCarrierName=\"" + requestedCarrierName + "\""
                + " connectionStateMobileNetType="  + connectionStateMobileNetType
                + " roaming="  + roaming
                + " hspaDistinguishable="  + hspaDistinguishable
                + " hspapDistinguishable="  + hspapDistinguishable
                + " showAtLeast3G="  + showAtLeast3G
            );
            mChosenIconSet.debugPrint( "chooseActiveIconSet: -> " );
        }
        return mChosenIconSet;
    }

    /** @hide */
    public int getMobileDataTypeIconId(
        int connectionStateNetworkState,
        int inetCondition,
        boolean disabled,
        boolean suspended
    ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeIconId(
            connectionStateNetworkState,
            inetCondition,
            disabled,
            suspended
        );
        if( DEBUG ) {
            Log.d( TAG, "getMobileDataTypeIconId:"
                + " connectionStateNetworkState=\"" + connectionStateNetworkState + "\""
                + " inetCondition="  + inetCondition
                + " disabled="  + disabled
                + " suspended="  + suspended
                + " -> returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for connecting (but not connected) mode.
    public int getMobileDataTypeConnectingIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeConnectingIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeConnectingIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for attached (attached to network, but not connected) mode.
    public int getMobileDataTypeAttachedIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeAttachedIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeAttachedIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for suspended (mobile disallowed during wifi) mode.
    public int getMobileDataTypeSuspendedIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeSuspendedIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeSuspendedIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for user-disabled mode.
    public int getMobileDataTypeDisabledIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeDisabledIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeDisabledIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns network description for off, unknown, connected, fully-connected states.
    public int getMobileDataTypeDescriptionId(
        int connectionStateNetworkState,
        int inetCondition,
        boolean disabled,
        boolean suspended
    ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeDescriptionId(
            connectionStateNetworkState,
            inetCondition,
            disabled,
            suspended
        );
        if( DEBUG ) {
            Log.d( TAG, "getMobileDataTypeDescriptionId:"
                + " connectionStateNetworkState=\"" + connectionStateNetworkState + "\""
                + " inetCondition="  + inetCondition
                + " disabled="  + disabled
                + " suspended="  + suspended
                + " -> returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override description for connecting (but not connected) mode.
    public int getMobileDataTypeConnectingDescriptionId( ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeConnectingDescriptionId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeConnectingDescriptionId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override description for attached (attached to network, but not connected) mode.
    public int getMobileDataTypeAttachedDescriptionId( ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeAttachedDescriptionId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeAttachedDescriptionId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override description for suspended (mobile disallowed during wifi) mode.
    public int getMobileDataTypeSuspendedDescriptionId( ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeSuspendedDescriptionId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeSuspendedDescriptionId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override description for user-disabled mode.
    public int getMobileDataTypeDisabledDescriptionId( ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataTypeDisabledDescriptionId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataTypeDisabledDescriptionId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }


    /** @hide */
    public int getMobileDataActivityIconId(
        int connectionStateNetworkState,
        int inetCondition,
        boolean disabled,
        boolean suspended,
        int connectionStateMobileDataActivity
    ) {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataActivityIconId(
            connectionStateNetworkState,
            inetCondition,
            disabled,
            suspended,
            connectionStateMobileDataActivity
        );
        if( DEBUG ) {
            Log.d( TAG, "getMobileDataActivityIconId: connectionStateNetworkState=\"" + connectionStateNetworkState + "\""
                + " inetCondition="  + inetCondition
                + " disabled="  + disabled
                + " suspended="  + suspended
                + " connectionStateMobileDataActivity="  + connectionStateMobileDataActivity
                + " -> returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }


    /** @hide */
    // Returns alternate override icon for connecting (but not connected) mode.
    public int getMobileDataActivityConnectingIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataActivityConnectingIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataActivityConnectingIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for attached (attached to network, but not connected) mode.
    public int getMobileDataActivityAttachedIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataActivityAttachedIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataActivityAttachIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for suspended (mobile disallowed during wifi) mode.
    public int getMobileDataActivitySuspendedIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataActivitySuspendedIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataActivitySuspendedIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }

    /** @hide */
    // Returns alternate override icon for user-disabled mode.
    public int getMobileDataActivityDisabledIconId() {
        if ( mChosenIconSet == null ) return 0;

        int returnValue = mChosenIconSet.getMobileDataActivityDisabledIconId();
        if( DEBUG ) {
            Log.d( TAG,
                "getMobileDataActivityDisabledIconId: returnValue=" + returnValue
                + "/" + getResourceName( returnValue )
            );
        }
        return returnValue;
    }


    protected class IconSuiteArray {
        private HashMap<String, IconSuite> mIconSuiteArray = null;
        private IconSuite mChosenIconSuite = null;

        /** @hide */
        // void constructor
        public IconSuiteArray() {
            mIconSuiteArray  = new HashMap<String, IconSuite>();
            mChosenIconSuite = null;

            // Populate IconSuite from XML data
        }

        /** @hide */
        public IconSuite createDefaultSuite( String infoCarrierName ) {
            if( DEBUG ) {
                Log.d( TAG,
                    "createDefaultSuite: infoCarrierName=\"" + infoCarrierName + "\""
                );
            }

            IconSuite iconSuite;
            if( ! infoCarrierName.equals( XML_ATTR_VALUE__ICON_SUITE__CARRIER_NAME__DEFAULT ) ) {
                if( DEBUG ) {
                    Log.d( TAG,
                        "createDefaultSuite: Non-default case"
                    );
                }

                iconSuite = new IconSuite(
                    infoCarrierName,
                    mIconSuiteArray.get( XML_ATTR_VALUE__ICON_SUITE__CARRIER_NAME__DEFAULT )
                    );
            } else {
                if( DEBUG ) {
                    Log.d( TAG,
                        "createDefaultSuite: Default case"
                    );
                }

                iconSuite = new IconSuite( infoCarrierName );
            }
            mIconSuiteArray.put( infoCarrierName, iconSuite );
            return iconSuite;
        }


         /** @hide */
        public IconSet chooseActiveIconSet(
            String requestedCarrierName,
            int connectionStateMobileNetType,
            boolean roaming,
            boolean hspaDistinguishable,
            boolean hspapDistinguishable,
            boolean showAtLeast3G
        ) {
            mChosenIconSuite =  mIconSuiteArray.get( requestedCarrierName );

            if( mChosenIconSuite == null ) {
                mChosenIconSuite =  mIconSuiteArray.get( XML_ATTR_VALUE__ICON_SUITE__CARRIER_NAME__DEFAULT );
            }

            if( mChosenIconSuite == null ) {
                return null;
            }

            return mChosenIconSuite.chooseActiveIconSet(
                connectionStateMobileNetType,
                roaming,
                hspaDistinguishable,
                hspapDistinguishable,
                showAtLeast3G
            );
        }
    } // end: class IconSuiteArray

    protected class IconSuite {
        String mInfoCarrierNameString;
        IconSet[] mIconSetArray;
        IconSet mChosenSet = null;

        /** @hide */
        // constructor
        public IconSuite(
            String carrierNameString
        ) {
            if( DEBUG ) {
                Log.d( TAG,
                    "IconSuite: carrierNameString=\"" + carrierNameString + "\""
                );
            }

            mInfoCarrierNameString = carrierNameString;
            // create and populate the mambers of the IconSetArray
            mIconSetArray = new IconSet[LOOKUP_DATA_TYPE_ARRAY_SIZE];
            for(
                int i=0;
                i< LOOKUP_DATA_TYPE_ARRAY_SIZE;
                i++
            ) {
                IconSet iconSet = new IconSet(
                    mInfoCarrierNameString,
                    i
                );
                mIconSetArray[i] = iconSet;
            }
        }

        /** @hide */
        // copy constructor
        public IconSuite(
            String carrierNameString,
            IconSuite iconSuiteRef
        ) {
            if( DEBUG ) {
                Log.d( TAG,
                    "IconSuite: carrierNameString=\"" + carrierNameString + "\""
                    + " iconSuiteRef=" + iconSuiteRef
                );
            }

            mInfoCarrierNameString = carrierNameString;
            // create and populate the mambers of the IconSetArray
            mIconSetArray = new IconSet[LOOKUP_DATA_TYPE_ARRAY_SIZE];
            for( int i=0; i< LOOKUP_DATA_TYPE_ARRAY_SIZE; i++ ) {
                if( iconSuiteRef != null ) {
                    mIconSetArray[i] = new IconSet(
                        mInfoCarrierNameString,
                        i,
                        iconSuiteRef.getIconSet(i)
                    ) ;
                } else {
                    mIconSetArray[i] = new IconSet(
                        mInfoCarrierNameString,
                        i
                    );
                }
            }
        }

        /** @hide */
        public void debugPrint() {
            debugPrint( "" );
        }

        /** @hide */
        public void debugPrint(
            String prefix
        ) {
            if( DEBUG ) {
                Log.d( TAG,
                    prefix
                    + "IconSuite: infoCarrierName=\"" + mInfoCarrierNameString + "\""
                );
            }

            for( int i=0; i<LOOKUP_DATA_TYPE_ARRAY_SIZE; i++ ) {
                mIconSetArray[i].debugPrint();
            }
        }

         /** @hide */
        public IconSet getIconSet(
            int index
        ) {
            return mIconSetArray[index];
        }

         /** @hide */
        public String getCarrierName() {
            return mInfoCarrierNameString;
        }

         /** @hide */
        public IconSet chooseActiveIconSet(
            int connectionStateMobileNetType,
            boolean roaming,
            boolean hspaDistinguishable,
            boolean hspapDistinguishable,
            boolean showAtLeast3G
        ) {

            // Within lookup table, using lookupMobileDataTypeKey find an IconSet
            int lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID;

            switch( connectionStateMobileNetType )
            {
                case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                    if( ! showAtLeast3G ) {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UNKNOWN;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UNKNOWN__ROAMING;
                        }
                    } else {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_GPRS:
                    if( ! showAtLeast3G ) {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_GPRS;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_GPRS__ROAMING;
                        }
                    } else {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_EDGE:
                    if( ! showAtLeast3G ) {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EDGE;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EDGE__ROAMING;
                        }
                    } else {
                        if( ! roaming ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_UMTS:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_UMTS__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_HSDPA:
                    if( ! roaming ) {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED;
                        }
                    } else {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__ROAMING;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_HSUPA:
                    if( ! roaming ) {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED;
                        }
                    } else {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__ROAMING;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_HSPA:
                    if( ! roaming ) {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED;
                        }
                    } else {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__ROAMING;
                        } else {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING;
                        }
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    if( ! roaming ) {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS;
                        } else {
                            if( ! hspapDistinguishable ) {
                                lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED;
                            } else {
                                lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED;
                            }
                        }
                    } else {
                        if( ! hspaDistinguishable ) {
                            lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__ROAMING;
                        } else {
                            if( ! hspapDistinguishable ) {
                                lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING;
                            } else {
                                lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING;
                            }
                        }
                    }
                    break;


                case TelephonyManager.NETWORK_TYPE_CDMA:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_CDMA;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_CDMA__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_1xRTT:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_1xRTT;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_1xRTT__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EVDO__ROAMING;
                    }
                    break;

                case TelephonyManager.NETWORK_TYPE_EHRPD:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EHRPD;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_EHRPD__ROAMING;
                    }
                    break;


                case TelephonyManager.NETWORK_TYPE_LTE:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_LTE;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_LTE__ROAMING;
                    }
                    break;


                case TelephonyManager.NETWORK_TYPE_IDEN:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_IDEN;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_IDEN__ROAMING;
                    }
                    break;


                default:
                    if( ! roaming ) {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID;
                    } else {
                        lookupMobileDataTypeKey = LOOKUP_DATA_TYPE_INVALID__ROAMING;
                    }
                    break;
            }

            return mIconSetArray[ lookupMobileDataTypeKey ];
        }

    } // end: class IconsSuite

 // Combined lookupMobileDataTypeKey+Activity icons are no longer supported (HC, ICS, JB)
    protected class IconSet {
        String mInfoCarrierNameString;
        int mLookupMobileDataTypeKey = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        int[] mMobileDataTypeIcons       = new int[LOOKUP_DATA_STATE_ARRAY_SIZE];
        int[] mMobileDataTypeDescription = new int[LOOKUP_DATA_STATE_ARRAY_SIZE];
        int[] mMobileDataActivityIcons   = new int[LOOKUP_DATA_ACTIVITY_ARRAY_SIZE];

        /** @hide */
        // "default" constructor
        public IconSet(
            String carrierNameString,
            int lookupMobileDataTypeKey
        ) {
            mInfoCarrierNameString = carrierNameString;
            mLookupMobileDataTypeKey = lookupMobileDataTypeKey;
            for(
                int i=0;
                i < LOOKUP_DATA_STATE_ARRAY_SIZE;
                i++
            ) {
                mMobileDataTypeIcons[i] = 0;
                mMobileDataTypeDescription[i] = 0;
            }
            for(
                int i=0;
                i < LOOKUP_DATA_ACTIVITY_ARRAY_SIZE;
                i++
            ) {
                mMobileDataActivityIcons[i] = 0;
            }
        }

        /** @hide */
        // copy constructor
        public IconSet(
            String carrierNameString,
            int lookupMobileDataTypeKey,
            IconSet iconSetRef
        ) {
            mInfoCarrierNameString = iconSetRef.mInfoCarrierNameString;
            mLookupMobileDataTypeKey = lookupMobileDataTypeKey;
            for(
                int i=0;
                i<LOOKUP_DATA_STATE_ARRAY_SIZE;
                i++
            ) {
                mMobileDataTypeIcons[i] = iconSetRef.mMobileDataTypeIcons[i];
                mMobileDataTypeDescription[i] = iconSetRef.mMobileDataTypeDescription[i];
            }
            for(
                int i=0;
                i<LOOKUP_DATA_ACTIVITY_ARRAY_SIZE;
                i++
            ) {
                mMobileDataActivityIcons[i] = iconSetRef.mMobileDataActivityIcons[i];
            }
        }


        /** @hide */
        public void debugPrint() {
            debugPrint( "" );
        }

        /** @hide */
        public void debugPrint(
            String prefix
        ) {
            if( DEBUG ) {
                Log.d( TAG,
                    prefix
                    + "IconSet: mInfoCarrierNameString=\"" + mInfoCarrierNameString + "\""
                    + " mLookupMobileDataTypeKey=" + mLookupMobileDataTypeKey
                    + "/" + debugPrintLookupMobileDataTypeKey( mLookupMobileDataTypeKey )
                );
                for(
                    int i=0;
                    i<LOOKUP_DATA_STATE_ARRAY_SIZE;
                    i++
                ) {
                    Log.d( TAG,
                        prefix
                        +"IconSet:  icon[" + i + "]=" + mMobileDataTypeIcons[i]
                        + "/" + getResourceName( mMobileDataTypeIcons[i] )
                        + " description=" + mMobileDataTypeDescription[i]
                        + "/" + getResourceName( mMobileDataTypeDescription[i] )
                    );
                }
                for(
                    int i=0;
                    i<LOOKUP_DATA_ACTIVITY_ARRAY_SIZE;
                    i++
                ) {
                    Log.d( TAG,
                        prefix
                        +"IconSet:  ActivityIcon[" + i + "]=" + mMobileDataActivityIcons[i]
                        + "/" + getResourceName( mMobileDataActivityIcons[i] )
                    );
                }
            }
        }

        private String debugPrintLookupMobileDataTypeKey(
            int key
        ) {
            if ( key == LOOKUP_DATA_TYPE_UNKNOWN ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN;
            } else if ( key == LOOKUP_DATA_TYPE_GPRS ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS;
            } else if ( key == LOOKUP_DATA_TYPE_EDGE ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE;
            } else if ( key == LOOKUP_DATA_TYPE_UMTS ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS;
            } else if ( key == LOOKUP_DATA_TYPE_H ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H;
            } else if ( key == LOOKUP_DATA_TYPE_H__H_DISTINGUISHED ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED;
            } else if ( key == LOOKUP_DATA_TYPE_CDMA ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA;
            } else if ( key == LOOKUP_DATA_TYPE_1xRTT ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT;
            } else if ( key == LOOKUP_DATA_TYPE_EVDO ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO;
            } else if ( key == LOOKUP_DATA_TYPE_EHRPD ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD;
            } else if ( key == LOOKUP_DATA_TYPE_LTE ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE;
            } else if ( key == LOOKUP_DATA_TYPE_IDEN ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN;
            } else if ( key == LOOKUP_DATA_TYPE_INVALID ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID;
            } else if ( key == LOOKUP_DATA_TYPE_UNKNOWN__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UNKNOWN__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_GPRS__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__GPRS__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_EDGE__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EDGE__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_UMTS__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__UMTS__ROAMING;
            } else if ( key ==  LOOKUP_DATA_TYPE_H__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_H__H_DISTINGUISHED__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__H__H_DISTINGUISHED__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS__H_DISTINGUISHED__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__H_DISTINGUISHED__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_HPLUS__HPLUS_DISTINGUISHED__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__HPLUS__HPLUS_DISTINGUISHED__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_CDMA__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__CDMA__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_1xRTT__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__1xRTT__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_EVDO__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EVDO__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_EHRPD__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__EHRPD__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_LTE__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__LTE__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_IDEN__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__IDEN__ROAMING;
            } else if ( key == LOOKUP_DATA_TYPE_INVALID__ROAMING ) {
                return XML_ATTR_VALUE__ICON_SET__DATA_TYPE_KEY__INVALID__ROAMING;
            } else {
                return "(unset)";
            }
        }

        /** @hide */
        public void setMobileDataTypeIconId(
            int index,
            int value
        ) {
            if(
                ( index >= 0 )
                &&
                ( index < LOOKUP_DATA_STATE_ARRAY_SIZE )
            ) {
                mMobileDataTypeIcons[ index ] = value;
            }
        }

        /** @hide */
        public void setCarrierNameString(
            String value
        ) {
            mInfoCarrierNameString = value;
        }

        /** @hide */
        public void setMobileDataTypeDescriptionId(
            int index,
            int value
        ) {
            if(
                ( index >= 0 )
                &&
                ( index < LOOKUP_DATA_STATE_ARRAY_SIZE )
            ) {
                mMobileDataTypeDescription[ index ] = value;
            }
        }

        /** @hide */
        public void setMobileDataActivityIconId(
            int index,
            int value
        ) {
            if(
                ( index >= 0 )
                &&
                ( index < LOOKUP_DATA_ACTIVITY_ARRAY_SIZE )
            ) {
                mMobileDataActivityIcons[ index ] = value;
            }
        }

        /** @hide */
        public int getMobileDataTypeIconId(
            int connectionStateNetworkState,
            int inetCondition,
            boolean disabled,
            boolean suspended
        ) {
            int networkStateKey = LOOKUP_DATA_ACTIVITY_UNKNOWN;

            switch ( connectionStateNetworkState ) {
                default:
                case TelephonyManager.DATA_UNKNOWN:
                    networkStateKey = LOOKUP_DATA_STATE_UNKNOWN;
                    break;

                case TelephonyManager.DATA_DISCONNECTED:
                    networkStateKey = LOOKUP_DATA_STATE_DISCONNECTED;
                    break;

                case TelephonyManager.DATA_CONNECTING:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    }
                    networkStateKey = LOOKUP_DATA_STATE_CONNECTING;
                    break;

                case TelephonyManager.DATA_SUSPENDED:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    }
                    networkStateKey = LOOKUP_DATA_STATE_SUSPENDED;
                    break;

                case TelephonyManager.DATA_CONNECTED:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    } else if ( suspended ) {
                        networkStateKey = LOOKUP_DATA_STATE_SUSPENDED;
                        break;
                    }

                    if( inetCondition == 0 ) {
                        networkStateKey = LOOKUP_DATA_STATE_CONNECTED;
                        break;
                    } else {
                        networkStateKey = LOOKUP_DATA_STATE_FULLY_CONNECTED;
                        break;
                    }
            }

            if(
                ( networkStateKey >= 0 )
                &&
                ( networkStateKey < LOOKUP_DATA_STATE_ARRAY_SIZE )
            ) {
                return mMobileDataTypeIcons[networkStateKey];
            }
            else return 0;
        }

        /** @hide */
        public int getMobileDataTypeDescriptionId(
            int connectionStateNetworkState,
            int inetCondition,
            boolean disabled,
            boolean suspended
        ) {
            int networkStateKey = LOOKUP_DATA_ACTIVITY_UNKNOWN;

            switch ( connectionStateNetworkState ) {
                default:
                case TelephonyManager.DATA_UNKNOWN:
                    networkStateKey = LOOKUP_DATA_STATE_UNKNOWN;
                    break;

                case TelephonyManager.DATA_DISCONNECTED:
                    networkStateKey = LOOKUP_DATA_STATE_DISCONNECTED;
                    break;

                case TelephonyManager.DATA_CONNECTING:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    }
                    networkStateKey = LOOKUP_DATA_STATE_CONNECTING;
                    break;

                case TelephonyManager.DATA_SUSPENDED:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    }
                    networkStateKey = LOOKUP_DATA_STATE_SUSPENDED;
                    break;

                case TelephonyManager.DATA_CONNECTED:
                    if ( disabled ) {
                        networkStateKey = LOOKUP_DATA_STATE_DISABLED;
                        break;
                    } else if ( suspended ) {
                        networkStateKey = LOOKUP_DATA_STATE_SUSPENDED;
                        break;
                    }

                    if( inetCondition == 0 ) {
                        networkStateKey = LOOKUP_DATA_STATE_CONNECTED;
                        break;
                    } else {
                        networkStateKey = LOOKUP_DATA_STATE_FULLY_CONNECTED;
                        break;
                    }
            }

            if(
                ( networkStateKey >= 0 )
                &&
                ( networkStateKey < LOOKUP_DATA_STATE_ARRAY_SIZE )
            ) {
                return mMobileDataTypeDescription[networkStateKey];
            } else {
                return 0;
            }
        }

        /** @hide */
        public int getMobileDataActivityIconId(
            int connectionStateNetworkState,
            int inetCondition,
            boolean disabled,
            boolean suspended,
            int connectionStateMobileDataActivity
        ) {
            int mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_UNKNOWN;

            switch ( connectionStateNetworkState ) {
                default:
                case TelephonyManager.DATA_UNKNOWN:
                    mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_UNKNOWN;
                    break;

                case TelephonyManager.DATA_DISCONNECTED:
                    mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_DISCONNECTED;
                    break;

                case TelephonyManager.DATA_CONNECTING:
                    if ( disabled ) {
                        mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_DISABLED;
                        break;
                    }
                    mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTING;
                    break;

                case TelephonyManager.DATA_SUSPENDED:
                    if ( disabled ) {
                        mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_DISABLED;
                        break;
                    }
                    mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_SUSPENDED;
                    break;

                case TelephonyManager.DATA_CONNECTED:
                    if ( disabled ) {
                        mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_DISABLED;
                        break;
                    } else if ( suspended ) {
                        mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_SUSPENDED;
                        break;
                    }

                    if( inetCondition == 0 ) {
                        switch ( connectionStateMobileDataActivity ) {
                            default:
                            case TelephonyManager.DATA_ACTIVITY_NONE:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTED_IDLE;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_DORMANT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTED_DORMANT;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_IN:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTED_IN;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_OUT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTED_OUT;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_INOUT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_CONNECTED_INOUT;
                                break;
                        }
                    } else {
                        switch ( connectionStateMobileDataActivity ) {
                            default:
                            case TelephonyManager.DATA_ACTIVITY_NONE:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IDLE;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_DORMANT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_DORMANT;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_IN:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_IN;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_OUT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_OUT;
                                break;
                            case TelephonyManager.DATA_ACTIVITY_INOUT:
                                mobileDataActivityKey = LOOKUP_DATA_ACTIVITY_FULLY_CONNECTED_INOUT;
                                break;
                        }
                    }
            }

            if(
                ( mobileDataActivityKey >= 0 )
                &&
                ( mobileDataActivityKey < LOOKUP_DATA_ACTIVITY_ARRAY_SIZE )
            ) {
                return mMobileDataActivityIcons[mobileDataActivityKey];
            } else {
                return 0;
            }
        }


        /** @hide */
        // Returns alternate override icon for connecting (but not connected) mode.
        public int getMobileDataTypeConnectingIconId() {
            return mMobileDataTypeIcons[ LOOKUP_DATA_STATE_CONNECTING ];
        }

        /** @hide */
        // Returns alternate override icon for attached (attached to network, but not connected) mode.
        public int getMobileDataTypeAttachedIconId() {
            return mMobileDataTypeIcons[ LOOKUP_DATA_STATE_ATTACHED ];
        }

        /** @hide */
        // Returns alternate override icon for suspended (mobile disallowed during wifi) mode.
        public int getMobileDataTypeSuspendedIconId() {
            return mMobileDataTypeIcons[ LOOKUP_DATA_STATE_SUSPENDED ];
        }

        /** @hide */
        // Returns alternate override icon for user-disabled mode.
        public int getMobileDataTypeDisabledIconId() {
            return mMobileDataTypeIcons[ LOOKUP_DATA_STATE_DISABLED ];
        }

        /** @hide */
        // Returns alternate override description for connecting (but not connected) mode.
        public int getMobileDataTypeConnectingDescriptionId( ) {
            return mMobileDataTypeDescription[ LOOKUP_DATA_STATE_CONNECTING ];
        }

        /** @hide */
        // Returns alternate override description for attached (attached to network, but not connected) mode.
        public int getMobileDataTypeAttachedDescriptionId( ) {
            return mMobileDataTypeDescription[ LOOKUP_DATA_STATE_ATTACHED ];
        }

        /** @hide */
        // Returns alternate override description for suspended (mobile disallowed during wifi) mode.
        public int getMobileDataTypeSuspendedDescriptionId( ) {
            return mMobileDataTypeDescription[ LOOKUP_DATA_STATE_SUSPENDED ];
        }

        /** @hide */
        // Returns alternate override description for user-disabled mode.
        public int getMobileDataTypeDisabledDescriptionId( ) {
            return mMobileDataTypeDescription[ LOOKUP_DATA_STATE_DISABLED ];
        }

        /** @hide */
        // Returns alternate override activity icon for connecting (but not connected) mode.
        public int getMobileDataActivityConnectingIconId( ) {
            return mMobileDataActivityIcons[ LOOKUP_DATA_ACTIVITY_CONNECTING ];
        }

        /** @hide */
        // Returns alternate override activity icon for attached (attached to network but not connected) mode.
        public int getMobileDataActivityAttachedIconId( ) {
            return mMobileDataActivityIcons[ LOOKUP_DATA_ACTIVITY_ATTACHED ];
        }

        /** @hide */
        // Returns alternate override activity icon for suspended (mobile disallowed during wifi) mode.
        public int getMobileDataActivitySuspendedIconId( ) {
            return mMobileDataActivityIcons[ LOOKUP_DATA_ACTIVITY_SUSPENDED ];
        }

        /** @hide */
        // Returns alternate override activity icon for user-disabled mode.
        public int getMobileDataActivityDisabledIconId( ) {
            return mMobileDataActivityIcons[ LOOKUP_DATA_ACTIVITY_DISABLED ];
        }
    } // end: class IconSet

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


    private TelephonyIcons() {
        // Initialize the lookup tables from XML data.
    }
}
