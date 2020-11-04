/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods for securely attesting the properties of the device.
 *
 * <p>Use {@link Context#getSystemService(String))} with {@link Context#ATTESTATION_SERVICE} to
 * create an {@link AttestationManager}.
 */
@SystemService(Context.ATTESTATION_SERVICE)
public final class AttestationManager {
    private static final String TAG = "AttestationManager";

    final IAttestationManager mService;

    /** {@hide} */
    public AttestationManager(IAttestationManager service) {
        mService = service;
    }

    /**
     * Attest to a list of device identifiers.
     *
     * <p>If the device supports attestation in secure hardware, the chain will be rooted at a
     * trustworthy CA key. Otherwise, the chain will be rooted at an untrusted certificate. See
     * <a href="https://developer.android.com/training/articles/security-key-attestation.html">
     * Key Attestation</a> for the format of the certificate extension.
     *
     * <p>Attestation will only be successful when all of the following are true:
     * <ol>
     *     <li>The device has been set up to support device identifier attestation at the factory.
     *     <li>The user has not permanently disabled device identifier attestation.
     *     <li>You have permission to access the device identifiers you are requesting attestation
     *     for.
     * </ul>
     * For privacy reasons, you cannot distinguish between (1) and (2). If attestation is
     * unsuccessful, the device may not support it in general or the user may have permanently
     * disabled it.
     *
     * <p>Usage example:
     *
     * <pre class="prettyprint">
     * DeviceAttestationRequest dar =
     *      new DeviceAttestationRequest.Builder(attestationChallenge)
     *          .addIdentifier(DeviceAttestationRequest.DEVICE_IDENTIFIER_BRAND)
     *          .addIdentifier(DeviceAttestationRequest.DEVICE_IDENTIFIER_DEVICE)
     *          .setIndividualAttestation(true) // default = false
     *          .build();
     * List<X509Certificate> certChain = attestationManager.attestDevice(dar);
     * </pre>
     *
     * @param dar request containing the attestation challenge and the identifiers to be attested.
     * @return the full certificate chain, with the attestation included as an extension in the
     *     first element.
     * @throws SecurityException if the caller does not hold the correct permissions for the
     *     requested identifiers
     */
    public @NonNull List<X509Certificate> attestDevice(@NonNull DeviceAttestationRequest dar) {
        ByteArray[] certificates;
        try {
            certificates =
                    mService.attestDevice(
                            dar.getIdentifiers(),
                            dar.isIndividualAttestation(),
                            dar.getAttestationChallenge());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        List<X509Certificate> result = new ArrayList<X509Certificate>();
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            Log.e(TAG, "Not able to create a certificate factory for X.509 certificates", e);
            return result;
        }
        for (ByteArray certificateBytes : certificates) {
            try {
                result.add(
                        (X509Certificate)
                                cf.generateCertificate(
                                        new ByteArrayInputStream(certificateBytes.data)));
            } catch (CertificateException e) {
                Log.e(TAG, "Could not parse one of the returned certificates in the chain", e);
            }
        }
        return result;
    }
}
