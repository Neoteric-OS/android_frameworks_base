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
import android.security.keystore2.keygen.KeystoreKeyGenerator;

import java.security.AlgorithmParameters;
import java.security.KeyPair;

import javax.crypto.Cipher;

public class KeystoreRsaDecryptMeasurable extends KeystoreMeasurable {
    private final Cipher mCipher;
    private byte[] mEncryptedMessage;
    private AlgorithmParameters mAlgorithmParams;
    private KeyPair mKey;

    KeystoreRsaDecryptMeasurable(
            KeystoreKeyGenerator keyGenerator) throws Exception {
        super(keyGenerator, "decrypt",
                TestUtils.getMaxSupportedPlaintextInputSizeBytes(keyGenerator.getAlgorithm(),
                        keyGenerator.getKeySize()));
        mCipher = Cipher.getInstance(getAlgorithm());
    }

    @Override
    public void initialSetUp(String alias) throws Exception {
        mKey = getGenerator().getKeyEntry(alias);
        mCipher.init(Cipher.ENCRYPT_MODE, mKey.getPublic());
        mEncryptedMessage = mCipher.doFinal(getMessage());
        mAlgorithmParams = mCipher.getParameters();
    }

    @Override
    public void setUp() throws Exception {
        mCipher.init(Cipher.DECRYPT_MODE, mKey.getPrivate(), mAlgorithmParams);
    }

    @Override
    public void measure() throws Exception {
        mCipher.doFinal(mEncryptedMessage);
    }
}
