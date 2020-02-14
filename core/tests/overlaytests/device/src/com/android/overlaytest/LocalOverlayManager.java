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

package com.android.overlaytest;

import static android.content.Context.OVERLAY_SERVICE;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.NonNull;
import android.content.Context;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.os.UserHandle;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

class LocalOverlayManager {
    private static final long TIMEOUT = 30;

    public static void toggleOverlaysAndWait(@NonNull final String[] overlaysToEnable,
            @NonNull final String[] overlaysToDisable) throws Exception {
        final int userId = UserHandle.myUserId();
        OverlayManagerTransaction.Builder builder = new OverlayManagerTransaction.Builder();
        for (String pkg : overlaysToEnable) {
            builder.setEnabled(pkg, true, userId);
        }
        for (String pkg : overlaysToDisable) {
            builder.setEnabled(pkg, false, userId);
        }
        OverlayManagerTransaction transaction = builder.build();

        final Context ctx = InstrumentationRegistry.getContext();
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            while (true) {
                final String[] paths = ctx.getResources().getAssets().getApkPaths();
                if (arrayEndsWith(paths, overlaysToEnable)
                        && arrayDoesNotContain(paths, overlaysToDisable)) {
                    return true;
                }
                Thread.sleep(10);
            }
        });

        OverlayManager om = (OverlayManager) ctx.getSystemService(OVERLAY_SERVICE);
        if (!om.commit(transaction)) {
            throw new Exception("OMS transaction failed");
        }

        Executor executor = (cmd) -> new Thread(cmd).start();
        executor.execute(task);
        task.get(TIMEOUT, SECONDS);
    }

    private static boolean arrayEndsWith(@NonNull final String[] array,
            @NonNull final String[] tail) {
        if (array.length < tail.length) {
            return false;
        }
        for (int i = 0; i < tail.length; i++) {
            String a = array[array.length - tail.length + i];
            String t = tail[i];
            if (!a.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrayDoesNotContain(@NonNull final String[] array,
            @NonNull final String[] strings) {
        for (String s : strings) {
            for (String a : array) {
                if (a.contains(s)) {
                    return false;
                }
            }
        }
        return true;
    }
}
