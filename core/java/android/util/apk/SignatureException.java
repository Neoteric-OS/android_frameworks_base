/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.util.apk;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;

/**
 * Indicates an error when verifying the signature of the APK.
 *
 * @hide
 */
@FlaggedApi(android.content.pm.Flags.FLAG_CLOUD_COMPILATION_PM)
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class SignatureException extends Exception {
    private final int mCode;

    /** @hide */
    public SignatureException(int code, @NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
        mCode = code;
    }

    /**
     * Returns a code representing the cause, in one of the installation parse return codes in
     * {@link PackageManager}.
     */
    @FlaggedApi(android.content.pm.Flags.FLAG_CLOUD_COMPILATION_PM)
    public int getCode() {
        return mCode;
    }
}
