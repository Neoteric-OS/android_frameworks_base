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

import android.security.keystore2.TestConfigs;
import android.security.keystore2.keygen.KeystoreKeyGenerator;

/**
 * Measurable for generating key pairs.
 */
class KeystoreKeyPairGenMeasurable extends KeystoreMeasurable {

    KeystoreKeyPairGenMeasurable(KeystoreKeyGenerator keyGenerator)
            throws Exception {
        super(keyGenerator, "keygen");
    }

    @Override
    public void measure() throws Exception {
        generateKeyPair();
    }

    @Override
    public void tearDown(String alias) throws Exception {
        if (TestConfigs.getDeleteKeys()) {
            deleteKey(alias);
        }
    }
}
