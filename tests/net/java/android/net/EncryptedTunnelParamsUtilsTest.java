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

package android.net;

import static android.net.ipsec.ike.SaProposal.DH_GROUP_2048_BIT_MODP;
import static android.net.ipsec.ike.SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12;
import static android.net.ipsec.ike.SaProposal.PSEUDORANDOM_FUNCTION_AES128_XCBC;

import static org.junit.Assert.assertEquals;

import android.net.ipsec.ike.ChildSaProposal;
import android.net.ipsec.ike.IkeFqdnIdentification;
import android.net.ipsec.ike.IkeSaProposal;
import android.net.ipsec.ike.IkeSessionParams;
import android.net.ipsec.ike.IkeTunnelParams;
import android.net.ipsec.ike.SaProposal;
import android.net.ipsec.ike.TunnelModeChildSessionParams;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EncryptedTunnelParamsUtilsTest {
    private static final IkeSessionParams IKE_PARAMS;
    private static final TunnelModeChildSessionParams CHILD_PARAMS;

    static {
        final IkeSaProposal ikeProposal =
                new IkeSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .addDhGroup(DH_GROUP_2048_BIT_MODP)
                        .addPseudorandomFunction(PSEUDORANDOM_FUNCTION_AES128_XCBC)
                        .build();
        IKE_PARAMS =
                new IkeSessionParams.Builder()
                        .setServerHostname("192.0.2.100")
                        .addSaProposal(ikeProposal)
                        .setLocalIdentification(new IkeFqdnIdentification("test.client.com"))
                        .setRemoteIdentification(new IkeFqdnIdentification("test.server.com"))
                        .setAuthPsk("psk".getBytes())
                        .build();

        final ChildSaProposal childProposal =
                new ChildSaProposal.Builder()
                        .addEncryptionAlgorithm(
                                ENCRYPTION_ALGORITHM_AES_GCM_12, SaProposal.KEY_LEN_AES_128)
                        .build();
        CHILD_PARAMS =
                new TunnelModeChildSessionParams.Builder().addSaProposal(childProposal).build();
    }

    private static IkeTunnelParams buildTestParams() {
        return new IkeTunnelParams(IKE_PARAMS, CHILD_PARAMS);
    }

    @Test
    public void testIkeTunnelParamsPersistableBundle() {
        final IkeTunnelParams params = buildTestParams();

        assertEquals(
                params,
                EncryptedTunnelParamsUtils.fromPersistableBundle(
                        EncryptedTunnelParamsUtils.toPersistableBundle(params)));
    }
}
