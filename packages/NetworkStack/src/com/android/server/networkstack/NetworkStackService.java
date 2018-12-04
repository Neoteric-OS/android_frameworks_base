package com.android.server.networkstack;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.net.INetworkStackConnector;
import android.net.dhcp.DhcpServer;
import android.net.dhcp.DhcpServingParams;
import android.net.dhcp.DhcpServingParamsParcel;
import android.net.dhcp.IDhcpServer;
import android.net.dhcp.IDhcpServerCallbacks;
import android.net.util.SharedLog;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Android service used to start the network stack when bound to via an intent.
 *
 * <p>The service returns a binder for the system server to communicate with the network stack.
 */
public class NetworkStackService extends Service {
    private static final String TAG = NetworkStackService.class.getSimpleName();

    /**
     * Create a binder connector for the system server to communicate with the network stack.
     *
     * <p>On platforms where the network stack runs in the system server process, this method may
     * be called directly instead of obtaining the connector by binding to the service.
     */
    public static IBinder makeConnector() {
        return new NetworkStackConnector();
    }

    @NonNull
    @Override
    public IBinder onBind(Intent intent) {
        return makeConnector();
    }

    private static class NetworkStackConnector extends INetworkStackConnector.Stub {
        @NonNull
        private final SharedLog mLog = new SharedLog(TAG);

        @Override
        public void makeDhcpServer(@NonNull String ifName, @NonNull DhcpServingParamsParcel params,
                @NonNull IDhcpServerCallbacks cb) throws RemoteException {
            checkCallerIsSystemServer();
            final HandlerThread tetheringThread = new HandlerThread(
                    DhcpServer.class.getSimpleName() + "." + ifName);
            tetheringThread.start();
            final DhcpServer server = new DhcpServer(
                    tetheringThread.getLooper(),
                    ifName,
                    DhcpServingParams.fromParcel(params),
                    mLog.forSubComponent(ifName + ".DHCP"));
            cb.onDhcpServerCreated(new DhcpServerImpl(server));
        }

        @Override
        public int getInterfaceVersion() {
            return INetworkStackConnector.VERSION;
        }

        @Override
        protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
                @Nullable String[] args) {
            mLog.dump(fd, fout, args);
        }
    }

    private static class DhcpServerImpl extends IDhcpServer.Stub {
        private final DhcpServer mServer;

        private DhcpServerImpl(@NonNull DhcpServer server) {
            mServer = server;
        }

        @Override
        public void start() {
            checkCallerIsSystemServer();
            mServer.start();
        }

        @Override
        public void updateParams(@NonNull DhcpServingParamsParcel params) {
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
