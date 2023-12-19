package com.android.test.binder;
import com.android.test.binder.IFooCallback;

interface IFoo {
    void registerCallback(in IFooCallback callback);

    void invokeCallback(int arg);
}
