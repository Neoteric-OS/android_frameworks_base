/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.security.Provider;

/**
 * A provider focused on providing JCA interfaces for the Android KeyStore.
 *
 * @hide
 */
public class AndroidKeyStoreProvider extends Provider {
    public static final String PROVIDER_NAME = "AndroidKeyStore";

    public AndroidKeyStoreProvider() {
        super(PROVIDER_NAME, 1.0, "Android KeyStore security provider");

        // javax.crypto.KeyGenerator
        put("KeyGenerator.AES", KeyStoreKeyGeneratorSpi.AES.class.getName());
        put("KeyGenerator.HmacSHA256", KeyStoreKeyGeneratorSpi.HmacSHA256.class.getName());

        // java.security.SecretKeyFactory
        put("SecretKeyFactory.AES", KeyStoreSecretKeyFactorySpi.class.getName());
        put("SecretKeyFactory.HmacSHA256", KeyStoreSecretKeyFactorySpi.class.getName());

        // javax.crypto.Mac
//        put("Mac.HmacSHA256", KeyStoreMacSpi.HmacSHA256.class.getName());
//        put("Mac.HmacSHA256 SupportedKeyClasses", DelegatedSecretKey.class.getName());
        put("Mac.HmacSHA256", KeyStoreHmacSpi.HmacSHA256.class.getName());
        put("Mac.HmacSHA256 SupportedKeyClasses", KeyStoreSecretKey.class.getName());

        // javax.crypto.Cipher
        put("Cipher.AES/ECB/NoPadding", KeyStoreDelegatedCipherSpi.AES.ECB.NoPadding.class.getName());
        put("Cipher.AES/ECB/NoPadding SupportedKeyClasses", DelegatedSecretKey.class.getName());
        put("Cipher.AES/ECB/PKCS7Padding", KeyStoreDelegatedCipherSpi.AES.ECB.PKCS7Padding.class.getName());
        put("Cipher.AES/ECB/PKCS7Padding SupportedKeyClasses", DelegatedSecretKey.class.getName());

        put("Cipher.AES/CBC/NoPadding", KeyStoreDelegatedCipherSpi.AES.CBC.NoPadding.class.getName());
        put("Cipher.AES/CBC/NoPadding SupportedKeyClasses", DelegatedSecretKey.class.getName());
        put("Cipher.AES/CBC/PKCS7Padding", KeyStoreDelegatedCipherSpi.AES.CBC.PKCS7Padding.class.getName());
        put("Cipher.AES/CBC/PKCS7Padding SupportedKeyClasses", DelegatedSecretKey.class.getName());

        put("Cipher.AES/GCM/NoPadding", KeyStoreDelegatedCipherSpi.AES.GCM.NoPadding.class.getName());
        put("Cipher.AES/GCM/NoPadding SupportedKeyClasses", DelegatedSecretKey.class.getName());

        put("Cipher.AES/OCB/NoPadding", KeyStoreAuthenticatedCipherSpi.AES.OCB.NoPadding.class.getName());
        put("Cipher.AES/OCB/NoPadding SupportedKeyClasses", KeyStoreSecretKey.class.getName());

        // java.security.KeyStore
        put("KeyStore." + AndroidKeyStore.NAME, AndroidKeyStore.class.getName());

        // java.security.KeyPairGenerator
        put("KeyPairGenerator.EC", AndroidKeyPairGenerator.EC.class.getName());
        put("KeyPairGenerator.RSA", AndroidKeyPairGenerator.RSA.class.getName());
        put("KeyPairGenerator.ECnew", KeyStoreKeyPairGeneratorSpi.EC.class.getName());
        put("KeyPairGenerator.RSAnew", KeyStoreKeyPairGeneratorSpi.RSA.class.getName());
    }
}
