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

package com.android.server.om;

import android.annotation.NonNull;
import android.annotation.Nullable;

final class PackageInfoLite {
    public final String packageName;
    public final String overlayTarget; // FIXME: should be overlayTargetPackageName
    public final int uid;
    public final boolean isStaticOverlay; // FIXME: should be removed
    public final int overlayPriority; // FIXME: should be removed (and before that, renamed priority)
    public final String codePath;

    public PackageInfoLite(@NonNull final String packageName, @Nullable final String overlayTarget,
            final int uid, final boolean isStaticOverlay, final int overlayPriority,
            @NonNull final String codePath) {
        this.packageName = packageName;
        this.overlayTarget = overlayTarget;
        this.uid = uid;
        this.isStaticOverlay = isStaticOverlay;
        this.overlayPriority = overlayPriority;
        this.codePath = codePath;
    }
}
