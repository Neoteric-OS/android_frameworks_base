/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.security.net.config;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;

import com.android.internal.annotations.VisibleForTesting;

/** @hide */
public class RootTrustManagerFactorySpi extends TrustManagerFactorySpi {
    private ApplicationConfig mApplicationConfig;
    private TrustManagerFactory mDelegate;

    @Override
    public final void engineInit(ManagerFactoryParameters spec)
            throws InvalidAlgorithmParameterException {
        if (!(spec instanceof ApplicationConfigParameters)) {
            throw new InvalidAlgorithmParameterException("Unsupported spec: " +  spec + ". Only "
                    + ApplicationConfigParameters.class.getName() + " supported");

        }
        mApplicationConfig = ((ApplicationConfigParameters) spec).config;
    }

    @Override
    public final void engineInit(KeyStore ks) throws KeyStoreException {
        // When a TrustManager is initialized with a null KeyStore it uses its own default trust
        // configuration, otherwise it must use the provided KeyStore. We don't want to support
        // custom KeyStores in our TrustManagement code and thus delegate that to a
        // TrustManagerFactory from another provider.
        if (ks == null) {
            mDelegate = null;
            return;
        }

        mDelegate = findFallbackTrustManagerFactory();
        if (mDelegate == null) {
            throw new KeyStoreException("Failed to find delegate TrustManagerFactory");
        }
        mDelegate.init(ks);
    }

    @Override
    public final TrustManager[] engineGetTrustManagers() {
        if (mDelegate != null) {
            return mDelegate.getTrustManagers();
        }
        if (mApplicationConfig == null) {
            mApplicationConfig = ApplicationConfig.getDefaultInstance();
        }

        return new TrustManager[] { mApplicationConfig.getTrustManager() };
    }

    /**
     * Find the fallback {@link TrustManagerFactory} to use when this factory cannot be used.
     * @return The highest priority {@code TrustManagerFactory} that is not provided by the network
     * security config provider.
     */
    private final TrustManagerFactory findFallbackTrustManagerFactory() {
        Provider[] providers = null;
        try {
            providers = Security.getProviders("TrustManagerFactory.PKIX");
        } catch (InvalidParameterException e) {
            return null;
        }
        if (providers != null) {
            for (Provider provider : providers) {
                if (provider instanceof NetworkSecurityConfigProvider) {
                    continue;
                }
                try {
                    return TrustManagerFactory.getInstance("PKIX", provider);
                } catch (NoSuchAlgorithmException e) {
                    continue;
                }
            }
        }
        return null;
    }

    @VisibleForTesting
    public static final class ApplicationConfigParameters implements ManagerFactoryParameters {
        public final ApplicationConfig config;
        public ApplicationConfigParameters(ApplicationConfig config) {
            this.config = config;
        }
    }

}
