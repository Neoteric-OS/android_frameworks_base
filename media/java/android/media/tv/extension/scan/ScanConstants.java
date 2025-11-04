/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.media.tv.extension.scan;

import android.annotation.IntDef;
import android.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constant definition for scan extension package.
 *
 * @hide
 */
final class ScanConstants {
    /*************************************** BroadcastType ****************************************/
    @IntDef({
            TYPE_DVB_T,
            TYPE_DVB_C,
            TYPE_DVB_S,
            TYPE_DTMB,
            TYPE_ATSC,
            TYPE_ATSC3,
            TYPE_PAL_SECAM,
            TYPE_NTSC,
            TYPE_ISDB_T,
            TYPE_ISDB_TB,
            TYPE_ISDB_T3,
            TYPE_CQAM
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface BroadcastType{}
    public static final int TYPE_DVB_T = 0;
    public static final int TYPE_DVB_C = 1;
    public static final int TYPE_DVB_S = 2;
    public static final int TYPE_DTMB = 3;
    public static final int TYPE_ATSC = 4;
    public static final int TYPE_ATSC3 = 5;
    public static final int TYPE_PAL_SECAM = 6;
    public static final int TYPE_NTSC = 7;
    public static final int TYPE_ISDB_T = 8;
    public static final int TYPE_ISDB_TB = 9;
    public static final int TYPE_ISDB_T3 = 10;
    public static final int TYPE_CQAM = 11;

/************************************************ScanType******************************************/
    @StringDef({
            SCAN_TYPE_UNKNOWN,
            SCAN_TYPE_FULL,
            SCAN_TYPE_QUICK,
            SCAN_TYPE_NETWORK,
            SCAN_TYPE_MANUAL,
            SCAN_TYPE_UPDATE,
            SCAN_TYPE_RANGE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScanType{}
    public static final String SCAN_TYPE_UNKNOWN = "UNKNOWN";
    public static final String SCAN_TYPE_FULL = "FULL";
    public static final String SCAN_TYPE_QUICK = "QUICK";
    public static final String SCAN_TYPE_NETWORK = "NETWORK";
    public static final String SCAN_TYPE_MANUAL = "MANUAL";
    public static final String SCAN_TYPE_UPDATE = "UPDATE";
    public static final String SCAN_TYPE_RANGE = "RANGE";

/****************************************ScanResult************************************************/
    @IntDef({
            SCAN_RESULT_SUCCEEDED,
            SCAN_RESULT_CANCELED,
            SCAN_RESULT_FAILED,
            SCAN_RESULT_RESOURCE_BUSY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScanResult{}
    public static final int SCAN_RESULT_SUCCEEDED = 0;
    public static final int SCAN_RESULT_CANCELED = 1;
    public static final int SCAN_RESULT_FAILED = 2;
    public static final int SCAN_RESULT_RESOURCE_BUSY = 3;

/******************************************StoreResult*********************************************/
    @IntDef({
            STORE_RESULT_SUCCEEDED,
            STORE_RESULT_FAILED,
            STORE_RESULT_RESOURCE_BUSY,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface StoreResult{}
    public static final int STORE_RESULT_SUCCEEDED = 0;
    public static final int STORE_RESULT_FAILED = 1;
    public static final int STORE_RESULT_RESOURCE_BUSY = 2;

/**************************************OpResult****************************************************/
    @IntDef({
            RESULT_SUCCEEDED,
            RESULT_FAILED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OpResult{}
    public static final int RESULT_SUCCEEDED = 0;
    public static final int RESULT_FAILED = 2;

/***************************************OperatorType***********************************************/
    @StringDef({
            OPERATOR_DEFAULT_TERRESTRIAL,
            OPERATOR_DEFAULT_CABLE,
            OPERATOR_GENERAL_SATELLITE,
            OPERATOR_UPC,
            OPERATOR_COMHEM,
            OPERATOR_CANAL_DIGITA,
            OPERATOR_TELE2,
            OPERATOR_STOFA,
            OPERATOR_YOUSEE,
            OPERATOR_ZIGGO,
            OPERATOR_UNITYMEDIA,
            OPERATOR_CAM_READY,
            OPERATOR_VOLIA,
            OPERATOR_TELEMACH,
            OPERATOR_ROSTELECOM_MOSCOW,
            OPERATOR_AKADO,
            OPERATOR_ROSTELECOM_SPB,
            OPERATOR_DIVAN_TV,
            OPERATOR_NET1,
            OPERATOR_KDG,
            OPERATOR_CANAL_PLUS,
            OPERATOR_A1,
            OPERATOR_TELENET,
            OPERATOR_GLENTEN,
            OPERATOR_TELE_COLUMBUS,
            OPERATOR_DIGI,
            OPERATOR_VOO,
            OPERATOR_TIVU_SAT,
            OPERATOR_ASTRA_HD_PLUS,
            OPERATOR_SKY_DEUTSCHLAND,
            OPERATOR_DIGITURK,
            OPERATOR_CYFROWY_POLSAT,
            OPERATOR_D_SMART,
            OPERATOR_SIMPLI_TV,
            OPERATOR_TRICOLOR_TV,
            OPERATOR_MAGENTA,
            OPERATOR_KABLO_TV,
            OPERATOR_CABLE_READY,
            OPERATOR_FREESAT,
            OPERATOR_VODAFONE,
            OPERATOR_OTHERS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OperatorType {}
    public static final String OPERATOR_DEFAULT_TERRESTRIAL = "DEFAULT_TERRESTRIAL";
    public static final String OPERATOR_DEFAULT_CABLE = "DEFAULT_CABLE";
    public static final String OPERATOR_GENERAL_SATELLITE = "GENERAL_SATELLITE";
    public static final String OPERATOR_UPC = "UPC";
    public static final String OPERATOR_COMHEM = "COMHEM";
    public static final String OPERATOR_CANAL_DIGITA = "CANAL_DIGITA";
    public static final String OPERATOR_TELE2 = "TELE2";
    public static final String OPERATOR_STOFA = "STOFA";
    public static final String OPERATOR_YOUSEE = "YOUSEE";
    public static final String OPERATOR_ZIGGO = "ZIGGO";
    public static final String OPERATOR_UNITYMEDIA = "UNITYMEDIA";
    public static final String OPERATOR_CAM_READY = "CAM_READY";
    public static final String OPERATOR_VOLIA = "VOLIA";
    public static final String OPERATOR_TELEMACH = "TELEMACH";
    public static final String OPERATOR_ROSTELECOM_MOSCOW = "ROSTELECOM_MOSCOW";
    public static final String OPERATOR_AKADO = "AKADO";
    public static final String OPERATOR_ROSTELECOM_SPB = "ROSTELECOM_SPB";
    public static final String OPERATOR_DIVAN_TV = "DIVAN_TV";
    public static final String OPERATOR_NET1 = "NET1";
    public static final String OPERATOR_KDG = "KDG";
    public static final String OPERATOR_CANAL_PLUS = "CANAL_PLUS";
    public static final String OPERATOR_A1 = "A1";
    public static final String OPERATOR_TELENET = "TELENET";
    public static final String OPERATOR_GLENTEN = "GLENTEN";
    public static final String OPERATOR_TELE_COLUMBUS = "TELE_COLUMBUS";
    public static final String OPERATOR_DIGI = "DIGI";
    public static final String OPERATOR_VOO = "VOO";
    public static final String OPERATOR_TIVU_SAT = "TIVU_SAT";
    public static final String OPERATOR_ASTRA_HD_PLUS = "ASTRA_HD_PLUS";
    public static final String OPERATOR_SKY_DEUTSCHLAND = "SKY_DEUTSCHLAND";
    public static final String OPERATOR_DIGITURK = "DIGITURK";
    public static final String OPERATOR_CYFROWY_POLSAT = "CYFROWY_POLSAT";
    public static final String OPERATOR_D_SMART = "D_SMART";
    public static final String OPERATOR_SIMPLI_TV = "SIMPLI_TV";
    public static final String OPERATOR_TRICOLOR_TV = "TRICOLOR_TV";
    public static final String OPERATOR_MAGENTA = "MAGENTA";
    public static final String OPERATOR_KABLO_TV = "KABLO_TV";
    public static final String OPERATOR_CABLE_READY = "CABLE_READY";
    public static final String OPERATOR_FREESAT = "FREESAT";
    public static final String OPERATOR_VODAFONE = "VODAFONE";
    public static final String OPERATOR_OTHERS = "OTHERS";
}
