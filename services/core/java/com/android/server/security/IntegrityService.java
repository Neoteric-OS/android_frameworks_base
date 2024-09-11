package android.server.security;

import android.os.IBinder;
import android.security.IIntegrityService;
import com.android.server.SystemService;

public class IntegrityService extends SystemService {
    private final IBinder mService = new IIntegrityService.Stub(){
        @Override
        String generateIntegrityCertificate(){
                return "generateIntegrityCertificate";
        }
    }

    @Override
    public void onStart() {

    }
}
