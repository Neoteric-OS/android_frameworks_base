/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.app.timezone;

import android.annotation.SystemApi;

/**
 * Constants related to the contract between the Android system and the privileged time zone updater
 * application.
 *
 * @hide
 */
@SystemApi
public final class RulesUpdaterContract {

    // TODO Move these to Intent?

    /**
     * The action of the intent that the Android system will broadcast. The intent will be targeted
     * at the configured updater application's package meaning the term "broadcast" only loosely
     * applies.
     * @hide
     */
    @SystemApi
    public final static String ACTION_TRIGGER_RULES_UPDATE_CHECK =
            "android.intent.action.timezone.TRIGGER_RULES_UPDATE_CHECK";

    /**
     * The extra containing the {@code byte[]} that should be passed to
     * {@link RulesManager#checkComplete(byte[], boolean)} when the
     * {@link #ACTION_TRIGGER_RULES_UPDATE_CHECK} intent has been processed.
     * @hide
     */
    @SystemApi
    public final static String EXTRA_CHECK_TOKEN =
            "android.intent.extra.timezone.CHECK_TOKEN";
}
