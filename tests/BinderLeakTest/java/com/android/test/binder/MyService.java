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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class MyService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new IFooProvider.Stub() {
            ReferenceChecker<IFoo> mFooChecker;
            IFoo mFoo;

            @Override
            public IFoo createFoo() throws RemoteException {
                return createFooInternal(false);
            }

            @Override
            public IFoo createFooAndKeep() throws RemoteException {
                return createFooInternal(true);
            }

            private IFoo createFooInternal(boolean keep) throws RemoteException {
                IFoo binder = new IFoo.Stub() {
                    IFooCallback mCb;
                    @Override
                    public void registerCallback(IFooCallback callback) {
                        mCb = callback;
                    }

                    @Override
                    public void invokeCallback(int arg) throws RemoteException {
                        mCb.onCallback(arg);
                    }
                };
                mFooChecker = new ReferenceChecker<>(binder);
                if (keep) {
                    mFoo = binder;
                }
                return binder;
            }

            @Override
            public boolean isFooGarbageCollected() throws RemoteException {
                return mFooChecker != null && mFooChecker.forceGcAndCheckIfDeleted();
            }

            @Override
            public void killProcess() throws RemoteException {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        };
    }
}
