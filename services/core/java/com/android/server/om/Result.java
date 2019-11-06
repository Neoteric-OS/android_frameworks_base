/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.om;

import static com.android.server.om.OverlayManagerService.TAG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Slog;

final class Result {
    private static final int STATE_ERROR = 0;
    private static final int STATE_OK = 1;

    private static final Result ERROR = new Result(STATE_ERROR, null, -1);
    private static final Result OK = new Result(STATE_OK, null, -1);

    private final int mState;
    private final String mTargetPackageName;
    private final int mUserId;

    private Result(int state, @Nullable String targetPackageName, int userId) {
        mState = state;
        mTargetPackageName = targetPackageName;
        mUserId = userId;
    }

    public static Result error() {
        return ERROR;
    }

    public static Result ok() {
        return OK;
    }

    public static Result ok(@NonNull String targetPackageName, int userId) {
        return new Result(STATE_OK, targetPackageName, userId);
    }

    public boolean isOk() {
        return mState == STATE_OK;
    }

    public boolean hasValue() {
        return mTargetPackageName != null;
    }

    public @Nullable String getTargetPackageName() {
        if (mState == STATE_ERROR) {
            Slog.wtf(TAG, "Result#getTargetPackageName called in ERROR state");
            return null;
        }
        return mTargetPackageName;
    }

    public int getUserId() {
        if (mState == STATE_ERROR) {
            Slog.wtf(TAG, "Result#getUserId called in ERROR state");
            return -1;
        }
        return mUserId;
    }
}
