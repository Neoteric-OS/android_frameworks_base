/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security;

import java.io.File;
import java.security.Provider;

import android.os.Environment;
import android.os.UserHandle;

import com.android.org.conscrypt.KeyManagerFactoryImpl;
import com.android.org.conscrypt.TrustManagerFactoryImpl;
import com.android.org.conscrypt.TrustedCertificateKeyStoreSpi;
import com.android.org.conscrypt.TrustedCertificateStore;

/**
 * A provider focused on providing secure socket interface implementations.
 *
 * @hide
 */
public final class AndroidJSSEProvider extends Provider {
    public static final String PROVIDER_NAME = "AndroidJSSEProvider";

    static {
        // Make sure TrustedCertificateStore instances look in the right place for user-added CAs
        final File configDir = Environment.getUserConfigDirectory(UserHandle.myUserId());
        TrustedCertificateStore.setDefaultUserDirectory(configDir);
    }

    public AndroidJSSEProvider() {
        super(PROVIDER_NAME, 1.0, "Android JSSE security provider");

        put("KeyManagerFactory.PKIX", KeyManagerFactoryImpl.class.getName());
        put("Alg.Alias.KeyManagerFactory.X509", "PKIX");

        put("TrustManagerFactory.PKIX", TrustManagerFactoryImpl.class.getName());
        put("Alg.Alias.TrustManagerFactory.X509", "PKIX");

        put("KeyStore.AndroidCAStore", TrustedCertificateKeyStoreSpi.class.getName());
    }
}
