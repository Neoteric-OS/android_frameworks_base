/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.net.vcn.persistablebundleutils;

import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.SaProposal;
import android.os.PersistableBundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class IkeSessionParamsUtilsTest {
    private static IkeSessionParams.Builder createBuilderMinimum() {
        final InetAddress serverAddress = InetAddresses.parseNumericAddress("192.0.2.100");

        final IkeSaProposal saProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                SaProposal.ENCRYPTION_ALGORITHM_3DES, SaProposal.KEY_LEN_UNUSED)
                        .addIntegrityAlgorithm(SaProposal.INTEGRITY_ALGORITHM_HMAC_SHA1_96)
                        .addPseudorandomFunction(SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .addDhGroup(SaProposal.DH_GROUP_1024_BIT_MODP)
                        .build();

        return new IkeSessionParams.Builder()
                .setServerHostname(serverAddress.getHostAddress())
                .addSaProposal(saProposal)
                .setLocalIdentification(new IkeFqdnIdentification("client.test.android.net"))
                .setRemoteIdentification(new IkeFqdnIdentification("server.test.android.net"))
                .setAuthPsk("psk".getBytes());
    }

    private static void verifyPersistableBundleEncodeDecodeIsLossless(IkeSessionParams params) {
        final PersistableBundle bundle = IkeSessionParamsUtils.toPersistableBundle(params);
        final IkeSessionParams result = IkeSessionParamsUtils.fromPersistableBundle(bundle);

        assertEquals(result, params);
    }

    @Test
    public void testEncodeRecodeParamsWithLifetimes() throws Exception {
        final int hardLifetime = (int) TimeUnit.HOURS.toSeconds(20L);
        final int softLifetime = (int) TimeUnit.HOURS.toSeconds(10L);
        final IkeSessionParams params =
                createBuilderMinimum().setLifetimeSeconds(hardLifetime, softLifetime).build();
        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }

    @Test
    public void testEncodeRecodeParamsWithDpdDelay() throws Exception {
        final int dpdDelay = (int) TimeUnit.MINUTES.toSeconds(10L);
        final IkeSessionParams params = createBuilderMinimum().setDpdDelaySeconds(dpdDelay).build();

        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }

    @Test
    public void testEncodeRecodeParamsWithNattKeepalive() throws Exception {
        final int nattKeepAliveDelay = (int) TimeUnit.MINUTES.toSeconds(5L);
        final IkeSessionParams params =
                createBuilderMinimum().setNattKeepAliveDelaySeconds(nattKeepAliveDelay).build();

        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }

    @Test
    public void testEncodeRecodeParamsWithRetransmissionTimeouts() throws Exception {
        final int[] retransmissionTimeout = new int[] {500, 500, 500, 500, 500, 500};
        final IkeSessionParams params =
                createBuilderMinimum()
                        .setRetransmissionTimeoutsMillis(retransmissionTimeout)
                        .build();

        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }

    @Test
    public void testEncodeRecodeParamsWithAuthPsk() throws Exception {
        final IkeSessionParams params = createBuilderMinimum().setAuthPsk("psk".getBytes()).build();
        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }

    private static InputStream openAssetsFile(String fileName) throws Exception {
        return InstrumentationRegistry.getContext().getResources().getAssets().open(fileName);
    }

    private static X509Certificate createCertFromPemFile(String fileName) throws Exception {
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(openAssetsFile(fileName));
    }

    private static RSAPrivateKey createRsaPrivateKeyFromKeyFile(String fileName) throws Exception {
        final String newLineChar = "\n";
        final String pemTypePrivateKey = "-----(BEGIN|END) PRIVATE KEY-----";

        final String pemText =
                new BufferedReader(
                                new InputStreamReader(
                                        openAssetsFile(fileName), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining(newLineChar));

        final byte[] certificateBytes =
                Base64.getDecoder()
                        .decode(
                                pemText.replaceAll(pemTypePrivateKey, "")
                                        .replaceAll(newLineChar, "")
                                        .getBytes(StandardCharsets.UTF_8));
        return (RSAPrivateKey) CertUtils.privateKeyFromByteArray(certificateBytes);
    }

    @Test
    public void testEncodeRecodeParamsWithDigitalSignAuth() throws Exception {
        final X509Certificate serverCaCert = createCertFromPemFile("self-signed-ca.pem");
        final X509Certificate clientEndCert = createCertFromPemFile("client-end-cert.pem");
        final RSAPrivateKey clientPrivateKey =
                createRsaPrivateKeyFromKeyFile("client-private-key.key");

        final IkeSessionParams params =
                createBuilderMinimum()
                        .setAuthDigitalSignature(serverCaCert, clientEndCert, clientPrivateKey)
                        .build();
        verifyPersistableBundleEncodeDecodeIsLossless(params);
    }
}
