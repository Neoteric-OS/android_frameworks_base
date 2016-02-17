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
 * Message hashing algorithms supported in NDEF (NFC Data Exchange Format)
 * Signature Records as defined in the NFC Forum Signature Record Type
 * Definition Technical Specification Version 2.0.
 */
public enum NdefHashType {
    SHA_256((byte)0x02, "SHA256", "SHA-256 [SHS]");

    private final byte mId;
    private final String mName;
    private final String mDescription;

    NdefHashType(byte id, String name, String description) {
        mId = id;
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
     * Get the correspond hash type instance of the given id if exists or null
     * if not.
     */
    public static NdefHashType getInstanceOf(byte id) {
        if (id == SHA_256.getId()) {
            return SHA_256;
        } else {
            return null;
        }
    }
}
