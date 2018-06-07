package com.android.server.connectivity;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.ProxyInfo;

/**
 * A class to handle proxy for ConnectivityService.
 */
public class ProxyTracker {
    // TODO : make this private and import as much managing logic from ConnectivityService as
    // possible
    @NonNull
    public final Object mProxyLock = new Object();
    @Nullable
    public ProxyInfo mGlobalProxy = null;
    @Nullable
    public volatile ProxyInfo mDefaultProxy = null;
    public boolean mDefaultProxyDisabled = false;
}
