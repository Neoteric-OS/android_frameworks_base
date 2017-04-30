/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.connectivity.tethering;

import android.hardware.tetheroffload.control.V1_0.IOffloadControl;
import android.net.LinkProperties;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

/**
 * A wrapper around hardware offload interface.
 *
 * @hide
 */
public class OffloadController {
    private static final String TAG = OffloadController.class.getSimpleName();

    private static native boolean configOffload();

    private final Handler mHandler;
    private boolean mConfigComplete;
    private IOffloadControl mOffloadControl;
    private LinkProperties mUpstreamLinkProperties;

    public OffloadController(Handler h) {
        mHandler = h;
    }

    public void start() {
        if (started()) return;

        if (!mConfigComplete) {
            mConfigComplete = configOffload();
            if (!mConfigComplete) {
                Log.d(TAG, "tethering offload config not supported");
                return;
            }
        }

        if (mOffloadControl == null) {
            try {
                mOffloadControl = IOffloadControl.getService();
            } catch (RemoteException e) {
                Log.d(TAG, "tethering offload control not supported: " + e);
                return;
            }
        }

        // TODO: Create and register ITetheringOffloadCallback.
    }

    public void stop() {
        if (!started()) return;

        // TODO: stopOffload().
        mUpstreamLinkProperties = null;
    }

    public void setUpstreamLinkProperties(LinkProperties lp) {
        if (!started()) return;

        // TODO: setUpstreamParameters().
        mUpstreamLinkProperties = lp;
    }

    // TODO: public void addDownStream(...)

    private boolean started() {
        return mConfigComplete && (mOffloadControl != null);
    }
}
