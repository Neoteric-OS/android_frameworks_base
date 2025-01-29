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

package android.os;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.frameworks.coretests.bdr_helper_app.TestCommsReceiver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests functionality of {@link android.os.IBinder.FrozenStateChangeCallback}.
 */
@RunWith(AndroidJUnit4.class)
@IgnoreUnderRavenwood(blockedBy = ActivityManager.class)
public final class FreezeAwareBinderTest {
    private static final String TAG = FreezeAwareBinder.class.getSimpleName();
    private static final String TEST_PACKAGE_NAME =
            "com.android.frameworks.coretests.bdr_helper_app1";
    private static final int CALLBACK_WAIT_TIMEOUT_SECS = 5;

    private Context mContext;
    private Handler mHandler;
    private IBinder mTestAppBinder;
    private IInterface mFakeInterface;

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mHandler = new Handler(Looper.getMainLooper());
        ((ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE)).killUid(
                mContext.getPackageManager().getPackageUid(TEST_PACKAGE_NAME, 0),
                "Wiping Test Package");
        mTestAppBinder = getNewRemoteBinder(TEST_PACKAGE_NAME);
        mFakeInterface = () -> mTestAppBinder;
    }

    private IBinder getNewRemoteBinder(String testPackage) throws InterruptedException {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicInteger resultCode = new AtomicInteger(Activity.RESULT_CANCELED);
        final AtomicReference<Bundle> resultExtras = new AtomicReference<>();

        final Intent intent = new Intent(TestCommsReceiver.ACTION_GET_BINDER)
                .setClassName(testPackage, TestCommsReceiver.class.getName());
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                resultCode.set(getResultCode());
                resultExtras.set(getResultExtras(true));
                resultLatch.countDown();
            }
        }, mHandler, Activity.RESULT_CANCELED, null, null);

        assertTrue("Request for binder timed out", resultLatch.await(5, TimeUnit.SECONDS));
        assertEquals(Activity.RESULT_OK, resultCode.get());
        return resultExtras.get().getBinder(TestCommsReceiver.EXTRA_KEY_BINDER);
    }

    @Test
    public void addCallsToReducer() throws Exception {
        FreezeAwareBinder.Reducer<Integer> mockReducer =
                Mockito.mock(FreezeAwareBinder.Reducer.class);
        FreezeAwareBinder fab = new FreezeAwareBinder<IInterface, Integer>(
                mFakeInterface, mockReducer, Runnable::run);
        fab.call(1);
        fab.call(2);
        wait(() -> verify(mockReducer).add(1, IBinder.FrozenStateChangeCallback.STATE_UNFROZEN));
        wait(() -> verify(mockReducer).add(2, IBinder.FrozenStateChangeCallback.STATE_UNFROZEN));
        freezeApp();
        fab.call(3);
        fab.call(4);
        wait(() -> verify(mockReducer).add(3, IBinder.FrozenStateChangeCallback.STATE_FROZEN));
        wait(() -> verify(mockReducer).add(4, IBinder.FrozenStateChangeCallback.STATE_FROZEN));
        unfreezeApp();
        verify(mockReducer).flush();
    }

    @Test
    public void throwDeadObjectExceptionWhenCallingDeadBinder() throws Exception {
        FreezeAwareBinder.Reducer<Integer> mockReducer =
                Mockito.mock(FreezeAwareBinder.Reducer.class);
        FreezeAwareBinder fab = new FreezeAwareBinder<IInterface, Integer>(
                mFakeInterface, mockReducer, Runnable::run);
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("killall " + TEST_PACKAGE_NAME);
        wait(() -> assertThrows(DeadObjectException.class, () -> fab.call(1)));
    }

    @Test
    public void throwDeadObjectExceptionWhenRegisteringOnDeadBinder() throws Exception {
        // Kill the process and wait for its death notification to ensure it's already dead.
        ConditionVariable cv = new ConditionVariable();
        mTestAppBinder.linkToDeath(() -> cv.open(), 0);
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("killall " + TEST_PACKAGE_NAME);
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));

        // Now register a FreezeAwareBinder and check that a DeadObjectException is thrown.
        FreezeAwareBinder.Reducer<Integer> mockReducer =
                Mockito.mock(FreezeAwareBinder.Reducer.class);
        wait(() -> assertThrows(DeadObjectException.class,
                () -> new FreezeAwareBinder<IInterface, Integer>(
                mFakeInterface, mockReducer, Runnable::run)));
    }

    @Test
    public void dropCallsWhenFrozen() throws Exception {
        List<String> capturedData = new ArrayList<>();
        FreezeAwareBinder fab = new FreezeAwareBinder<IInterface, String>(
                mFakeInterface, FreezeAwareBinder.createDropReducer(
                    (data) -> capturedData.add(data)), Runnable::run);
        fab.call("1");
        fab.call("2");
        freezeApp();
        fab.call("3");
        fab.call("4");
        unfreezeApp();
        assertArrayEquals(new String[]{"1", "2"}, capturedData.toArray(new String[0]));
    }

    @Test
    public void replayAllCalls() throws Exception {
        List<String> capturedData = new ArrayList<>();
        FreezeAwareBinder.Reducer<String> reducer = FreezeAwareBinder.createReplayReducer(
                (data) -> capturedData.add(data));
        FreezeAwareBinder fab = new FreezeAwareBinder<IInterface, String>(
                mFakeInterface, reducer, Runnable::run);
        fab.call("1");
        fab.call("2");
        wait(() -> assertArrayEquals(new String[]{"1", "2"}, capturedData.toArray(new String[0])));
        capturedData.clear();
        freezeApp();
        fab.call("3");
        fab.call("4");
        assertArrayEquals(new String[]{}, capturedData.toArray(new String[0]));
        unfreezeApp();
        assertArrayEquals(new String[]{"3", "4"}, capturedData.toArray(new String[0]));
    }

    @Test
    public void replayMostRecentCall() throws Exception {
        List<String> capturedData = new ArrayList<>();
        FreezeAwareBinder.Reducer<String> reducer = FreezeAwareBinder.createReplayMostRecentReducer(
                (data) -> capturedData.add(data));
        FreezeAwareBinder fab = new FreezeAwareBinder<IInterface, String>(
                mFakeInterface, reducer, Runnable::run);
        fab.call("1");
        fab.call("2");
        wait(() -> assertArrayEquals(new String[]{"1", "2"}, capturedData.toArray(new String[0])));
        capturedData.clear();
        freezeApp();
        fab.call("3");
        fab.call("4");
        assertArrayEquals(new String[]{}, capturedData.toArray(new String[0]));
        unfreezeApp();
        assertArrayEquals(new String[]{"4"}, capturedData.toArray(new String[0]));
    }

    private void wait(Runnable r) {
        long startTime = System.currentTimeMillis();
        long timeout = 5000; // 5 seconds
        AssertionError lastError = null;

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                r.run();
                return; // Success!
            } catch (AssertionError e) {
                lastError = e;
                // Expected: The method hasn't been called yet.
                // Wait and try again.
                try {
                    Thread.sleep(100); // Small delay
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt(); // Restore interrupt status
                }
            }
        }
        throw lastError;
    }

    private void freezeApp() throws Exception {
        ConditionVariable cv = new ConditionVariable();
        mTestAppBinder.addFrozenStateChangeCallback(
                Runnable::run,
                (who, state) -> {
                    if (state == IBinder.FrozenStateChangeCallback.STATE_FROZEN) {
                        cv.open();
                    }
                });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("am freeze " + TEST_PACKAGE_NAME);
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));
    }

    private void unfreezeApp() throws Exception {
        ConditionVariable cv = new ConditionVariable();
        mTestAppBinder.addFrozenStateChangeCallback(
                Runnable::run,
                (who, state) -> {
                    if (state == IBinder.FrozenStateChangeCallback.STATE_UNFROZEN) {
                        cv.open();
                    }
                });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("am unfreeze " + TEST_PACKAGE_NAME);
        assertTrue(cv.block(CALLBACK_WAIT_TIMEOUT_SECS * 1000));
    }
}
