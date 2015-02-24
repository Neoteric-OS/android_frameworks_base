/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

/**
 * @hide
 */
public abstract class KeyStoreDelegatedCipherSpi extends CipherSpi {

    public static abstract class AES {
        public static abstract class ECB {
            public static class NoPadding extends KeyStoreDelegatedCipherSpi {
                public NoPadding() {
                    super("AES/ECB/NoPadding");
                }
            }

            public static class PKCS7Padding extends KeyStoreDelegatedCipherSpi {
                public PKCS7Padding() {
                    super("AES/ECB/PKCS7Padding");
                }
            }
        }

        public static abstract class CBC {
            public static class NoPadding extends KeyStoreDelegatedCipherSpi {
                public NoPadding() {
                    super("AES/CBC/NoPadding");
                }
            }

            public static class PKCS7Padding extends KeyStoreDelegatedCipherSpi {
                public PKCS7Padding() {
                    super("AES/CBC/PKCS7Padding");
                }
            }
        }

        public static abstract class GCM {
            public static class NoPadding extends KeyStoreDelegatedCipherSpi {
                public NoPadding() {
                    super("AES/GCM/NoPadding");
                }
            }
        }
    }

    private final String mTransformation;

    private Cipher mDelegate;

    protected KeyStoreDelegatedCipherSpi(String transformation) {
        mTransformation = transformation;
    }

    protected String getTransformation() {
        return mTransformation;
    }

    private Cipher getDelegate() {
        if (mDelegate == null) {
            try {
                mDelegate = Cipher.getInstance(getTransformation(), "AndroidOpenSSL");
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to get Cipher " + getTransformation() + " from Conscrypt", e);
            }
        }
        return mDelegate;
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        // Workaround for b/19099415
        if (input == null) {
            input = new byte[0];
        }

        return getDelegate().doFinal(input, inputOffset, inputLen);
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output,
            int outputOffset) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {

        // Workaround for b/19099415
        if (input == null) {
            input = new byte[0];
        }

        return getDelegate().doFinal(input, inputOffset, inputLen, output, outputOffset);
    }

    @Override
    protected int engineDoFinal(ByteBuffer input, ByteBuffer output)
            throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        return getDelegate().doFinal(input, output);
    }

    @Override
    protected int engineGetBlockSize() {
        return getDelegate().getBlockSize();
    }

    @Override
    protected byte[] engineGetIV() {
        return getDelegate().getIV();
    }

    @Override
    protected int engineGetOutputSize(int inputLen) {
        return getDelegate().getOutputSize(inputLen);
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return getDelegate().getParameters();
    }

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        if (!(key instanceof DelegatedSecretKey)) {
            throw new InvalidKeyException(
                    "Unsupported key: " + ((key != null) ? key.getClass().getName() : "null"));
        }

        if ((opmode != Cipher.ENCRYPT_MODE) && (opmode != Cipher.DECRYPT_MODE)) {
            throw new UnsupportedOperationException(
                    "Only ENCRYPT and DECRYPT modes supported. Mode: " + opmode);
        }
        getDelegate().init(opmode, ((DelegatedSecretKey) key).getDelegate(), random);
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params,
            SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (!(key instanceof DelegatedSecretKey)) {
            throw new InvalidKeyException(
                    "Unsupported key: " + ((key != null) ? key.getClass().getName() : "null"));
        }

        if ((opmode != Cipher.ENCRYPT_MODE) && (opmode != Cipher.DECRYPT_MODE)) {
            throw new UnsupportedOperationException(
                    "Only ENCRYPT and DECRYPT modes supported. Mode: " + opmode);
        }
        getDelegate().init(opmode, ((DelegatedSecretKey) key).getDelegate(), params, random);
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (!(key instanceof DelegatedSecretKey)) {
            throw new InvalidKeyException(
                    "Unsupported key: " + ((key != null) ? key.getClass().getName() : "null"));
        }

        if ((opmode != Cipher.ENCRYPT_MODE) && (opmode != Cipher.DECRYPT_MODE)) {
            throw new UnsupportedOperationException(
                    "Only ENCRYPT and DECRYPT modes supported. Mode: " + opmode);
        }
        getDelegate().init(opmode, ((DelegatedSecretKey) key).getDelegate(), params, random);
    }

    @Override
    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void engineSetPadding(String padding) throws NoSuchPaddingException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
        return getDelegate().unwrap(wrappedKey, wrappedKeyAlgorithm, wrappedKeyType);
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        return getDelegate().update(input, inputOffset, inputLen);
    }

    @Override
    protected int engineUpdate(ByteBuffer input, ByteBuffer output) throws ShortBufferException {
        return getDelegate().update(input, output);
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output,
            int outputOffset) throws ShortBufferException {
        return getDelegate().update(input, inputOffset, inputLen, output, outputOffset);
    }

    @Override
    protected void engineUpdateAAD(byte[] input, int inputOffset, int inputLen) {
        getDelegate().updateAAD(input, inputOffset, inputLen);
    }

    @Override
    protected void engineUpdateAAD(ByteBuffer input) {
        getDelegate().updateAAD(input);
    }

    @Override
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
        return getDelegate().wrap(key);
    }
}
