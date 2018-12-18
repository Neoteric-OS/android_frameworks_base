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

package android.net.ipmemorystore;

import android.annotation.NonNull;

/**
 * A parcelable status representing the result of an operation.
 * Parcels as StatusParceled.
 * @hide
 */
public class Status {
    public static final int SUCCESS = 0;

    public static final int ERROR_GENERIC = -1;
    public static final int ERROR_ILLEGAL_ARGUMENT = -2;
    public static final int ERROR_DATABASE_CANNOT_BE_OPENED = -3;

    public final int resultCode;

    public Status(final int resultCode) {
        this.resultCode = resultCode;
    }

    Status(@NonNull final StatusParceled parceled) {
        this(parceled.resultCode);
    }

    /** Converts this Status to a parcelable object */
    @NonNull
    public StatusParceled toParcelable() {
        final StatusParceled parceled = new StatusParceled();
        parceled.resultCode = resultCode;
        return parceled;
    }

    public boolean isSuccess() {
        return SUCCESS == resultCode;
    }
}
