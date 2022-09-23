/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.webkit;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.UserPackage;
import android.webkit.WebViewFactory;
import android.webkit.WebViewProviderInfo;
import android.webkit.WebViewZygote;

import java.util.List;

/**
 * Default implementation for the WebView preparation Utility interface.
 * @hide
 */
class SystemImpl implements SystemInterface {
    private static final String TAG = SystemImpl.class.getSimpleName();

    @Override
    public WebViewProviderAuthority getProviderAuthority() {
        return WebViewProviderAuthority.getInstance();
    }

    public long getFactoryPackageVersion(String packageName) throws NameNotFoundException {
        PackageManager pm = AppGlobals.getInitialApplication().getPackageManager();
        return pm.getPackageInfo(packageName, PackageManager.MATCH_FACTORY_ONLY)
                .getLongVersionCode();
    }

    @Override
    public int onWebViewProviderChanged(PackageInfo packageInfo) {
        return WebViewFactory.onWebViewProviderChanged(packageInfo);
    }

    @Override
    public String getUserChosenWebViewProvider(Context context) {
        return Settings.Global.getString(context.getContentResolver(),
                Settings.Global.WEBVIEW_PROVIDER);
    }

    @Override
    public void updateUserSetting(Context context, String newProviderName) {
        Settings.Global.putString(context.getContentResolver(),
                Settings.Global.WEBVIEW_PROVIDER,
                newProviderName == null ? "" : newProviderName);
    }

    @Override
    public void killPackageDependents(String packageName) {
        try {
            ActivityManager.getService().killPackageDependents(packageName,
                    UserHandle.USER_ALL);
        } catch (RemoteException e) {
        }
    }

    @Override
    public void enablePackageForAllUsers(Context context, String packageName, boolean enable) {
        UserManager userManager = (UserManager)context.getSystemService(Context.USER_SERVICE);
        for(UserInfo userInfo : userManager.getUsers()) {
            enablePackageForUser(packageName, enable, userInfo.id);
        }
    }

    private void enablePackageForUser(String packageName, boolean enable, int userId) {
        try {
            AppGlobals.getPackageManager().setApplicationEnabledSetting(
                    packageName,
                    enable ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0,
                    userId, null);
        } catch (RemoteException | IllegalArgumentException e) {
            Log.w(TAG, "Tried to " + (enable ? "enable " : "disable ") + packageName
                    + " for user " + userId + ": " + e);
        }
    }

    @Override
    public boolean systemIsDebuggable() {
        return Build.IS_DEBUGGABLE;
    }

    @Override
    public PackageInfo getPackageInfoForProvider(WebViewProviderInfo configInfo)
            throws NameNotFoundException {
        PackageManager pm = AppGlobals.getInitialApplication().getPackageManager();
        return pm.getPackageInfo(configInfo.packageName, PACKAGE_FLAGS);
    }

    @Override
    public List<UserPackage> getPackageInfoForProviderAllUsers(Context context,
            WebViewProviderInfo configInfo) {
        return UserPackage.getPackageInfosAllUsers(context, configInfo.packageName, PACKAGE_FLAGS);
    }

    @Override
    public int getMultiProcessSetting(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                                      Settings.Global.WEBVIEW_MULTIPROCESS, 0);
    }

    @Override
    public void setMultiProcessSetting(Context context, int value) {
        Settings.Global.putInt(context.getContentResolver(),
                               Settings.Global.WEBVIEW_MULTIPROCESS, value);
    }

    @Override
    public void notifyZygote(boolean enableMultiProcess) {
        WebViewZygote.setMultiprocessEnabled(enableMultiProcess);
    }

    @Override
    public void ensureZygoteStarted() {
        WebViewZygote.getProcess();
    }

    @Override
    public boolean isMultiProcessDefaultEnabled() {
        // Multiprocess is enabled by default for all devices.
        return true;
    }

    // flags declaring we want extra info from the package manager for webview providers
    private final static int PACKAGE_FLAGS = PackageManager.GET_META_DATA
            | PackageManager.GET_SIGNATURES | PackageManager.GET_SHARED_LIBRARY_FILES
            | PackageManager.MATCH_DEBUG_TRIAGED_MISSING | PackageManager.MATCH_ANY_USER;
}
