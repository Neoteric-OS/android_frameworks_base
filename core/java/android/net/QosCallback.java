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
import android.annotation.SystemApi;

/**
 * Receives Qos information given a {@link Network}.  Registered with
 * {@link ConnectivityManager#registerQosCallback(QosFilter, QosCallback)}
 *
 * {@hide}
 */
@SystemApi
public abstract class QosCallback {
    /**
     * Invoked when there was an error on the registered callback.  The callback does not have to
     * be unregistered.  This is NOT called if the callback was already unregistered.
     *
     * The underlying exception can either be a runtime exception or a custom exception made for
     * {@link QosCallback}. Check {@link QosCallbackException} for more information.     *
     *
     * @param exception wraps the underlying cause
     */
    public void onError(@NonNull final QosCallbackException exception) {
    }

    /**
     * Called when a Qos Session first becomes available to the callback or if its attributes have
     * changed.
     * Note: There is no guarantee that the session attributes have changed between invocations.
     *
     * @param session the available session
     * @param sessionAttributes the attributes of the session
     */
    public void onQosSessionAvailable(@NonNull final QosSession session,
            @NonNull final QosSessionAttributes sessionAttributes) {
    }

    /**
     * Called when an available Qos Session is now lost.
     *
     * @param session the lost session
     */
    public void onQosSessionLost(@NonNull final QosSession session) {
    }

    /**
     * Exception thrown if there is a problem while registering {@link QosCallback}.
     *
     * Thrown synchronously when the callback is registered with {@link ConnectivityManager}.
     */
    public static class QosCallbackRegistrationException extends Exception {
    }
}
