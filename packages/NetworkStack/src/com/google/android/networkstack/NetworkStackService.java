package com.google.android.networkstack;

import static android.os.Binder.getCallingUid;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.net.INetworkStackConnector;
import android.os.IBinder;
import android.os.Process;

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
        // TODO: makeDhcpServer(), etc. will go here.

        @Override
        public int getInterfaceVersion() {
            return INetworkStackConnector.VERSION;
        }

        @Override
        protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter fout,
                @Nullable String[] args) {
            checkCaller();
            fout.println("NetworkStack logs:");
            // TODO: dump logs here
        }
    }

    private static void checkCaller() {
        if (getCallingUid() != Process.SYSTEM_UID && getCallingUid() != Process.ROOT_UID) {
            throw new SecurityException("Invalid caller: " + getCallingUid());
        }
    }
}
