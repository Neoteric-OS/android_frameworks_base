package android.telephony;

import android.util.Log;

/*
 * Copyright (C) 2010 The Android Open Source Project
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

public enum RadioTechnology {
    /* implicitly matches those defined in ril.h */
    RADIO_TECH_UNKNOWN, //0
    RADIO_TECH_GPRS,    //1
    RADIO_TECH_EDGE,    //2
    RADIO_TECH_UMTS,    //3
    RADIO_TECH_IS95A,   //4
    RADIO_TECH_IS95B,   //5
    RADIO_TECH_1xRTT,   //6
    RADIO_TECH_EVDO_0,  //7
    RADIO_TECH_EVDO_A,  //8
    RADIO_TECH_HSDPA,   //9
    RADIO_TECH_HSUPA,   //10
    RADIO_TECH_HSPA,    //11
    RADIO_TECH_EVDO_B;  //12

    public boolean isUnknown() {
        return this == RADIO_TECH_UNKNOWN;
    }

    public boolean isGsm() {
        return this == RADIO_TECH_GPRS || this == RADIO_TECH_EDGE || this == RADIO_TECH_UMTS
                || this == RADIO_TECH_HSDPA || this == RADIO_TECH_HSUPA
                || this == RADIO_TECH_HSPA;
    }

    public boolean isCdma() {
        return this == RADIO_TECH_IS95A || this == RADIO_TECH_IS95B || this == RADIO_TECH_1xRTT
                || this == RADIO_TECH_EVDO_0 || this == RADIO_TECH_EVDO_A
                || this == RADIO_TECH_EVDO_B;
    }

    public boolean isEvdo() {
        return this == RADIO_TECH_EVDO_0 || this == RADIO_TECH_EVDO_A
                || this == RADIO_TECH_EVDO_B;
    }

    public static RadioTechnology getRadioTechFromInt(int techInt) {
        RadioTechnology rt = RADIO_TECH_UNKNOWN;
        try {
            rt = values()[techInt];
        } catch (IndexOutOfBoundsException e) {
            Log.e("RIL", "Invalid radio technology : " + techInt);
        }
        return rt;
    }
}