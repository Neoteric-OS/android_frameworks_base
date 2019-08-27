/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.connectivity;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ITetheringConnector;
import android.os.IBinder;

import androidx.annotation.NonNull;

/**
 * Android service used to start the tethering when bound to via an intent.
 *
 * <p>The service returns a binder for the system server to communicate with the tethering.
 */
public class TetheringService extends Service {
    private static final String TAG = TetheringService.class.getSimpleName();
    private static TetheringConnector sConnector;

    /**
     * Create a binder connector for the system server to communicate with the tethering.
     *
     * <p>On platforms where the tethering runs in the system server process, this method may be
     * called directly instead of obtaining the connector by binding to the service.
     */
    private static synchronized IBinder makeConnector(Context context) {
        if (sConnector == null) {
            sConnector = new TetheringConnector(context);
        }
        return sConnector;
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return makeConnector(this);
    }

    private static class TetheringConnector extends ITetheringConnector.Stub {
        private final Context mContext;

        TetheringConnector(Context context) {
            mContext = context;
        }
    }
}
