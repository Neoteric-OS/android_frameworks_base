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

package android.test.extract;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class PunchExtractedLibTest {

    @Test
    public void testPunchedNativeLibs_extractedLib() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        CountDownLatch receivedSignal = new CountDownLatch(1);

        IntentFilter intentFilter =
                new IntentFilter(MainActivity.INTENT_TYPE);
        BroadcastReceiver broadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        receivedSignal.countDown();
                    }
                };
        context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);

        Intent launchIntent =
                context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        context.startActivity(launchIntent);
        Assert.assertTrue("Failed to launch app", receivedSignal.await(10, TimeUnit.SECONDS));
    }
}
