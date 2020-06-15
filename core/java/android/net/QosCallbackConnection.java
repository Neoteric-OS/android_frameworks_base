/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net;

import android.annotation.NonNull;
import android.telephony.EpsBearerQosSessionAttributes;

import java.util.Objects;

/** {@hide} */
public class QosCallbackConnection extends android.net.IQosCallback.Stub {

    @NonNull private final ConnectivityManager mConnectivityManager;
    @NonNull private QosCallback mCallback;

    @NonNull public QosCallback getCallback() {
        return mCallback;
    }

    public QosCallbackConnection(@NonNull final ConnectivityManager connectivityManager,
            @NonNull final QosCallback callback) {
        mConnectivityManager = Objects.requireNonNull(connectivityManager,
                "connectivityManager must be non-null");
        mCallback = Objects.requireNonNull(callback, "callback must be non-null");
    }

    /**
     * Stops delivering events to the callback.
     * ConnectivityManager calls this when unregister is called.
     */
    void stop() {
        if (mCallback != null) {
            synchronized (this) {
                clearCallback();
            }
        }
    }

    private void clearCallback() {
        mCallback = null;
    }

    @Override
    public void onError(int errorType, String errorMsg) {
        synchronized (this) {
            QosCallback callback = mCallback;
            if (callback != null) {
                clearCallback();
                QosCallbackException ex = QosCallbackException.createException(errorType, errorMsg);
                mConnectivityManager.unregisterQosCallback(callback);
                if (ex != null) {
                    callback.onError(ex);
                }
            }
        }
    }

    @Override
    public void onQosEpsBearerSessionAvailable(@NonNull final QosSession session,
            @NonNull final EpsBearerQosSessionAttributes attributes) {
        synchronized (this) {
            QosCallback callback = mCallback;
            if (callback != null) {
                callback.onQosSessionAvailable(session, attributes);
            }
        }
    }

    @Override
    public void onQosSessionLost(@NonNull final QosSession session) {
        synchronized (this) {
            QosCallback callback = mCallback;
            if (callback != null) {
                callback.onQosSessionLost(session);
            }
        }
    }
}
