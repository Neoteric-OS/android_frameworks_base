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

import android.annotation.NonNull;
import android.annotation.SystemApi;

/**
 * {@hide}
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public abstract class UserRestrictionsCallback {

    final IUserRestrictionsListener mListener = new IUserRestrictionsListener.Stub() {
        @Override
        public void onUserRestrictionsChanged(int userId, Bundle newRestrictions,
                Bundle prevRestrictions) {
            UserRestrictionsCallback.this
                .onUserRestrictionsChanged(userId, newRestrictions, prevRestrictions);
        }
    };

    /**
     * Called when a user restriction changes.
     *
     * @param userId target user id
     * @param newRestrictions new user restrictions
     * @param prevRestrictions user restrictions that were previously set
     */
    public abstract void onUserRestrictionsChanged(int userId, @NonNull Bundle newRestrictions,
            @NonNull Bundle prevRestrictions);
}
