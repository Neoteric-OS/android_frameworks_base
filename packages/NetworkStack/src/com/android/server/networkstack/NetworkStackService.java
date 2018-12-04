package com.android.server.networkstack;

import android.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.net.INetworkStackConnector;
import android.net.dhcp.DhcpServer;
import android.net.dhcp.DhcpServingParams;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpServer;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.util.ILogDumpCallback;
import android.net.util.SharedLog;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.Process;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class NetworkStackService extends Service {
    private static final String TAG = NetworkStackService.class.getSimpleName();

    public static IBinder getConnector() {
        return new NetworkStackConnector();
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return getConnector();
    }

    private static class NetworkStackConnector extends INetworkStackConnector.Stub {
        private final SharedLog mLog = new SharedLog(TAG);
        private final HandlerThread mTetheringThread;

        NetworkStackConnector() {
            mTetheringThread = new HandlerThread(TAG);
            mTetheringThread.start();
        }

        @Override
        public void makeDhcpServer(String ifName, DhcpServingParamsParcel params,
                IDhcpServerCallbacks cb) throws RemoteException {
            checkCallerIsSystemServer();
            final DhcpServer server = new DhcpServer(
                    mTetheringThread.getLooper(),
                    ifName,
                    DhcpServingParams.fromParcel(params),
                    mLog.forSubComponent(ifName + ".DHCP"));
            cb.onDhcpServerCreated(new DhcpServerImpl(server));
        }

        @Override
        public void requestTetheringLogDump(ILogDumpCallback cb) {
            checkCallerIsSystemServer();
            mTetheringThread.getThreadHandler().post(() -> {
                try {
                    cb.onDumpCompleted(mLog.toCharArray());
                } catch (RemoteException e) {
                    Log.e(TAG, "Error transmitting DHCP logs", e);
                }
            });
        }

        @Override
        public int getInterfaceVersion() {
            return INetworkStackConnector.VERSION;
        }
    }

    private static class DhcpServerImpl extends IDhcpServer.Stub {
        private final DhcpServer mServer;

        private DhcpServerImpl(DhcpServer server) {
            mServer = server;
        }

        @Override
        public void start() {
            checkCallerIsSystemServer();
            mServer.start();
        }

        @Override
        public void updateParams(DhcpServingParamsParcel params) {
            checkCallerIsSystemServer();
            mServer.updateParams(DhcpServingParams.fromParcel(params));
        }

        @Override
        public void stop() throws RemoteException {
            checkCallerIsSystemServer();
            mServer.stop();
        }

        @Override
        public int getInterfaceVersion() {
            return IDhcpServer.VERSION;
        }
    }

    private static void checkCallerIsSystemServer() {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Uid " + Binder.getCallingUid()
                    + " is not allowed to access this service.");
        }
    }
}
