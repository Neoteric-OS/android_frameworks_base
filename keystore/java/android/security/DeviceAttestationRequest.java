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
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/** Request class for use with {@link AttestationManager}. */
public final class DeviceAttestationRequest implements Parcelable {

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
     * android.telephony.TelephonyManager}.
     */
    public static final int DEVICE_IDENTIFIER_IMEI = 5;

    /**
     * Specifies that the device should attest to the MEIDs for all radios, as provided by {@link
     * android.telephony.TelephonyManager}.
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

    byte[] mAttestationChallenge = new byte[0];
    int[] mIdentifiers = new int[0];
    boolean mUseIndividualAttestation = false;

    public @NonNull byte[] getAttestationChallenge() {
        return mAttestationChallenge.clone();
    }

    public @NonNull int[] getIdentifiers() {
        return mIdentifiers.clone();
    }

    public @NonNull boolean isIndividualAttestation() {
        return mUseIndividualAttestation;
    }

    /** Private constructor. Use {@link DeviceAttestationRequest.Builder} to create an instance. */
    private DeviceAttestationRequest() {}

    private DeviceAttestationRequest(@NonNull Parcel in) {
        in.readByteArray(mAttestationChallenge);
        in.readIntArray(mIdentifiers);
        mUseIndividualAttestation = in.readBoolean();
    }

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeByteArray(mAttestationChallenge);
        out.writeIntArray(mIdentifiers);
        out.writeBoolean(mUseIndividualAttestation);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Parcelable.Creator<DeviceAttestationRequest> CREATOR =
            new Parcelable.Creator<DeviceAttestationRequest>() {
                @Override
                public DeviceAttestationRequest createFromParcel(Parcel in) {
                    return new DeviceAttestationRequest(in);
                }

                @Override
                public DeviceAttestationRequest[] newArray(int size) {
                    return new DeviceAttestationRequest[size];
                }
            };

    /** Builder for {@link DeviceAttestationRequest}. */
    public static final class Builder {

        private byte[] mAttestationChallenge;
        private List<Integer> mIdentifiers = new ArrayList<Integer>();
        private boolean mUseIndividualAttestation = false;

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
         * Build a DeviceAttestationRequest.
         *
         * @return the DeviceAttestationRequest.
         */
        public @NonNull DeviceAttestationRequest build() {
            DeviceAttestationRequest result = new DeviceAttestationRequest();
            result.mAttestationChallenge = mAttestationChallenge;
            result.mIdentifiers = mIdentifiers.stream().mapToInt(i -> i).toArray();
            result.mUseIndividualAttestation = mUseIndividualAttestation;
            return result;
        }
    }
}
