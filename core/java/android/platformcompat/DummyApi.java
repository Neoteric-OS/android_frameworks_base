/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.platformcompat;

import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
/**
 * This is a dummy API to test gating
 *
 * @hide
 */
public class DummyApi {

    @ChangeId
    public static final long CHANGE_ID = 666013;

    @ChangeId
    public static final long CHANGE_ID_1 = 666014;

    @ChangeId
    public static final long CHANGE_ID_2 = 666015;

    @ChangeId
    public static final long CHANGE_SYSTEM_SERVER = 666016;

    /**
     * Dummy method
     * @return "A" if change is enabled, "B" otherwise.
     */
    public static String dummyFunc() {
        if (Compatibility.isChangeEnabled(CHANGE_ID)) {
            return "A";
        }
        return "B";
    }

    /**
     * Dummy combined method
     * @return "A" if change 1 is enabled and change 2 is disabled, "B" otherwise.
     */
    public static String dummyCombinedFunc() {
        if (!Compatibility.isChangeEnabled(CHANGE_ID_1)
                && !Compatibility.isChangeEnabled(CHANGE_ID_2)) {
            return "0";
        } else if (!Compatibility.isChangeEnabled(CHANGE_ID_1)
                && Compatibility.isChangeEnabled(CHANGE_ID_2)) {
            return "1";
        } else if (Compatibility.isChangeEnabled(CHANGE_ID_1)
                && !Compatibility.isChangeEnabled(CHANGE_ID_2)) {
            return "2";
        }
        return "3";
    }
}
