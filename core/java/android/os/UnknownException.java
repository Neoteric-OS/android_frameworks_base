/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os;

import android.annotation.Nullable;
import android.util.AndroidRuntimeException;

/**
 * Exception for unknown error or not enough error information
 */
public class UnknownException extends AndroidRuntimeException {
    public UnknownException(@Nullable String msg) {
        super(msg);
    }
    public UnknownException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
