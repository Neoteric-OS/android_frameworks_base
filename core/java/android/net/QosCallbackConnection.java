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

/**
 * Sends messages from {@link com.android.server.ConnectivityService} to the registered
 * {@link QosCallback}.
 *
 * {@hide}
 */
public class QosCallbackConnection extends android.net.IQosCallback.Stub {

    @NonNull private final ConnectivityManager mConnectivityManager;
    @NonNull private QosCallback mCallback;

    @NonNull public QosCallback getCallback() {
        return mCallback;
    }

    /**
     * ..ctor for the connection
     *
     * @param connectivityManager the mgr that created this connection
     * @param callback the callback to send messages back to
     */
    public QosCallbackConnection(@NonNull final ConnectivityManager connectivityManager,
            @NonNull final QosCallback callback) {
        mConnectivityManager = Objects.requireNonNull(connectivityManager,
                "connectivityManager must be non-null");
        mCallback = Objects.requireNonNull(callback, "callback must be non-null");
    }

    /**
     * Called when either the {@link EpsBearerQosSessionAttributes} has changed or on the first time
     * the attributes have become available.
     *
     * Note: Aidl callback specific to {@link EpsBearerQosSessionAttributes}.  Would have preferred
     * to only have the callback on {@link QosSessionAttributes} but ws difficult to do given
     * that you {@link android.os.Parcelable} classes must be final.
     *
     * @param session the session that is now available
     * @param attributes the corresponding attributes of session
     */
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

    /**
     * Called when the session is lost.
     *
     * @param session the session that was lost
     */
    @Override
    public void onQosSessionLost(@NonNull final QosSession session) {
        synchronized (this) {
            QosCallback callback = mCallback;
            if (callback != null) {
                callback.onQosSessionLost(session);
            }
        }
    }

    /**
     * Called when there is an error on the registered callback.
     *
     * @param errorType the type of error
     * @param errorMsg the error message
     */
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
}
