/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.test.embedded;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

public class MainActivity extends Activity {

    static {
        System.loadLibrary("punchtest");
    }

    @VisibleForTesting
    static final String INTENT_TYPE = "android.test.embedded.EMBEDDED_LIB_LOADED";

    @Override
    public void onCreate(Bundle savedOnstanceState) {
        super.onCreate(savedOnstanceState);
        Intent intent = new Intent(INTENT_TYPE);
        // Send broadcast so that test can know app has launched and lib is loaded
        sendBroadcast(intent);
    }
}
