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

import android.keystore.cts.util.EmptyArray;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;

public class AndroidKeystoreRsaKeyGenerator extends KeystoreKeyGenerator {
    public AndroidKeystoreRsaKeyGenerator(String algorithm, int keySize, int purpose)
            throws Exception {
        super(algorithm, keySize, purpose);
    }

    @Override
    public void initialize(String alias) throws Exception {
        String digest = TestUtils.getCipherDigest(getAlgorithm());
        getKeyPairGenerator().initialize(new KeyGenParameterSpec.Builder(alias, getPurpose())
                .setKeySize(getKeySize())
                .setBlockModes(TestUtils.getCipherBlockMode(getAlgorithm()))
                .setEncryptionPaddings(TestUtils.getCipherEncryptionPadding(getAlgorithm()))
                .setRandomizedEncryptionRequired(false)
                .setDigests((digest != null) ? new String[]{digest} : EmptyArray.STRING)
                .build());
    }
}
