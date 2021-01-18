/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @hide
 */
@SystemApi
@SystemService(Context.PAC_PROXY_SERVICE)
public class PacProxyManager {
    private final Context mContext;
    private final IPacProxyManager mService;
    private final Map<PacProxyInstalledCallback, PacProxyInstalledCallbackProxy>
            mCallbackMap = new ConcurrentHashMap<>();

    /** @hide */
    public PacProxyManager(Context context, IPacProxyManager service) {
        if (service == null) {
            throw new IllegalArgumentException("missing IPacProxyManager");
        }
        mContext = context;
        mService = service;
    }

    /**
     * Add a callback to start monitoring the event which reports from PacProxyInstaller.
     */
    public void registerPacProxyInstalledCallback(@NonNull Executor executor,
            @NonNull PacProxyInstalledCallback callback) {
        try {
            if (callback == null) {
                throw new NullPointerException("Callback cannot be null.");
            }

            final PacProxyInstalledCallbackProxy callbackProxy =
                    new PacProxyInstalledCallbackProxy(executor, callback);
            if (null != mCallbackMap.putIfAbsent(callback, callbackProxy)) {
                throw new IllegalArgumentException("Callback is already added.");
            }

            mService.registerCallback(callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove the callback to stop monitoring the event of PacProxyInstalledCallback.
     */
    public void unregisterPacProxyInstalledCallback(@NonNull PacProxyInstalledCallback callback) {
        try {
            if (callback == null) {
                throw new NullPointerException("Callback cannot be null.");
            }

            final PacProxyInstalledCallbackProxy callbackProxy = mCallbackMap.remove(callback);
            if (callbackProxy == null) return;

            mService.unregisterCallback(callbackProxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the PAC Proxy Installer with current Proxy information.
     */
    public void setCurrentProxyScriptUrl(@NonNull ProxyInfo proxy) {
        try {
            mService.setCurrentProxyScriptUrl(proxy);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * A callback class for monitoring changes of PAC proxy information.
     */
    public static class PacProxyInstalledCallback {
        /**
         * Notify that the PAC proxy has been installed.
         *
         * @param proxy the installed proxy.
         */
        public void onPacProxyInstalled(@NonNull ProxyInfo proxy) {}
    }

    /**
     * PacProxyInstalledCallback proxy for PacProxyInstalledCallback object.
     * @hide
     */
    public class PacProxyInstalledCallbackProxy extends Callback {
        private final Executor mExecutor;
        private final PacProxyInstalledCallback mCallback;

        PacProxyInstalledCallbackProxy(Executor executor, PacProxyInstalledCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onPacProxyInstalled(ProxyInfo proxy) {
            Binder.withCleanCallingIdentity(() -> {
                mExecutor.execute(() -> {
                    mCallback.onPacProxyInstalled(proxy);
                });
            });
        }
    }

    private static class Callback extends IPacProxyInstalledCallback.Stub {
        @Override
        public void onPacProxyInstalled(ProxyInfo proxy) {}
    }
}
