package android.security;

import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;

@SystemService(Context.INTEGRITY_SERVICE)
public class IntegrityManager {
    @NonNull
    private final IIntegrityService mService;

    public String generateIntegrityCertificate() {
        return mService.generateIntegrityCertificate()
    }
}
