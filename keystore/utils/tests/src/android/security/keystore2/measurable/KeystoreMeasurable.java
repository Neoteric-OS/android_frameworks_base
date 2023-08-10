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

package android.security.keystore2.measurable;

import android.keystore.cts.util.TestUtils;
import android.os.Build;
import android.security.keystore2.TestConfigs;
import android.security.keystore2.keygen.KeystoreKeyGenerator;

import java.security.KeyPair;

import javax.crypto.SecretKey;

public abstract class KeystoreMeasurable extends Measurable {
    private final String mName;
    private final byte[] mMessage;
    private final KeystoreKeyGenerator mGenerator;

    KeystoreMeasurable(
            KeystoreKeyGenerator generator, String operation, int messageSize)
            throws Exception {
        super();
        mGenerator = generator;
        if (messageSize < 0) {
            mName = (operation
                    + "/" + getAlgorithm()
                    + "/" + mGenerator.getKeySize());
            mMessage = null;
        } else {
            mName = (operation
                    + "/" + getAlgorithm()
                    + "/" + mGenerator.getKeySize()
                    + "/" + messageSize);
            mMessage = TestUtils.generateRandomMessage(messageSize);
        }
    }

    KeystoreMeasurable(KeystoreKeyGenerator generator, String operation)
            throws Exception {
        this(generator, operation, -1);
    }

    @Override
    public KeystoreKeyGenerator getGenerator() {
        return mGenerator;
    }

    @Override
    public String getEnvironment() {
        return mGenerator.getProvider() + "/" + Build.CPU_ABI;
    }

    @Override
    public String getName() {
        return mName;
    }

    byte[] getMessage() {
        return mMessage;
    }

    String getAlgorithm() {
        return mGenerator.getAlgorithm();
    }

    @Override
    public void tearDown(String alias) throws Exception {
        deleteKey(alias);
    }

    public void deleteKey(String alias) throws Exception {
        if (TestConfigs.getDeleteKeys()) {
            mGenerator.deleteKey(alias);
        }
    }

    SecretKey generateSecretKey() throws Exception {
        return mGenerator.getSecretKeyGenerator().generateKey();
    }

    KeyPair generateKeyPair() throws Exception {
        return mGenerator.getKeyPairGenerator().generateKeyPair();
    }
}
