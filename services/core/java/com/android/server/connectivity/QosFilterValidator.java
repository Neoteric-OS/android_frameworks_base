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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.TRANSPORT_TEST;

import android.annotation.NonNull;
import android.net.QosCallbackException;
import android.net.QosFilter;
import android.net.QosSocketFilter;
import android.system.ErrnoException;
import android.system.Os;

import java.util.Objects;
import java.util.Optional;

/**
 * Validates the different types of qos filters.
 *
 * Keeping validation methods on the qos filters themselves meant there would be a bigger
 * code footprint in each application and make future updates to behavior more difficult which
 * lead to a validator class.
 *
 * The QosFilter abstraction is broken here since because we would only need one validator at this
 * time and so creating multiple a filter validator for the just socket didn't make since.
 * We can break out other validators in the future when more types of filters arrive.
 *
 * @hide
 */
public class QosFilterValidator {
    /**
     * Performs validation on the filter
     * @return null if validation is passed
     */
    @NonNull
    public Optional<Integer> validate(@NonNull final QosFilter filter,
            @NonNull final NetworkAgentInfo networkAgentInfo) {
        Objects.requireNonNull(filter, "filter must be non-null");
        Objects.requireNonNull(networkAgentInfo, "networkAgentInfo must be non-null");
        if (filter instanceof QosSocketFilter) {
            return validateSocketFilter((QosSocketFilter) filter, networkAgentInfo);
        } else {
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<Integer> validateSocketFilter(QosSocketFilter filter, NetworkAgentInfo info) {
        if (isTest(info)) {
            return Optional.empty();
        } else {
            try {
                if (filter.getParcelFileDescriptor() == null
                        || filter.getParcelFileDescriptor().getFileDescriptor() == null
                        || Os.getsockname(
                                filter.getParcelFileDescriptor().getFileDescriptor()) == null) {
                    return Optional.of(QosCallbackException.EX_TYPE_FILTER_SOCKET_NOT_BOUND);
                } else {
                    return Optional.empty();
                }
            } catch (ErrnoException e) {
                return Optional.empty();
            }
        }
    }

    //Checks to see if this is a test network and in that case, we do not need to validate that the
    //socket is valid
    private boolean isTest(NetworkAgentInfo info) {
        return info.networkCapabilities.getTransportTypes().length == 1
                && info.networkCapabilities.hasTransport(TRANSPORT_TEST);
    }
}
