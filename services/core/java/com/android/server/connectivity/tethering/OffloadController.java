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

import android.net.LinkProperties;
import android.os.Handler;
import android.util.Log;

/**
 * A wrapper around hardware offload interface.
 *
 * @hide
 */
public class OffloadController {
    private static final String TAG = OffloadController.class.getSimpleName();

    private static native boolean configOffload();

    private final OperatingState mState;
    private final Handler mHandler;
    private LinkProperties mUpstreamLinkProperties;

    public OffloadController(Handler h) {
        mState = new OperatingState();
        mHandler = h;
    }

    public void start() {
        if (!mState.configComplete) {
            mState.configComplete = configOffload();
            if (!mState.configComplete) {
                Log.d(TAG, "tethering offload not supported");
                return;
            }
        }

        // TODO: initOffload() and configure callbacks to be handled on our
        // preferred Handler.
    }

    public void stop() {
        if (!mState.started()) return;

        // TODO: stopOffload().
        mUpstreamLinkProperties = null;
    }

    public void setUpstreamLinkProperties(LinkProperties lp) {
        if (!mState.started()) return;

        // TODO: setUpstreamParameters().
        mUpstreamLinkProperties = lp;
    }

    // TODO: public void addDownStream(...)

    // Track several pieces of related state in one place.
    private static class OperatingState {
        boolean configComplete;
        // TODO: ITetheringOffloadCallback callback;
        // TODO: boolean offloadEnabled  /* for current RAT */

        boolean started() {
            return configComplete /* && callback != null */;
        }
    }
}
