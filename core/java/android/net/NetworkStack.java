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
package android.net;

import static android.Manifest.permission.NETWORK_STACK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.content.Context;

/**
 *
 * Constants for client code communicating with the network stack service.
 * @hide
 */
@SystemApi
@TestApi
public class NetworkStack {
    /**
     * Permission granted only to the NetworkStack APK, defined in NetworkStackStub with signature
     * protection level.
     * @hide
     */
    @SystemApi
    @TestApi
    public static final String PERMISSION_MAINLINE_NETWORK_STACK =
            "android.permission.MAINLINE_NETWORK_STACK";

    private NetworkStack() {}

    /**
     * If the NetworkStack or MAINLINE_NETWORK_STACK permission are not allowed for a particular
     * process, throw a {@link SecurityException}.
     *
     * @param context {@link android.content.Context} for the process.
     * @hide
     */
    @SystemApi
    @TestApi
    public static void checkNetworkStackPermission(final @NonNull Context context) {
        enforceAnyPermissionOf(context, NETWORK_STACK, PERMISSION_MAINLINE_NETWORK_STACK);
    }

    private static void enforceAnyPermissionOf(final @NonNull Context context,
            String... permissions) {
        if (!checkAnyPermissionOf(context, permissions)) {
            throw new SecurityException("Requires one of the following permissions: "
                + String.join(", ", permissions) + ".");
        }
    }

    private static boolean checkAnyPermissionOf(final @NonNull Context context,
            String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission) == PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

}
