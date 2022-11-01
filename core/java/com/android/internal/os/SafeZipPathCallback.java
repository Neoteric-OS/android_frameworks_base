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
 * risk of zip path traversal vulnerabilities.
 *
 * @hide
 */
public class SafeZipPathCallback implements ZipPathValidator.Callback {
    /**
     * This change targets zip path traversal vulnerabilities by throwing
     * {@link java.util.zip.ZipException} if zip path entries contain ".." or start with "/".
     * <p>
     * The exception will be triggered by {@link java.util.zip.ZipInputStream#getNextEntry} or
     * {@link java.util.zip.ZipFile#ZipFile(String)}.
     * <p>
     * This validation is enabled for apps with targetSDK >= U.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static final long VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL = 242716250L;

    @Override
    public void onZipEntryAccess(@NonNull String name) throws ZipException {
        if (!CompatChanges.isChangeEnabled(VALIDATE_ZIP_PATH_FOR_PATH_TRAVERSAL)) {
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
