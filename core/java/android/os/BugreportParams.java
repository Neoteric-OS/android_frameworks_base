/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.os;

import android.annotation.IntDef;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Parameters that specify what kind of bugreport should be taken.
 * @hide
 */
@SystemApi
public class BugreportParams {
    int mMode;

    BugreportParams(int mode) {
        mMode = mode;
    }

    // The following modes are used to specify options to Dumpstate binder service.
    // In the future the options will be directly added to {@link BugreportParams}.
    // In the meantime, these modes help do the right thing under the hood.

    /**
     * Defines acceptable types of bugreports.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = { "BUGREPORT_MODE_" }, value = {
            BUGREPORT_MODE_FULL,
            BUGREPORT_MODE_INTERACTIVE,
            BUGREPORT_MODE_REMOTE,
            BUGREPORT_MODE_WEAR,
            BUGREPORT_MODE_TELEPHONY,
            BUGREPORT_MODE_WIFI
    })
    @interface BugreportMode {}
    private static final int BUGREPORT_MODE_FULL = IDumpstate.BUGREPORT_MODE_FULL;
    private static final int BUGREPORT_MODE_INTERACTIVE = IDumpstate.BUGREPORT_MODE_INTERACTIVE;
    private static final int BUGREPORT_MODE_REMOTE = IDumpstate.BUGREPORT_MODE_REMOTE;
    private static final int BUGREPORT_MODE_WEAR = IDumpstate.BUGREPORT_MODE_WEAR;
    private static final int BUGREPORT_MODE_TELEPHONY = IDumpstate.BUGREPORT_MODE_TELEPHONY;
    private static final int BUGREPORT_MODE_WIFI = IDumpstate.BUGREPORT_MODE_WIFI;

    // TODO(b/111441001): Give more readable names?

    /**
     * Takes a bugreport without user interference (and hence causing less
     * interference to the system), but includes all sections.
     */
    public static final BugreportParams BUGREPORT_PARAMS_FULL =
            new BugreportParams(BUGREPORT_MODE_FULL);

    /**
     * Allows user to monitor progress and enter additional data; might not include all
     * sections.
     */
    public static final BugreportParams BUGREPORT_PARAMS_INTERACTIVE =
            new BugreportParams(BUGREPORT_MODE_INTERACTIVE);

    /**
     * Takes a bugreport requested remotely by administrator of the Device Owner app,
     * not the device's user.
     */
    public static final BugreportParams BUGREPORT_PARAMS_REMOTE =
            new BugreportParams(BUGREPORT_MODE_REMOTE);

    /**
     * Takes a bugreport on a wearable device.
     */
    public static final BugreportParams BUGREPORT_PARAMS_WEAR =
            new BugreportParams(BUGREPORT_MODE_WEAR);

    /**
     * Takes a lightweight version of bugreport that only includes a few, urgent sections
     * used to report telephony bugs.
     */
    public static final BugreportParams BUGREPORT_PARAMS_TELEPHONY =
            new BugreportParams(BUGREPORT_MODE_TELEPHONY);

    /**
     * Takes a lightweight bugreport that only includes a few sections related to Wifi.
     */
    public static final BugreportParams BUGREPORT_PARAMS_WIFI =
            new BugreportParams(BUGREPORT_MODE_WIFI);
}
