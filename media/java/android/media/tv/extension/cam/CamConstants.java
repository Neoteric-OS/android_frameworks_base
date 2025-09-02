/*
 * Copyright (C) 2025 The Android Open Source Project
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
package android.media.tv.extension.cam;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class CamConstants {
    /**************************************OpResult************************************************/
    @IntDef({
            OP_Success,
            OP_INVALID_SLOT_ID,
            OP_CICAM_NOT_INSERTED,
            OP_UNKNOWN,
            OP_NOT_SUPPORTED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CamOpResult{}
    public static final int OP_Success = 0;
    public static final int OP_INVALID_SLOT_ID = -1;
    public static final int OP_CICAM_NOT_INSERTED = -2;
    public static final int OP_UNKNOWN = -3;
    public static final int OP_NOT_SUPPORTED = -6;
}
