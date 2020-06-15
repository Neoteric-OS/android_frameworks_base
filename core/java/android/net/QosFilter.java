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

import java.net.Socket;

/**
 * Filters Qos sessions before being sent to a {@link QosCallback}.  Both the filter and callback
 * are registered as a pair through
 * {@link ConnectivityManager#registerQosCallback(QosFilter, QosCallback)}.
 *
 * @hide
 */
@SystemApi
public abstract class QosFilter {

    /**
     * The network used with this filter.
     * @return the registered {@link Network}
     */
    @NonNull
    public abstract Network getNetwork();

    /**
     * Creates a {@link QosSocketFilter} given a network and bound socket.  The corresponding
     * {@link QosCallback} will only receive {@link QosSession} that correspond with the
     * socket's binding.
     *
     * @param network the registered {@link Network}
     * @param socket the filter will only sent back sessions that match the binding of this socket
     * @return the socket filter
     */
    @NonNull
    public static QosSocketFilter fromSocket(
            @NonNull final Network network, @NonNull final Socket socket) {
        return new QosSocketFilter(network, socket);
    }

    /**
     * Indicates that the Network used within the QosFilter is now invalid.
     * This is passed through QosCallbackException.getCause().
     */
    public static class NetworkReleasedException extends Exception {

        public NetworkReleasedException() {
            super();
        }

        public @QosCallbackException.ExceptionType int getExceptionType() {
            return QosCallbackException.EX_TYPE_FILTER_NETWORK_RELEASED;
        }

        @NonNull
        @Override
        public String getMessage() {
            return "The Network was released and is no longer available";
        }
    }

    /**
     * The filter provided is not supported on this device.
     */
    public static class NotSupportException extends Exception {
        public NotSupportException() {
        }

        public @QosCallbackException.ExceptionType int getExceptionType() {
            return QosCallbackException.EX_TYPE_FILTER_NOT_SUPPORTED;
        }

        @NonNull
        @Override
        public String getMessage() {
            // We can get more granular with using the bundle down the road
            return "This device does not support the specified filter";
        }
    }
}

