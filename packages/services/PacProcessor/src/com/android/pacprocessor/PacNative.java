/**
 * Copyright (c) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.pacprocessor;

import android.util.Log;

/**
 * @hide
 */
public class PacNative {
    private static final String TAG = "PacProxy";

    // Only make native calls from inside synchronized blocks.
    private native boolean setProxyScriptNativeLocked(String script, int netId);
    private native void setDefaultNetIdNativeLocked(int netId);
    private native void setNetworkProxyDisableNativeLocked(boolean networkProxyDisable);
    private native void shutdownNativeLocked();

    private native String makeProxyRequestNativeLocked(String url, String host, int netId);

    static {
        System.loadLibrary("jni_pacprocessor");
    }

    PacNative() {

    }

    public synchronized boolean setCurrentProxyScript(String script, int netId) {
        if (setProxyScriptNativeLocked(script, netId)) {
            Log.e(TAG, "Unable to parse proxy script.");
            return true;
        }
        return false;
    }

    public synchronized void setNetworkProxyDisable(boolean networkProxyDisable) {
        setNetworkProxyDisableNativeLocked(networkProxyDisable);
    }

    public synchronized void setDefaultNetId(int netId) {
        setDefaultNetIdNativeLocked(netId);
    }

    public synchronized void shutdown() {
        shutdownNativeLocked();
    }

    public synchronized String makeProxyRequest(String url, String host, int netId) {
        String ret = makeProxyRequestNativeLocked(url, host, netId);
        if ((ret == null) || (ret.length() == 0)) {
            Log.e(TAG, "v8 Proxy request failed.");
            ret = null;
        }
        return ret;
    }
}
