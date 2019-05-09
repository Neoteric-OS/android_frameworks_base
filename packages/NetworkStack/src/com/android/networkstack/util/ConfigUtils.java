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

package com.android.networkstack.util;

import static android.net.util.NetworkStackUtils.NAMESPACE_CONNECTIVITY;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.net.util.NetworkStackUtils;

import androidx.annotation.StringRes;

/**
 * Collection of utilities for Configuation access.
 */
public class ConfigUtils {
    /**
     * Gets an integer setting from resources or device config
     *
     * configResource is used if set, followed by device config if set, followed by defaultResource.
     * If none of these are set then an exception is thrown.
     *
     * TODO(b/130324939): test that the resources can be overlayed by an RRO package.
     */
    public static int getIntSetting(@NonNull final Context context, @StringRes int configResource,
            @NonNull String symbol, @StringRes int defaultResource) {
        final Resources res = context.getResources();
        try {
            return res.getInteger(configResource);
        } catch (Resources.NotFoundException e) {
            return NetworkStackUtils.getDeviceConfigPropertyInt(NAMESPACE_CONNECTIVITY,
                    symbol, res.getInteger(defaultResource));
        }
    }
}
