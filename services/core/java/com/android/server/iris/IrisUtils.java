/**
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.iris;

import android.content.Context;
import android.hardware.iris.Iris;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.List;

/**
 * Utility class for dealing with irises and iris settings.
 */
public class IrisUtils {

    private static final Object sInstanceLock = new Object();
    private static IrisUtils sInstance;

    @GuardedBy("this")
    private final SparseArray<IrisUserState> mUsers = new SparseArray<>();

    public static IrisUtils getInstance() {
        synchronized (sInstanceLock) {
            if (sInstance == null) {
                sInstance = new IrisUtils();
            }
        }
        return sInstance;
    }

    private IrisUtils() {
    }

    public List<Iris> getIrisesForUser(Context ctx, int userId) {
        return getStateForUser(ctx, userId).getIrises();
    }

    public void addIrisForUser(Context ctx, int irisId, int userId) {
        getStateForUser(ctx, userId).addIris(irisId, userId);
    }

    public void removeIrisIdForUser(Context ctx, int irisId, int userId) {
        getStateForUser(ctx, userId).removeIris(irisId);
    }

    public void renameIrisForUser(Context ctx, int irisId, int userId, CharSequence name) {
        if (TextUtils.isEmpty(name)) {
            // Don't do the rename if it's empty
            return;
        }
        getStateForUser(ctx, userId).renameIris(irisId, name);
    }

    private IrisUserState getStateForUser(Context ctx, int userId) {
        synchronized (this) {
            IrisUserState state = mUsers.get(userId);
            if (state == null) {
                state = new IrisUserState(ctx, userId);
                mUsers.put(userId, state);
            }
            return state;
        }
    }
}

