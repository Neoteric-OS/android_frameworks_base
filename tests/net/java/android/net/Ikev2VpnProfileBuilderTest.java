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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.security.Credentials;
import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.org.bouncycastle.x509.X509V1CertificateGenerator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

/** Unit tests for {@link Ikev2VpnProfileBuilder}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class Ikev2VpnProfileBuilderTest {
    private static final String SESSION_NAME_STRING = "testSession";
    private static final String SERVER_ADDR_STRING = "1.2.3.4";
    private static final String IDENTITY_STRING = "Identity";
    private static final String USERNAME_STRING = "username";
    private static final String PASSWORD_STRING = "pa55w0rd";
    private static final String PSK_STRING = "preSharedKey";
    private static final int TEST_MTU = 1300;

    private MockContext mMockContext =
            new MockContext() {
                @Override
                public String getOpPackageName() {
                    return "fooPackage";
                }

                @Override
                public Object getSystemService(String serviceName) {
                    return mock(VpnManager.class);
                }

                @Override
                public String getSystemServiceName(Class<?> serviceClass) {
                    return Context.VPN_MANAGEMENT_SERVICE;
                }
            };
    private X509Certificate mUserCert;
    private X509Certificate mServerRootCa;
    private PrivateKey mPrivateKey;
    private final ProxyInfo mProxy = mock(ProxyInfo.class);

    @Before
    public void setUp() throws Exception {
        mServerRootCa = buildCertAndKeyPair().cert;

        CertificateAndKey userCertKey = buildCertAndKeyPair();
        mUserCert = userCertKey.cert;
        mPrivateKey = userCertKey.key;
    }

    @Test
    public void testBuildValidProfileWithOptions() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        builder.setSessionName(SESSION_NAME_STRING);
        builder.setAuthUsernamePassword(USERNAME_STRING, PASSWORD_STRING, mServerRootCa);
        builder.setBypassable(true);
        builder.setHttpProxy(mProxy);
        builder.setMaxMtu(TEST_MTU);
        builder.setMetered(true);
        builder.build();

        // Check non-auth parameters correctly stored
        // TODO: Inspect these based on the provisioned profile once that is possible.
        assertEquals(SESSION_NAME_STRING, builder.mProfile.name);
        assertEquals(SERVER_ADDR_STRING, builder.mProfile.server);
        assertEquals(IDENTITY_STRING, builder.mProfile.ipsecIdentifier);
        assertEquals(mProxy, builder.mProfile.proxy);
        assertTrue(builder.mProfile.isBypassable);
        assertTrue(builder.mProfile.isMetered);
        assertEquals(TEST_MTU, builder.mProfile.maxMtu);
        assertTrue(builder.mProfile.authParamsInline);
    }

    @Test
    public void testBuildUsernamePasswordProfile() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        builder.setAuthUsernamePassword(USERNAME_STRING, PASSWORD_STRING, mServerRootCa);
        builder.build();

        assertEquals(USERNAME_STRING, builder.mProfile.username);
        assertEquals(PASSWORD_STRING, builder.mProfile.password);
        assertEquals(
                new String(Credentials.convertToPem(mServerRootCa), StandardCharsets.US_ASCII),
                builder.mProfile.ipsecCaCert);
    }

    @Test
    public void testBuildDigitalSignatureProfile() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        builder.setAuthDigitalSignature(mUserCert, mPrivateKey, mServerRootCa);
        builder.build();

        assertEquals(
                new String(Credentials.convertToPem(mUserCert), StandardCharsets.US_ASCII),
                builder.mProfile.ipsecUserCert);
        assertEquals(
                new String(Credentials.convertToPem(mServerRootCa), StandardCharsets.US_ASCII),
                builder.mProfile.ipsecCaCert);
        assertEquals(
                Base64.getEncoder().encodeToString(mPrivateKey.getEncoded()),
                builder.mProfile.ipsecSecret);
    }

    @Test
    public void testBuildPresharedKeyProfile() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        builder.setAuthPsk(PSK_STRING);
        builder.build();

        assertEquals(PSK_STRING, builder.mProfile.ipsecSecret);
    }

    @Test
    public void testBuildNoAuthMethodSet() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        try {
            builder.build();
            fail("Expected exception due to lack of auth method");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBuildInvalidMtu() throws Exception {
        Ikev2VpnProfileBuilder builder =
                new Ikev2VpnProfileBuilder(mMockContext, SERVER_ADDR_STRING, IDENTITY_STRING);

        try {
            builder.setMaxMtu(500);
            fail("Expected exception due to too-small MTU");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static class CertificateAndKey {
        public final X509Certificate cert;
        public final PrivateKey key;

        CertificateAndKey(X509Certificate cert, PrivateKey key) {
            this.cert = cert;
            this.key = key;
        }
    }

    private static CertificateAndKey buildCertAndKeyPair() throws Exception {
        Date validityBeginDate = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1L));
        Date validityEndDate = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1L));

        // Generate a keypair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Principal dnName = new X500Principal("CN=test.android.com");
        X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(dnName);
        certGen.setIssuerDN(dnName);
        certGen.setNotBefore(validityBeginDate);
        certGen.setNotAfter(validityEndDate);
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

        X509Certificate cert = certGen.generate(keyPair.getPrivate(), "AndroidOpenSSL");
        return new CertificateAndKey(cert, keyPair.getPrivate());
    }
}
