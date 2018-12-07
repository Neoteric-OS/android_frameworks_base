package com.android.server.networkstack;

import android.annotation.NonNull;
import android.app.Service;
import android.content.Intent;
import android.net.INetworkStackConnector;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

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
        // TODO: makeDhcpServer(), etc. will go here.

        @Override
        public int getInterfaceVersion() {
            return INetworkStackConnector.VERSION;
        }
    }
}
