package com.android.server.connectivity;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.net.IConnectivityAppConnector;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;

import android.net.INetworkMonitor;
import android.net.INetworkMonitorCallback;
import android.net.PrivateDnsConfig;
import android.os.RemoteException;
import android.util.Log;

import com.android.server.connectivity.NetworkMonitor.MonitoringEndedCallback;

public class NetworkMonitorService extends Service {
    private static final String TAG = NetworkMonitorService.class.getName();

    private final MonitoringEndedCallback mCallback = () -> {
        new Handler(getMainLooper()).post(() -> {
            // Stopping with last start ID stops the service
            stopSelf();
            // TODO: does the HandlerThread need to be stopped manually ?
        });
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new NetworkMonitorConnector();
    }

    public class NetworkMonitorConnector extends IConnectivityAppConnector.Stub {
        @Override
        public void startNetworkMonitor(Network network, NetworkRequest defaultRequest,
                INetworkMonitorCallback cb) {
            final NetworkMonitor nm = new NetworkMonitor(NetworkMonitorService.this, cb,
                    network, defaultRequest, mCallback);

            try {
                cb.onNetworkMonitorCreated(new NetworkMonitorImpl(nm));
            } catch (RemoteException e) {
                Log.e(TAG, "Error calling onNetworkMonitorCreated", e);
            }
        }
    }

    private static class NetworkMonitorImpl extends INetworkMonitor.Stub {
        private final NetworkMonitor mNm;

        public NetworkMonitorImpl(NetworkMonitor nm) {
            mNm = nm;
        }

        @Override
        public void launchCaptivePortalApp() {
            mNm.launchCaptivePortalApp();
        }

        @Override
        public void forceReevaluation(int uid) {
            mNm.forceReevaluation(uid);
        }

        @Override
        public void notifyPrivateDnsChanged(PrivateDnsConfig config) {
            mNm.notifyPrivateDnsSettingsChanged(config);
        }
    }
}
