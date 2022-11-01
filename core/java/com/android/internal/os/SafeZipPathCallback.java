/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.os;

import android.annotation.NonNull;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.os.Build;

import dalvik.system.ZipPathValidator;

import java.util.zip.ZipException;

/**
 * A child implementation of the {@link dalvik.system.ZipPathValidator.Callback} that removes the
 * risk of path traversal vulnerabilities by throwing {@link java.util.zip.ZipException} on any zip
 * entry name that contains ".." or starts with "/".
 * <p>
 * This validation is enabled for apps with targetSDK >= U.
 *
 * @hide
 */
public class SafeZipPathCallback implements ZipPathValidator.Callback {

    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final long ZIP_ENTRY_NAME_VALIDATION = 242716250L;

    @Override
    public void onZipEntryAccess(@NonNull String name) throws ZipException {
        if (!CompatChanges.isChangeEnabled(ZIP_ENTRY_NAME_VALIDATION)) {
            return;
        }
        if (name.startsWith("/")) {
            throw new ZipException("Invalid zip entry path: " + name);
        }
        if (name.contains("..")) {
            // If the string does contain "..", break it down into its actual name elements to
            // ensure it actually contains ".." as a name, not just a name like "foo..bar" or even
            // "foo..", which should be fine.
            java.io.File file = new java.io.File(name);
            while (file != null) {
                if (file.getName().equals("..")) {
                    throw new ZipException("Invalid zip entry path: " + name);
                }
                file = file.getParentFile();
            }
        }
    }
}
