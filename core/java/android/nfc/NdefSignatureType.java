/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.nfc;

/**
 * Digital signatures types supported in NDEF (NFC Data Exchange Format)
 * Signature Records as defined in the NFC Forum Signature Record Type
 * Definition Technical Specification Version 2.0.
 */
public enum NdefSignatureType {
    NO_SIGNATURE_PRESENT  ((byte)0x00, "No signature present",            "NONE",  00),
    RSASSA_PSS_1024       ((byte)0x01, "RSASSA-PSS [PKCS_1] 1024",        "RSA",   80),
    RSASSA_PKCS1_V1_5_1024((byte)0x02, "RSASSA-PKCS1-v1_5 [PKCS_1] 1024", "RSA",   80),
    DSA_1024              ((byte)0x03, "DSA [DSS] 1024",                  "DSA",   80),
    ECDSA_P192            ((byte)0x04, "ECDSA [DSS] P192",                "ECDSA", 80),
    RSASSA_PSS_2048       ((byte)0x05, "RSASSA-PSS [PKCS_1] 2048",        "RSA",   112),
    RSASSA_PKCS1_v1_2048  ((byte)0x06, "RSASSA-PKCS1-v1 [PKCS_1] 2048",   "RSA",   112),
    DSA_2048              ((byte)0x07, "DSA [DSS] 2048",                  "DSA",   112),
    ECDSA_P224            ((byte)0x08, "ECDSA [DSS] P224",                "ECDSA", 112),
    ECDSA_K233            ((byte)0x09, "ECDSA [DSS] K233",                "ECDSA", 112),
    ECDSA_B233            ((byte)0x0A, "ECDSA [DSS] B233",                "ECDSA", 112),
    ECDSA_P256            ((byte)0x0B, "ECDSA [DSS] P256",                "ECDSA", 128);

    private final byte mId;
    private final String mDescription;
    private final String mAlgorithm;
    private final int mSecurityStrengthBits;

    /**
     * Signature Type is defined as 7 bits on spec, so make sure only 7 bits is
     * stored in mId field.
     */
    NdefSignatureType(byte id, String description, String algorithm, int securityStrengthBits) {
        mId = (byte)(id & 0x7F);
        mDescription = description;
        mAlgorithm = algorithm;
        mSecurityStrengthBits = securityStrengthBits;
    }

    public byte getId() {
        return mId;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getAlgorithm() {
        return mAlgorithm;
    }

    public int getSecurityStrengthBits() {
        return mSecurityStrengthBits;
    }

    /**
     * Get the correspond signature type instance of the given id if exists or
     * null if not.
     */
    public static NdefSignatureType getInstanceOf(byte id) {
        if (id == NO_SIGNATURE_PRESENT.getId()) {
            return NO_SIGNATURE_PRESENT;
        } else if (id == RSASSA_PSS_1024.getId()) {
            return RSASSA_PSS_1024;
        } else if (id == RSASSA_PKCS1_V1_5_1024.getId()) {
            return RSASSA_PKCS1_V1_5_1024;
        } else if (id == DSA_1024.getId()) {
            return DSA_1024;
        } else if (id == ECDSA_P192.getId()) {
            return ECDSA_P192;
        } else if (id == RSASSA_PSS_2048.getId()) {
            return RSASSA_PSS_2048;
        } else if (id == RSASSA_PKCS1_v1_2048.getId()) {
            return RSASSA_PKCS1_v1_2048;
        } else if (id == DSA_2048.getId()) {
            return DSA_2048;
        } else if (id == ECDSA_P224.getId()) {
            return ECDSA_P224;
        } else if (id == ECDSA_K233.getId()) {
            return ECDSA_K233;
        } else if (id == ECDSA_B233.getId()) {
            return ECDSA_B233;
        } else if (id == ECDSA_P256.getId()) {
            return ECDSA_P256;
        } else {
            return null;
        }
    }
}

