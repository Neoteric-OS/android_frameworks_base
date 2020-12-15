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

package com.android.server.locksettings;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

class AesEncryptedBlob {
    /** The algorithm used for the encryption of the key blob. */
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";

    private final byte[] mUnencryptedData;
    private final byte[] mEncryptedBlob;
    private final byte[] mIv;
    private final SecretKey mKey;

    AesEncryptedBlob(byte[] unencryptedData,  byte[] encryptedBlob, byte[] iv, SecretKey key) {
        mUnencryptedData = unencryptedData;
        mEncryptedBlob = encryptedBlob;
        mIv = iv;
        mKey = key;
    }

    public byte[] getUnencryptedData() {
        return mUnencryptedData;
    }

    public byte[] getEncryptedBlob() {
        return mEncryptedBlob;
    }

    public byte[] getIv() {
        return mIv;
    }

    public SecretKey getKey() {
        return mKey;
    }

    static AesEncryptedBlob decryptBlob(SecretKey key, byte[] blob) throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(blob);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(blob));
        int ivSize = dis.readInt();
        if (ivSize < 0 || ivSize > 32) {
            throw new IOException("IV out of range: " + ivSize);
        }
        byte[] iv = new byte[ivSize];
        dis.readFully(iv);

        int cipherTextSize = dis.readInt();
        if (cipherTextSize < 0) {
            throw new IOException("Invalid cipher text size: " + cipherTextSize);
        }

        byte[] cipherText = new byte[cipherTextSize];
        dis.readFully(cipherText);

        final byte[] unencryptedData;
        try {
            Cipher c = Cipher.getInstance(CIPHER_ALGO);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            unencryptedData = c.doFinal(cipherText);
        } catch (NoSuchAlgorithmException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException | NoSuchPaddingException
                | InvalidAlgorithmParameterException e) {
            throw new IOException("Could not decrypt cipher text", e);
        }

        return new AesEncryptedBlob(unencryptedData, blob, iv, key);
    }

    static AesEncryptedBlob encryptBlob(SecretKey key, byte[] blob)
            throws IOException {
        Objects.requireNonNull(key);
        Objects.requireNonNull(blob);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        final byte[] cipherText;
        final byte[] iv;
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipherText = cipher.doFinal(blob);
            iv = cipher.getIV();
        } catch (NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException
                | NoSuchPaddingException | InvalidKeyException e) {
            throw new IOException("Could not encrypt input data", e);
        }

        dos.writeInt(iv.length);
        dos.write(iv);
        dos.writeInt(cipherText.length);
        dos.write(cipherText);

        return new AesEncryptedBlob(blob, bos.toByteArray(), iv, key);
    }
}
