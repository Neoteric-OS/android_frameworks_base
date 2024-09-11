package android.security;

import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;

@SystemService(Context.INTEGRITY_PROVIDER_SERVICE)
public class IntegrityProviderManager {
    @NonNull
    private final IIntegrityProviderService mService;

    public String provideIntegrity() {
        return mService.provideIntegrity();
    }
}