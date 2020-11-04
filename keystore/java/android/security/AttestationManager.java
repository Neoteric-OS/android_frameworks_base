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
import android.hardware.keymint.KeyParameter;
import android.hardware.keymint.Tag;
import android.os.Build;
import android.os.RemoteException;
import android.telephony.TelephonyManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provides methods for securely attesting the properties of the device.
 *
 * <p>Use {@link Context#getSystemService(String))} with {@link Context#ATTESTATION_SERVICE} to
 * create an {@link AttestationManager}.
 */
@SystemService(Context.ATTESTATION_SERVICE)
public final class AttestationManager {
    private static final String TAG = "AttestationManager";
    private static final int MIN_ATTESTATION_CHALLENGE_LENGTH = 8;
    private static final int MAX_ATTESTATION_CHALLENGE_LENGTH = 64;

    final Context mContext;
    final IAttestationManager mService;

    /**
     * Constructor for use by {@code SystemServiceRegistry} only.
     *
     * <p>{@hide}
     */
    public AttestationManager(Context context, IAttestationManager service) {
        mContext = context;
        mService = service;
    }

    /**
     * Attest to a list of device identifiers.
     *
     * <p>If the device supports attestation in secure hardware, the chain will be rooted at a
     * trustworthy CA key. Otherwise, the chain will be rooted at an untrusted certificate. See <a
     * href="https://developer.android.com/training/articles/security-key-attestation.html">Key
     * Attestation</a> for the format of the certificate extension.
     *
     * <p>Attestation will only be successful when all of the following are true:
     *
     * <ol>
     *   <li>The device has been set up to support device identifier attestation at the factory.
     *   <li>You have permission to access the device identifiers you are requesting attestation
     *       for.
     * </ul>
     *
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
     *     requested identifiers.
     * @throws AttestationException when attestation fails.
     */
    public @NonNull List<X509Certificate> attestDevice(@NonNull DeviceAttestationRequest dar)
            throws AttestationException {
        if (dar.getAttestationChallenge().length < MIN_ATTESTATION_CHALLENGE_LENGTH
                || dar.getAttestationChallenge().length > MAX_ATTESTATION_CHALLENGE_LENGTH) {
            throw new AttestationException(
                    "AttestationChallenge must be in range ["
                            + MIN_ATTESTATION_CHALLENGE_LENGTH
                            + ", "
                            + MAX_ATTESTATION_CHALLENGE_LENGTH
                            + "].");
        }

        KeyParameter[] keyParameters = createKeyParameters(dar.getIdentifiers());

        ByteArray[] certificates;
        try {
            certificates =
                    mService.attestDevice(
                            keyParameters,
                            dar.isIndividualAttestation(),
                            dar.getAttestationChallenge(),
                            dar.getSecurityLevel());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        List<X509Certificate> result = new ArrayList<X509Certificate>();
        CertificateFactory cf;
        try {
            cf = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e) {
            throw new AttestationException(
                    "Unable to create a certificate factory for X.509 certificates", e);
        }
        for (ByteArray certificateBytes : certificates) {
            try {
                result.add(
                        (X509Certificate)
                                cf.generateCertificate(
                                        new ByteArrayInputStream(certificateBytes.data)));
            } catch (CertificateException e) {
                throw new AttestationException(
                        "Could not parse one of the returned certificates in the chain", e);
            }
        }
        return result;
    }

    /**
     * Populate key parameters before passing them on to the {@IAttestationManager}, by collecting
     * data from across the OS.
     *
     * @param deviceIdentifiers a list of {@code DeviceAttestationRequest#DEVICE_IDENTIFIER_*}
     *     constants.
     */
    @NonNull
    private KeyParameter[] createKeyParameters(@NonNull Set<Integer> deviceIdentifiers)
            throws AttestationException {
        final List<KeyParameter> result = new ArrayList();

        TelephonyManager telephonyManager = null;
        if (deviceIdentifiers.contains(DeviceAttestationRequest.DEVICE_IDENTIFIER_IMEI)
                || deviceIdentifiers.contains(DeviceAttestationRequest.DEVICE_IDENTIFIER_MEID)) {
            telephonyManager = mContext.getSystemService(TelephonyManager.class);
            if (telephonyManager == null) {
                throw new AttestationException("Unable to access telephony service");
            }
        }

        for (final int deviceIdentifier : deviceIdentifiers) {
            switch (deviceIdentifier) {
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_BRAND:
                    result.add(createKeyParameter(Tag.ATTESTATION_ID_BRAND, Build.BRAND));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_DEVICE:
                    result.add(createKeyParameter(Tag.ATTESTATION_ID_DEVICE, Build.DEVICE));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_PRODUCT:
                    result.add(createKeyParameter(Tag.ATTESTATION_ID_PRODUCT, Build.PRODUCT));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_MANUFACTURER:
                    result.add(
                            createKeyParameter(
                                    Tag.ATTESTATION_ID_MANUFACTURER, Build.MANUFACTURER));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_MODEL:
                    result.add(createKeyParameter(Tag.ATTESTATION_ID_MODEL, Build.MODEL));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_IMEI:
                    result.add(
                            createKeyParameter(
                                    Tag.ATTESTATION_ID_IMEI, telephonyManager.getImei()));
                    break;
                case DeviceAttestationRequest.DEVICE_IDENTIFIER_MEID:
                    result.add(
                            createKeyParameter(
                                    Tag.ATTESTATION_ID_MEID, telephonyManager.getMeid()));
                    break;
                default:
                    throw new AttestationException(
                            "Unknown device identifier: " + deviceIdentifier);
            }
        }

        return result.toArray(new KeyParameter[result.size()]);
    }

    @NonNull
    private KeyParameter createKeyParameter(int tag, String string) {
        KeyParameter result = new KeyParameter();
        result.tag = tag;
        result.blob = string.getBytes(StandardCharsets.UTF_8);
        return result;
    }
}
