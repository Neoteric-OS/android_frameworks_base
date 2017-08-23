/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.net;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.support.test.filters.SmallTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link android.net.IpSecTransform}. */
@SmallTest
@RunWith(JUnit4.class)
public class IpSecTransformTest {
    private static final byte[] CRYPT_KEY = {
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
        0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
        0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
    };
    private static final byte[] AUTH_KEY = {
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F,
        0x7A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7F
    };

    Context mMockContext;

    @Before
    public void setUp() throws Exception {
        mMockContext = mock(Context.class);
    }

    @Test
    public void testBuildInvalidAlgosAuthCryptAfterCrypt() throws Exception {
        IpSecAlgorithm encryptionAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        try {
            new IpSecTransform.Builder(mMockContext)
                    .setEncryption(IpSecTransform.DIRECTION_OUT, encryptionAlgo)
                    .setAuthenticatedEncryption(
                            IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo);

            fail("Setting Authenticated Encryption after Encryption should fail");
        } catch (IllegalArgumentException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosCryptAfterAuthCrypt() throws Exception {
        IpSecAlgorithm encryptionAlgo = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, CRYPT_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        try {
            new IpSecTransform.Builder(mMockContext)
                    .setAuthenticatedEncryption(
                            IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                    .setEncryption(IpSecTransform.DIRECTION_OUT, encryptionAlgo);

            fail("Setting Encryption after Authenticated Encryption should fail");
        } catch (IllegalArgumentException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosAuthCryptAfterAuth() throws Exception {
        IpSecAlgorithm authenticationAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        try {
            new IpSecTransform.Builder(mMockContext)
                    .setAuthentication(IpSecTransform.DIRECTION_OUT, authenticationAlgo)
                    .setAuthenticatedEncryption(
                            IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo);

            fail("Setting Authenticated Encryption after Authentication should fail");
        } catch (IllegalArgumentException e) {
            // Test passes
        }
    }

    @Test
    public void testBuildInvalidAlgosAuthAfterAuthCrypt() throws Exception {
        IpSecAlgorithm authenticationAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, AUTH_KEY);
        IpSecAlgorithm authenticatedEncryptionAlgo =
                new IpSecAlgorithm(IpSecAlgorithm.AUTH_CRYPT_AES_GCM, AUTH_KEY);

        try {
            new IpSecTransform.Builder(mMockContext)
                    .setAuthenticatedEncryption(
                            IpSecTransform.DIRECTION_OUT, authenticatedEncryptionAlgo)
                    .setAuthentication(IpSecTransform.DIRECTION_OUT, authenticationAlgo);

            fail("Setting Authentication after Authenticated Encryption should fail");
        } catch (IllegalArgumentException e) {
            // Test passes
        }
    }
}
