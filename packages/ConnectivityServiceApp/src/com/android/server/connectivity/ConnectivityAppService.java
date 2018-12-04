package com.android.server.connectivity;

import android.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.net.IConnectivityAppConnector;
import android.net.dhcp.DhcpServer;
import android.net.dhcp.DhcpServingParams;
import android.net.dhcp.IDhcpServer;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.util.ILogDumpCallback;
import android.net.util.SharedLog;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class ConnectivityAppService extends Service {
    private static final String TAG = ConnectivityAppService.class.getSimpleName();

    public static IBinder getConnector() {
        return new ConnectivityAppConnector();
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return getConnector();
    }

    private static class ConnectivityAppConnector extends IConnectivityAppConnector.Stub {
        private final SharedLog mLog = new SharedLog(TAG);
        private final HandlerThread mTetheringThread;

        ConnectivityAppConnector() {
            mTetheringThread = new HandlerThread(TAG);
            mTetheringThread.start();
        }

        @Override
        public void makeDhcpServer(String ifName, DhcpServingParams params, IDhcpServerCallbacks cb)
                throws RemoteException {
            final DhcpServer server = new DhcpServer(mTetheringThread.getLooper(), ifName,
                    params, mLog.forSubComponent(ifName + ".DHCP"));
            cb.onDhcpServerCreated(new DhcpServerImpl(server));
        }

        @Override
        public void requestTetheringLogDump(ILogDumpCallback cb) {
            mTetheringThread.getThreadHandler().post(() -> {
                try {
                    cb.onDumpCompleted(mLog.toCharArray());
                } catch (RemoteException e) {
                    Log.e(TAG, "Error transmitting DHCP logs", e);
                }
            });
        }
    }

    private static class DhcpServerImpl extends IDhcpServer.Stub {
        private final DhcpServer mServer;

        private DhcpServerImpl(DhcpServer server) {
            mServer = server;
        }

        @Override
        public void start() throws RemoteException {
            mServer.start();
        }

        @Override
        public void updateParams(DhcpServingParams params) {
            mServer.updateParams(params);
        }

        @Override
        public void stop() throws RemoteException {
            mServer.stop();
        }
    }
}
