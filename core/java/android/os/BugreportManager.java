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

import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;

/**
 * Service that provides a privileged API to capture and consume bugreports.
 * @hide
 */
@SystemApi
@SystemService(Context.BUGREPORT_SERVICE)
public class BugreportManager {


    private static final String TAG = "BugreportManager";
    private final Context mContext;

    public BugreportManager(Context context) {
        mContext = context;
    }

    /**
     * An interface describing the listener for bugreport progress and status.
     */
    public interface BugreportListener {
        // TODO: Add progress and status update methods.
    }

    /**
     * Starts a bugreport asynchronously.
     */
    @RequiresPermission(android.Manifest.permission.DUMP)
    public void startBugreport(BugreportParams params, BugreportListener listener) {
        startBugreport(params.mMode, listener);
    }

    private void startBugreport(@BugreportParams.BugreportMode int type,
            BugreportListener listener) {
        // TODO: implement
        // validate(type);
        // SystemProperties.set("ctl.start", "bugreport");
        // IDumpstate ds = getService();
        // if (ds != null) {
        //     try {
        //         ds.startBugreport(type, listener);
        //     }  catch (RemoteException e) {
        //         Slog.w(TAG, "startBugreport failed: " + e.getMessage());
        //     }
        // }
    }
}
