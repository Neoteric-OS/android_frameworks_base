/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.keystore2.keygen;

import android.keystore.cts.util.TestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;

/**
 * Wrapper for generating Keystore keys.
 */
public abstract class KeystoreKeyGenerator {
    protected static final String TAG = KeystoreKeyGenerator.class.getSimpleName();
    public static final String ALIAS_PREFIX = "keystoreStressTestKey_";
    private final String mAlgorithm;
    private final String mProvider = TestUtils.EXPECTED_PROVIDER_NAME;
    private KeyGenerator mSecretKeyGenerator = null;
    private KeyPairGenerator mKeyPairGenerator = null;
    private final KeyStore mKeyStore;
    private int mKeySize;
    private int mPurpose;

    public KeystoreKeyGenerator(String algorithm, int keySize, int purpose) throws Exception {
        mKeyStore = KeyStore.getInstance(getProvider());
        mKeyStore.load(null);
        mAlgorithm = algorithm;
        mKeySize = keySize;
        mPurpose = purpose;
    }

    public String getAlgorithm() {
        return mAlgorithm;
    }

    public String getProvider() {
        return mProvider;
    }

    public void deleteKey(String alias) throws Exception {
        mKeyStore.deleteEntry(alias);
    }

    public KeyGenerator getSecretKeyGenerator() throws Exception {
        if (mSecretKeyGenerator == null) {
            mSecretKeyGenerator =
                    KeyGenerator.getInstance(TestUtils.getKeyAlgorithm(mAlgorithm), mProvider);
        }
        return mSecretKeyGenerator;
    }

    public KeyPairGenerator getKeyPairGenerator() throws Exception {
        if (mKeyPairGenerator == null) {
            mKeyPairGenerator =
                    KeyPairGenerator.getInstance(
                            TestUtils.getKeyAlgorithm(mAlgorithm), mProvider);
        }
        return mKeyPairGenerator;
    }

    public void initialize(String alias) throws Exception {
    }

    public int getKeySize() {
        return mKeySize;
    }

    public int getPurpose() {
        return mPurpose;
    }

    public KeyPair getKeyEntry(String alias) throws Exception {
        KeyStore.PrivateKeyEntry privateKeyEntry =
                (KeyStore.PrivateKeyEntry) mKeyStore.getEntry(alias, null);
        return new KeyPair(privateKeyEntry.getCertificate().getPublicKey(),
                privateKeyEntry.getPrivateKey());
    }
}
