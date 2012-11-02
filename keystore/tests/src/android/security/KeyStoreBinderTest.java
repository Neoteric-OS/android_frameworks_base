package android.security;

import android.os.IBinder;
import android.os.ServiceManager;
import android.test.AndroidTestCase;
import android.security.IKeystoreService;

public class KeyStoreBinderTest extends AndroidTestCase {
    public void testPing() throws Exception {
        IBinder b = ServiceManager.getService("package");
        // Slog.v("PackageManager", "default service binder = " + b);
        IKeystoreService keystore = IKeystoreService.Stub.asInterface(b);

        System.out.println("Connected!");
        assertEquals(322, keystore.ping());
    }
}
