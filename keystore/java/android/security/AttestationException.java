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

package android.security;

import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Thrown when {@link AttestationManager} is unable to attest the given key or handle the resulting
 * attestation record.
 */
public class AttestationException extends Exception {
    /**
     * Constructs a new {@code AttestationException} with the current stack trace and the specified
     * message.
     *
     * @param message the detail message for this exception.
     * @hide
     */
    public AttestationException(@NonNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code AttestationException} with the current stack trace, the specified
     * message and the specified cause.
     *
     * @param message the detail message for this exception.
     * @param cause the cause of this exception.
     * @hide
     */
    public AttestationException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
