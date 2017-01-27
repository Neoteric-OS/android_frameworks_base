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

package android.os.vintf;

/** @hide */
public enum Transport {
    /* Keep these values align with system/libvintf/include/vintf/Transport.h. */
    EMPTY       (0),
    PASSTHROUGH (1),
    HWBINDER    (2);

    Transport(long id) {
        mId = id;
    }
    public long getValue() {
        return mId;
    }
    public static Transport fromValue(long id) throws IllegalArgumentException {
        for (Transport tr : values()) {
            if (tr.mId == id) {
                return tr;
            }
        }
        throw new IllegalArgumentException(Long.toString(id));
    }
    private final long mId;
}
