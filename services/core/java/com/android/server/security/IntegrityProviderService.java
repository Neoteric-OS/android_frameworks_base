package com.android.server.security;

import android.os.IBinder;
import android.security.IIntegrityProviderService;
import com.android.server.SystemService;
public class IntegrityProviderService extends SystemService {
    private final IBinder mService = new IIntegrityProviderService.Stub()
    {
        @Override
        String provideIntegrity(){
            return "provideIntegrity";
        }
    }

    @Override
    public void onStart() {

    }
}
