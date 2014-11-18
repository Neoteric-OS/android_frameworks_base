/**
 * Copyright (c) 2013, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.connectivity;

import static android.net.ConnectivityManager.NETID_UNSET;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Network;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.net.IProxyCallback;
import com.android.net.IProxyPortListener;
import com.android.net.IProxyService;
import com.android.server.IoThread;

import libcore.io.Streams;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @hide
 */
public class PacManager {
    public static final String PAC_PACKAGE = "com.android.pacprocessor";
    public static final String PAC_SERVICE = "com.android.pacprocessor.PacService";
    public static final String PAC_SERVICE_NAME = "com.android.net.IProxyService";

    public static final String PROXY_PACKAGE = "com.android.proxyhandler";
    public static final String PROXY_SERVICE = "com.android.proxyhandler.ProxyService";

    private static final String TAG = "PacManager";

    private static final String ACTION_PAC_REFRESH = "android.net.proxy.PAC_REFRESH";

    private static final String DEFAULT_DELAYS = "8 32 120 14400 43200";
    private static final int DELAY_1 = 0;
    private static final int DELAY_4 = 3;
    private static final int DELAY_LONG = 4;

    /** Keep these values up-to-date with ProxyService.java */
    public static final String KEY_PROXY = "keyProxy";

    private AlarmManager mAlarmManager;

    private final Context mContext;
    private final Handler mConnectivityHandler;
    private final int mProxyMessage;

    private class Pac {
        private final Network mNetwork;
        private final Uri mPacUrl;
        private String mPac;

        private int mCurrentDelay;

        private boolean mHasDownloaded;
        private boolean mHasStartedDownload;
        private boolean mDisposed;

        private PendingIntent mPacRefreshIntent;

        private int getNetId() {
            return mNetwork == null ? NETID_UNSET : mNetwork.netId;
        }

        private Runnable mPacDownloader = new Runnable() {
            @Override
            public void run() {
                if (Uri.EMPTY.equals(mPacUrl)) return;
                // Do not hold lock while waiting to download.
                String file;
                try {
                    file = get(mPacUrl.toString());
                } catch (IOException ioe) {
                    file = null;
                    Log.w(TAG, "Failed to load PAC file: " + ioe);
                }
                synchronized (mProxyLock) {
                    if (mDisposed) return;
                    if (file != null) {
                        if (!file.equals(mPac)) {
                            setCurrentProxyScriptLocked(file);
                        }
                        mDownloadsCompletedSinceLastBroadcast = true;
                        mHasDownloaded = true;
                        sendNetworkProxyBroadcastIfNeededLocked();
                        longScheduleLocked();
                    } else {
                        rescheduleLocked();
                    }
                }
            }
        };

        private BroadcastReceiver mPacRefreshIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                new Thread(mPacDownloader).start();
            }
        };

        public Pac(Uri pacUrl, Network network) {
            mNetwork = network;
            mPacUrl = pacUrl;
            mPac = null;

            final String intentAction = ACTION_PAC_REFRESH + getNetId();
            mPacRefreshIntent = PendingIntent.getBroadcast(
                    mContext, 0, new Intent(intentAction), 0);
            mContext.registerReceiver(mPacRefreshIntentReceiver, new IntentFilter(intentAction));

            mCurrentDelay = DELAY_1;
            mHasDownloaded = false;
            mHasStartedDownload = false;
            mDisposed = false;
            getAlarmManager().cancel(mPacRefreshIntent);
        }

        private void sendNetworkProxyBroadcastIfNeededLocked() {
            // For non-default Networks we need to force the broadcast, otherwise the
            // broadcast will not be sent because it does not look like the default
            // Network's proxy config has changed.
            if (getDefaultPacLocked() != this) {
                sendBroadcastLocked(true);
            } else {
                sendBroadcastIfNeededLocked();
            }
        }

        public boolean hasDownloadedLocked() {
            return mHasDownloaded;
        }

        public void startInitialDownloadLocked() {
            if (mHasStartedDownload) return;
            mHasStartedDownload = true;
            new Thread(mPacDownloader).start();
        }

        public void disposeLocked() {
            mDisposed = true;
            getAlarmManager().cancel(mPacRefreshIntent);
            mContext.unregisterReceiver(mPacRefreshIntentReceiver);
            if (mPac != null) {
                setCurrentProxyScriptLocked(null);
                sendNetworkProxyBroadcastIfNeededLocked();
            }
        }

        private int getNextDelay(int currentDelay) {
           if (++currentDelay > DELAY_4) {
               return DELAY_4;
           }
           return currentDelay;
        }

        private void longScheduleLocked() {
            mCurrentDelay = DELAY_1;
            setDownloadInLocked(DELAY_LONG);
        }

        private void rescheduleLocked() {
            mCurrentDelay = getNextDelay(mCurrentDelay);
            setDownloadInLocked(mCurrentDelay);
        }

        private void setDownloadInLocked(int delayIndex) {
            long delay = getDownloadDelay(delayIndex);
            long timeTillTrigger = 1000 * delay + SystemClock.elapsedRealtime();
            getAlarmManager().set(AlarmManager.ELAPSED_REALTIME, timeTillTrigger,
                    mPacRefreshIntent);
        }

        /**
         * Fetch PAC script.
         *
         * @throws IOException
         */
        private String get(String urlString) throws IOException {
            URL url = new URL(urlString);
            URLConnection urlConnection;
            if (mNetwork == null) {
                urlConnection = url.openConnection(java.net.Proxy.NO_PROXY);
            } else {
                urlConnection = mNetwork.openConnection(url, java.net.Proxy.NO_PROXY);
            }
            return new String(Streams.readFully(urlConnection.getInputStream()));
        }

        private void setCurrentProxyScriptLocked(String script) {
            if (mProxyService == null) {
                Log.e(TAG, "setCurrentProxyScript: no proxy service");
                return;
            }
            try {
                mProxyService.setPacFile(script, getNetId());
                mPac = script;
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to set PAC file", e);
            }
        }
    }

    private final Object mProxyLock = new Object();
    @GuardedBy("mProxyLock")
    private IProxyService mProxyService;
    @GuardedBy("mProxyLock")
    private ServiceConnection mConnection;
    @GuardedBy("mProxyLock")
    private ServiceConnection mProxyConnection;
    @GuardedBy("mProxyLock")
    private final SparseArray<Pac> mPacForNetId = new SparseArray<Pac>();
    @GuardedBy("mProxyLock")
    private boolean mNetworkProxyDisable = false;
    @GuardedBy("mProxyLock")
    private Pac mGlobalPac;
    @GuardedBy("mProxyLock")
    private int mDefaultNetworkNetId = NETID_UNSET;
    @GuardedBy("mProxyLock")
    private boolean mDownloadsCompletedSinceLastBroadcast = false;
    @GuardedBy("mProxyLock")
    private int mLastPort = -1;
    @GuardedBy("mProxyLock")
    private ProxyInfo mGlobalProxy;
    @GuardedBy("mProxyLock")
    private int mLastDefaultNetIdSentToProxyService = NETID_UNSET;

    public PacManager(Context context, Handler handler, int proxyMessage) {
        mContext = context;
        mConnectivityHandler = handler;
        mProxyMessage = proxyMessage;
    }

    private AlarmManager getAlarmManager() {
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        }
        return mAlarmManager;
    }

    public void setNetworkProxyDisable(boolean networkProxyDisable) {
        synchronized (mProxyLock) {
            if (networkProxyDisable == mNetworkProxyDisable) return;
            mNetworkProxyDisable = networkProxyDisable;
            updateProxyServiceNetworkProxyDisableLocked();
            updateServiceBindingsLocked();
            sendBroadcastIfNeededLocked();
        }
    }

    private void updateProxyServiceDefaultNetIdLocked(boolean forceUpdate) {
        if (mProxyService == null) return;
        final int newNetId = mGlobalProxy != null ? NETID_UNSET : mDefaultNetworkNetId;
        if (!forceUpdate && newNetId == mLastDefaultNetIdSentToProxyService) return;
        try {
            mProxyService.setDefaultNetId(newNetId);
            mLastDefaultNetIdSentToProxyService = newNetId;
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to set default NetId", e);
        }
    }


    private void updateProxyServiceNetworkProxyDisableLocked() {
        if (mProxyService == null) return;
        try {
            mProxyService.setNetworkProxyDisable(mNetworkProxyDisable);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to set network proxy disable", e);
        }
    }

    public void setGlobalProxy(ProxyInfo proxy) {
        synchronized (mProxyLock) {
            mGlobalProxy = proxy;
            final Uri newPacUrl = proxy == null ? Uri.EMPTY : proxy.getPacFileUrl();
            if (mGlobalPac != null) mGlobalPac.disposeLocked();
            if (Uri.EMPTY.equals(newPacUrl)) {
                mGlobalPac = null;
            } else {
                mGlobalPac = new Pac(newPacUrl, null);
                if (mProxyService != null) mGlobalPac.startInitialDownloadLocked();
            }
            updateProxyServiceDefaultNetIdLocked(false);
            updateServiceBindingsLocked();
            sendBroadcastIfNeededLocked();
        }
    }

    public void setNetworkProxy(ProxyInfo proxy, Network network, boolean isDefault) {
        synchronized (mProxyLock) {
            final Uri newPacUrl = proxy == null ? Uri.EMPTY : proxy.getPacFileUrl();
            if (isDefault) {
                mDefaultNetworkNetId = network.netId;
            } else {
                if (mDefaultNetworkNetId == network.netId) mDefaultNetworkNetId = NETID_UNSET;
            }
            updateProxyServiceDefaultNetIdLocked(false);
            final Pac oldPac = mPacForNetId.get(network.netId);
            if (oldPac != null) oldPac.disposeLocked();
            if (Uri.EMPTY.equals(newPacUrl)) {
                mPacForNetId.remove(network.netId);
            } else {
                final Pac newPac = new Pac(newPacUrl, network);
                mPacForNetId.put(network.netId, newPac);
                if (mProxyService != null) newPac.startInitialDownloadLocked();
            }
            updateServiceBindingsLocked();
            sendBroadcastIfNeededLocked();
        }
    }

    private Pac getDefaultPacLocked() {
        if (mGlobalProxy != null) return mGlobalPac;
        if (mNetworkProxyDisable) return null;
        return mPacForNetId.get(mDefaultNetworkNetId);
    }

    private boolean needProxyServiceLocked() {
        return mGlobalPac != null || mPacForNetId.size() > 0;
    }

    private boolean needLegacyProxyServiceLocked() {
        return getDefaultPacLocked() != null;
    }

    private boolean pendingDownloadsLocked() {
        if (mGlobalPac != null && !mGlobalPac.hasDownloadedLocked()) return true;
        for (int i = 0; i < mPacForNetId.size(); i++) {
            if (!mPacForNetId.valueAt(i).hasDownloadedLocked()) return true;
        }
        return false;
    }

    private void sendBroadcastIfNeededLocked() {
        if (needProxyServiceLocked() && mProxyService == null) return;
        if (needLegacyProxyServiceLocked() && mLastPort == -1) return;
        if (pendingDownloadsLocked() && !mDownloadsCompletedSinceLastBroadcast) return;
        sendBroadcastLocked(false);
    }

    private void sendBroadcastLocked(boolean forceBroadcast) {
        mDownloadsCompletedSinceLastBroadcast = false;
        mConnectivityHandler.sendMessage(mConnectivityHandler.obtainMessage(mProxyMessage,
                mLastPort, forceBroadcast ? 1 : 0));
    }

    private void updateServiceBindingsLocked() {
        if (needProxyServiceLocked()) {
            if (mConnection == null) bindPacServiceLocked();
        } else {
            if (mConnection != null) unbindPacServiceLocked();
        }

        if (needLegacyProxyServiceLocked()) {
            if (mProxyConnection == null) bindLegacyProxyServiceLocked();
        } else {
            if (mProxyConnection != null) unbindLegacyProxyServiceLocked();
        }
    }

    private String getPacChangeDelay() {
        final ContentResolver cr = mContext.getContentResolver();

        /** Check system properties for the default value then use secure settings value, if any. */
        String defaultDelay = SystemProperties.get(
                "conn." + Settings.Global.PAC_CHANGE_DELAY,
                DEFAULT_DELAYS);
        String val = Settings.Global.getString(cr, Settings.Global.PAC_CHANGE_DELAY);
        return (val == null) ? defaultDelay : val;
    }

    private long getDownloadDelay(int delayIndex) {
        String[] list = getPacChangeDelay().split(" ");
        if (delayIndex < list.length) {
            return Long.parseLong(list[delayIndex]);
        }
        return 0;
    }

    private void bindPacServiceLocked() {
        Intent intent = new Intent();
        intent.setClassName(PAC_PACKAGE, PAC_SERVICE);
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName component) {
                synchronized (mProxyLock) {
                    mProxyService = null;
                }
            }

            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                synchronized (mProxyLock) {
                    try {
                        Log.d(TAG, "Adding service " + PAC_SERVICE_NAME + " "
                                + binder.getInterfaceDescriptor());
                    } catch (RemoteException e1) {
                        Log.e(TAG, "Remote Exception", e1);
                    }
                    ServiceManager.addService(PAC_SERVICE_NAME, binder);
                    mProxyService = IProxyService.Stub.asInterface(binder);
                    if (mProxyService == null) {
                        Log.e(TAG, "No proxy service");
                    } else {
                        updateProxyServiceDefaultNetIdLocked(true);
                        updateProxyServiceNetworkProxyDisableLocked();
                        if (mGlobalPac != null) mGlobalPac.startInitialDownloadLocked();
                        for (int i = 0; i < mPacForNetId.size(); i++) {
                            mPacForNetId.valueAt(i).startInitialDownloadLocked();
                        }
                    }
                }
            }
        };
        mContext.bindServiceAsUser(intent, mConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND | Context.BIND_NOT_VISIBLE,
                UserHandle.CURRENT);
    }

    private void bindLegacyProxyServiceLocked() {
        Intent intent = new Intent();
        intent.setClassName(PROXY_PACKAGE, PROXY_SERVICE);
        mProxyConnection = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName component) {
            }

            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                IProxyCallback callbackService = IProxyCallback.Stub.asInterface(binder);
                if (callbackService != null) {
                    try {
                        callbackService.getProxyPort(new IProxyPortListener.Stub() {
                            @Override
                            public void setProxyPort(int port) throws RemoteException {
                                synchronized (mProxyLock) {
                                    mLastPort = port;
                                    if (port != -1) {
                                        Log.d(TAG, "Local proxy is bound on " + port);
                                        sendBroadcastIfNeededLocked();
                                    } else {
                                        Log.e(TAG, "Received invalid port from Local Proxy,"
                                                + " PAC will not be operational");
                                    }
                                }
                            }
                        });
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        mContext.bindServiceAsUser(intent, mProxyConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND | Context.BIND_NOT_VISIBLE,
                UserHandle.CURRENT);
    }

    private void unbindPacServiceLocked() {
        if (mConnection != null) {
            mContext.unbindService(mConnection);
            mConnection = null;
        }
        mProxyService = null;
    }

    private void unbindLegacyProxyServiceLocked() {
        if (mProxyConnection != null) {
            mContext.unbindService(mProxyConnection);
            mProxyConnection = null;
        }
        mLastPort = -1;
    }
}
