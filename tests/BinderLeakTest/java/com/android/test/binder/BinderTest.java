/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.test.binder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Flags;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class BinderTest {
    @Rule
    public final ServiceTestRule serviceRule = new ServiceTestRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testDeathRecipientLeaksOrNot()
            throws RemoteException, TimeoutException, InterruptedException {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MyService.class);
        IFooProvider provider = IFooProvider.Stub.asInterface(serviceRule.bindService(intent));
        FooHolder holder = new FooHolder(provider.createFoo());

        ReferenceChecker checker = new ReferenceChecker(holder);

        DeathRecorder deathRecorder = new DeathRecorder();
        holder.registerDeathRecorder(deathRecorder);

        if (getSdkVersion() >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            /////////////////////////////////////////////
            // New behavior
            //
            // Reference chain at this moment:
            // holder --(java strong ref)--> FooHolder
            // FooHolder.mProxy --(java strong ref)--> IFoo.Proxy
            // IFoo.Proxy.mRemote --(java strong ref)--> BinderProxy
            // BinderProxy --(binder ref)--> Foo.Stub
            // In other words, the variable "holder" is the root of the reference chain.

            // By setting the variable to null, we make FooHolder, IFoo.Proxy, BinderProxy, and even
            // Foo.Stub unreachable.
            holder = null;

            // Ensure that the objects are garbage collected
            assertTrue(checker.forceGcAndCheckIfDeleted());
            assertTrue(provider.isFooGarbageCollected());

            // The binder has died, but we don't get notified since the death recipient is GC'ed.
            provider.killProcess();
            Thread.sleep(1000); // give some time for the service process to die and reaped
            assertFalse(deathRecorder.deathRecorded);
        } else {
            /////////////////////////////////////////////
            // Legacy behavior
            //
            // Reference chain at this moment:
            // JavaDeathRecipient --(JNI strong ref)--> FooHolder
            // holder --(java strong ref)--> FooHolder
            // FooHolder.mProxy --(java strong ref)--> IFoo.Proxy
            // IFoo.Proxy.mRemote --(java strong ref)--> BinderProxy
            // BinderProxy --(binder ref)--> Foo.Stub
            // So, BOTH JavaDeathRecipient and holder are roots of the reference chain.

            // Even if we set holder to null, it doesn't make other objects unreachable; they are
            // still reachable via the JNI strong ref.
            holder = null;

            // Check that objects are not garbage collected
            assertFalse(checker.forceGcAndCheckIfDeleted());
            assertFalse(provider.isFooGarbageCollected());

            // The legacy behavior is getting notified even when there's no reference
            provider.killProcess();
            Thread.sleep(1000); // give some time for the service process to die and reaped
            assertTrue(deathRecorder.deathRecorded);
        }
    }

    static class FooHolder implements IBinder.DeathRecipient {
        private IFoo mProxy;
        private DeathRecorder mDeathRecorder;

        FooHolder(IFoo proxy) throws RemoteException {
            proxy.asBinder().linkToDeath(this, 0);

            // A strong reference from DeathRecipient(this) to the binder proxy is created here
            mProxy = proxy;
        }

        public void registerDeathRecorder(DeathRecorder dr) {
            mDeathRecorder = dr;
        }

        @Override
        public void binderDied() {
            if (mDeathRecorder != null) {
                mDeathRecorder.deathRecorded = true;
            }
        }
    }

    static class DeathRecorder {
        public boolean deathRecorded = false;
    }

    private static int getSdkVersion() {
        return ApplicationProvider.getApplicationContext().getApplicationInfo().targetSdkVersion;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_NEW_CONSTRUCTOR_FOR_LIFETIME_BINDING)
    public void testBinderIsNotDeletedIfRemotelyReferenced()
            throws RemoteException, TimeoutException, InterruptedException {
        testLifetimeOfBinder(/* isLifetimeBoundToProxy= */false);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_BINDER_NEW_CONSTRUCTOR_FOR_LIFETIME_BINDING)
    public void testBinderIsDeletedIfLifetimeBounded()
            throws RemoteException, TimeoutException, InterruptedException {
        testLifetimeOfBinder(/* isLifetimeBoundToProxy= */true);
    }

    private int receivedArg;
    private void testLifetimeOfBinder(boolean isLifetimeBoundToProxy)
            throws RemoteException, TimeoutException, InterruptedException {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MyService.class);
        IFooProvider provider = IFooProvider.Stub.asInterface(serviceRule.bindService(intent));
        IFoo proxy = provider.createFooAndKeep();

        IFooCallback cb = new IFooCallback.Stub(isLifetimeBoundToProxy ? proxy : null) {
            @Override
            public void onCallback(int arg) {
                receivedArg = arg;
            }
        };
        proxy.registerCallback(cb);

        // Check if IFooCallback is alive
        proxy.invokeCallback(100);
        assertEquals(100, receivedArg);

        ReferenceChecker proxyChecker = new ReferenceChecker(proxy);
        ReferenceChecker cbChecker = new ReferenceChecker(cb);

        // Dropping the local reference to IFooCallback doesn't make it eligible for garbage
        // collection. This is because it is still referenced locally from proxy (in case
        // isLifetimeBoundToProxy is true) or remotely in the process that implements IFoo.
        cb = null;
        assertFalse(cbChecker.forceGcAndCheckIfDeleted());
        // Check again if IFooCallback is really alive
        proxy.invokeCallback(200);
        assertEquals(200, receivedArg);

        // Now, the local reference to proxy is dropped. Make sure that it is garbage collected.
        proxy = null;
        assertTrue(proxyChecker.forceGcAndCheckIfDeleted());

        // Check if the dropping of proxy also removes IFooCallback or not.
        if (isLifetimeBoundToProxy) {
            // The new behavior. If libetime is bound to the proxy, dropping the proxy object
            // deletes IFooCallback as well.
            assertTrue(cbChecker.forceGcAndCheckIfDeleted());
        } else {
            // The old behavior. Memory is leaked(?)
            assertFalse(cbChecker.forceGcAndCheckIfDeleted());

            // .. until the remote reference to it is gone. Prove this by killing the remote
            // process.
            provider.killProcess();
            Thread.sleep(1000); // give some time for the service process to die and reaped
            assertTrue(cbChecker.forceGcAndCheckIfDeleted());
        }
    }

}
