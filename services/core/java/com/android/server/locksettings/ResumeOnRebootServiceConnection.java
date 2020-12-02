/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.locksettings;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.resumeonreboot.IResumeOnRebootService;
import android.service.resumeonreboot.ResumeOnRebootService;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ResumeOnRebootServiceConnection {

    private static final String TAG = "ResumeOnRebootServiceConnection";
    private final Context mContext;
    private final ComponentName mComponentName;
    private IResumeOnRebootService binder;

    public ResumeOnRebootServiceConnection(Context context,
            @NonNull ComponentName componentName) {
        mContext = context;
        mComponentName = componentName;
    }

    public void unbindService() {
        mContext.unbindService(new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;

            }
        });
    }

    public void bindToService(long timeOut) throws TimeoutException {
        if (binder == null || !binder.asBinder().isBinderAlive()) {
            CountDownLatch connectionLatch = new CountDownLatch(1);
            Intent intent = new Intent();
            intent.setComponent(mComponentName);
            final boolean success = mContext.bindServiceAsUser(intent, new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                            binder = IResumeOnRebootService.Stub.asInterface(service);
                            connectionLatch.countDown();
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                        }
                    },
                    Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE,
                    BackgroundThread.getHandler(), UserHandle.of(mContext.getUserId()));

            if (!success) {
                Slog.e(TAG, "Binding: " + mComponentName + " u" + mContext.getUserId()
                        + " failed.");
            }
            waitForLatch(connectionLatch, "serviceConnection", timeOut);
        }
    }

    public byte[] wrapSecret(byte[] secret, long lifeTimeInMillis,
            long timeOut)
            throws RemoteException, TimeoutException {
        if (binder == null || !binder.asBinder().isBinderAlive()) {
            throw new RemoteException("Service not bound");
        }
        CountDownLatch binderLatch = new CountDownLatch(1);
        ResumeOnRebootServiceCallback resultCallback =
                new ResumeOnRebootServiceCallback(
                        binderLatch);
        binder.wrapSecret(secret, lifeTimeInMillis, new RemoteCallback(resultCallback));
        waitForLatch(binderLatch, "wrapSecret", timeOut);
        return resultCallback.result.getByteArray(ResumeOnRebootService.CIPHER_TEXT_KEY);
    }

    public byte[] unwrap(byte[] cipherText, long timeOut)
            throws RemoteException, TimeoutException {
        if (binder == null || !binder.asBinder().isBinderAlive()) {
            throw new RemoteException("Service not bound");
        }
        CountDownLatch binderLatch = new CountDownLatch(1);
        ResumeOnRebootServiceCallback resultCallback =
                new ResumeOnRebootServiceCallback(
                        binderLatch);
        binder.unwrap(cipherText, new RemoteCallback(resultCallback));
        waitForLatch(binderLatch, "unWrapSecret", timeOut);
        return resultCallback.getResult().getByteArray(ResumeOnRebootService.SECRET_KEY);
    }

    private void waitForLatch(CountDownLatch latch, String reason, long timeOut)
            throws TimeoutException {
        try {
            if (!latch.await(timeOut, TimeUnit.SECONDS)) {
                throw new TimeoutException("Latch wait for " + reason + " elapsed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Latch wait for " + reason + " interrupted");
        }
    }

    private static class ResumeOnRebootServiceCallback implements
            RemoteCallback.OnResultListener {

        private final CountDownLatch mResultLatch;
        private Bundle result;

        private ResumeOnRebootServiceCallback(CountDownLatch resultLatch) {
            this.mResultLatch = resultLatch;
        }

        @Override
        public void onResult(@Nullable Bundle result) {
            this.result = result;
            mResultLatch.countDown();
        }

        private Bundle getResult() {
            return result;
        }
    }
}

