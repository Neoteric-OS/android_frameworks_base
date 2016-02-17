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
 * Digital certificate formats supported in NDEF (NFC Data Exchange Format)
 * Signature Records as defined in the NFC Forum Signature Record Type
 * Definition Technical Specification Version 2.0.
 */
public enum NdefCertificateFormat {
    X_509((byte)0x00, "X509", "X.509 [X_509]"),
    M2M  ((byte)0x01, "M2M",  "M2M");

    private final byte mId;
    private final String mName;
    private final String mDescription;

    /**
     * Signature Type is defined as 3 bits on spec, so make sure only 3 bits is
     * stored in mId field.
     */
    NdefCertificateFormat(byte id, String name, String description) {
        mId = (byte)(id & 0x07);
        mName = name;
        mDescription = description;
    }

    public byte getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return mDescription;
    }

    /**
     * Get the correspond certificate format instance of the given id if exists
     * or null if not.
     */
    public static NdefCertificateFormat getInstanceOf(byte id) {
        if (id == X_509.getId()) {
            return X_509;
        } else if (id == M2M.getId()) {
            return M2M;
        } else {
            return null;
        }
    }
}
