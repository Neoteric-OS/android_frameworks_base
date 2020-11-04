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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Build;
import android.security.keystore.KeyProperties;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

/** Request class for use with {@link AttestationManager}. */
public final class DeviceAttestationRequest {

    /** Specifies that the device should attest to its {@link Build#BRAND}. */
    public static final int DEVICE_IDENTIFIER_BRAND = 0;

    /** Specifies that the device should attest to its {@link Build#DEVICE}. */
    public static final int DEVICE_IDENTIFIER_DEVICE = 1;

    /** Specifies that the device should attest to its {@link Build#PRODUCT}. */
    public static final int DEVICE_IDENTIFIER_PRODUCT = 2;

    /** Specifies that the device should attest to its {@link Build#MANUFACTURER}. */
    public static final int DEVICE_IDENTIFIER_MANUFACTURER = 3;

    /** Specifies that the device should attest to its {@link Build#MODEL}. */
    public static final int DEVICE_IDENTIFIER_MODEL = 4;

    /**
     * Specifies that the device should attest to the IMEIs for all radios, as provided by {@link
     * android.telephony.TelephonyManager#getImei()}.
     */
    public static final int DEVICE_IDENTIFIER_IMEI = 5;

    /**
     * Specifies that the device should attest to the MEIDs for all radios, as provided by {@link
     * android.telephony.TelephonyManager#getMeid()}.
     */
    public static final int DEVICE_IDENTIFIER_MEID = 6;

    /** Specifies that the device should attest to its {@link Build#getSerial()}. */
    public static final int DEVICE_IDENTIFIER_SERIAL = 7;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"DEVICE_IDENTIFIER"},
            value = {
                DEVICE_IDENTIFIER_BRAND,
                DEVICE_IDENTIFIER_DEVICE,
                DEVICE_IDENTIFIER_PRODUCT,
                DEVICE_IDENTIFIER_MANUFACTURER,
                DEVICE_IDENTIFIER_MODEL,
                DEVICE_IDENTIFIER_IMEI,
                DEVICE_IDENTIFIER_MEID,
                DEVICE_IDENTIFIER_SERIAL,
            })
    public @interface DeviceIdentifier {}

    byte[] mAttestationChallenge;
    Set<Integer> mIdentifiers;
    boolean mUseIndividualAttestation;
    int mSecurityLevel;

    public @NonNull byte[] getAttestationChallenge() {
        return mAttestationChallenge.clone();
    }

    public @NonNull Set<Integer> getIdentifiers() {
        return new HashSet(mIdentifiers);
    }

    public @NonNull boolean isIndividualAttestation() {
        return mUseIndividualAttestation;
    }

    public @NonNull int getSecurityLevel() {
        return mSecurityLevel;
    }

    /** Private constructor. Use {@link DeviceAttestationRequest.Builder} to create an instance. */
    private DeviceAttestationRequest() {}

    /** Builder for {@link DeviceAttestationRequest}. */
    public static final class Builder {

        private byte[] mAttestationChallenge;
        private Set<Integer> mIdentifiers = new HashSet<Integer>();
        private boolean mUseIndividualAttestation = false;
        private int mSecurityLevel = KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT;

        /**
         * Constructs a new {@link DeviceAttestationRequest.Builder} with a given challenge.
         *
         * <p>The purpose of the challenge value is to enable relying parties to verify that the key
         * was created in response to a specific request. The value of this array is included as
         * part of the generated attestation extension, either as the {@code attestation_challenge}
         * field (for ASN.1-based attestations), or as the {@code nonce} claim (for EAT
         * attestations).
         *
         * @param attestationChallenge a chosen byte-array of length [8, 64].
         */
        Builder(@NonNull byte[] attestationChallenge) {
            mAttestationChallenge = attestationChallenge.clone();
        }

        /**
         * Add an identifier to attest to.
         *
         * @param identifier one of the {@code DeviceAttestationRequest#DEVICE_IDENTIFIER_*}
         *     constants.
         * @return this builder instance.
         */
        public @NonNull Builder addIdentifier(
                @DeviceAttestationRequest.DeviceIdentifier int identifier) {
            mIdentifiers.add(identifier);

            return this;
        }

        /**
         * Specifies whether the device should sign the attestation record using its device-unique
         * attestation certificate. Set to false by default.
         *
         * @param useIndividualAttestation whether to use individual attestation.
         * @return this builder instance.
         */
        public @NonNull Builder setIndividualAttestation(boolean useIndividualAttestation) {
            mUseIndividualAttestation = useIndividualAttestation;

            return this;
        }

        /**
         * Specifies the requested security level for the attestation. Set to {@link
         * KeyProperties#SECURITY_LEVEL_TRUSTED_ENVIRONMENT} by default.
         *
         * @param securityLevel
         * @return this builder instance.
         * @throws IllegalArgumentException for unsupported security levels.
         */
        public @NonNull Builder setSecurityLevel(
                @KeyProperties.SecurityLevelEnum int securityLevel) {
            if (securityLevel < 0) {
                // KeyProperties.SecurityLevelEnum also contains negative values, indicating
                // unknown security levels.
                throw new IllegalArgumentException("Unsupported security level");
            }
            mSecurityLevel = securityLevel;

            return this;
        }

        /**
         * Build a DeviceAttestationRequest.
         *
         * @return the DeviceAttestationRequest.
         */
        public @NonNull DeviceAttestationRequest build() {
            DeviceAttestationRequest result = new DeviceAttestationRequest();
            result.mAttestationChallenge = mAttestationChallenge;
            result.mIdentifiers.addAll(mIdentifiers);
            result.mUseIndividualAttestation = mUseIndividualAttestation;
            result.mSecurityLevel = mSecurityLevel;
            return result;
        }
    }
}
