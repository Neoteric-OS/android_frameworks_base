/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@IgnoreUnderRavenwood(blockedBy = PowerManager.class)
public class BinderProxyTest {
    private static class CountingListener implements Binder.ProxyTransactListener {
        int mStartedCount;
        int mEndedCount;

        public Object onTransactStarted(IBinder binder, int transactionCode) {
            mStartedCount++;
            return null;
        }

        public void onTransactEnded(@Nullable Object session) {
            mEndedCount++;
        }
    };

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private Context mContext;
    private PowerManager mPowerManager;

    /**
     * Setup any common data for the upcoming tests.
     */
    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
    }

    @Test
    @MediumTest
    public void testNoListener() throws Exception {
        CountingListener listener = new CountingListener();
        Binder.setProxyTransactListener(listener);
        Binder.setProxyTransactListener(null);

        mPowerManager.isInteractive();

        assertEquals(0, listener.mStartedCount);
        assertEquals(0, listener.mEndedCount);
    }

    @Test
    @MediumTest
    public void testListener() throws Exception {
        CountingListener listener = new CountingListener();
        Binder.setProxyTransactListener(listener);

        mPowerManager.isInteractive();

        assertEquals(1, listener.mStartedCount);
        assertEquals(1, listener.mEndedCount);
    }

    @Test
    @MediumTest
    public void testSessionPropagated() throws Exception {
        Binder.setProxyTransactListener(new Binder.ProxyTransactListener() {
            public Object onTransactStarted(IBinder binder, int transactionCode) {
                return "foo";
            }

            public void onTransactEnded(@Nullable Object session) {
                assertEquals("foo", session);
            }
        });

        // Check it does not throw..
        mPowerManager.isInteractive();
    }

    private class MyServiceConnection implements ServiceConnection, AutoCloseable {
        private final CountDownLatch bindLatch = new CountDownLatch(1);
        private IBinder mRemoteBinder = null;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteBinder = service;
            bindLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}

        public IBinder getBinder() {
            return mRemoteBinder;
        }

        public IBinderProxyTest getInterface() {
            return IBinderProxyTest.Stub.asInterface(mRemoteBinder);
        }

        private MyServiceConnection() {}
        public MyServiceConnection bind() throws Exception {
            mContext.bindService(
                    new Intent(mContext, BinderProxyService.class),
                    this,
                    Context.BIND_AUTO_CREATE);

            if (!bindLatch.await(500, TimeUnit.MILLISECONDS)) {
                fail(
                        "Timed out while binding service: "
                                + BinderProxyService.class.getSimpleName());
            }
            assertTrue(mRemoteBinder instanceof BinderProxy);
            assertNotNull(mRemoteBinder);
            return this;
        }

        public void close() {
            mContext.unbindService(this);
        }
    }

    @Test
    @SmallTest
    public void testClose() throws Exception {
        try (MyServiceConnection connection = new MyServiceConnection().bind()) {
            BinderProxy binder = (BinderProxy) connection.getInterface().newBinder();
            assertEquals(true, binder.close());
            assertEquals(false, binder.close());
        }
    }

    @Test
    @SmallTest
    public void testCantCloseLostBinder() throws Exception {
        try (MyServiceConnection connection = new MyServiceConnection().bind()) {
            BinderProxy binder1 = (BinderProxy) connection.getInterface().newBinder();

            // when we send the binder out again, and we re-read it, it's impossible for
            // the infrastructure to understand where else it goes in the process (here
            // we know it goes back to the same stack frame, but infra can only tell it
            // is passed to Java multiple times)
            BinderProxy binder2 = (BinderProxy) connection.getInterface().repeatBinder(binder1);

            assertEquals(binder1, binder2);
            assertEquals(false, binder1.close());
        }
    }

    @Test
    @MediumTest
    public void testGetExtension() throws Exception {
        try (MyServiceConnection connection = new MyServiceConnection().bind()) {
            IBinder extension = connection.getBinder().getExtension();
            assertNotNull(extension);
            assertTrue(extension.pingBinder());
        }
    }
}
