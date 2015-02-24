package android.security;

import android.os.IBinder;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;
import android.security.keymaster.OperationResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import javax.crypto.spec.GCMParameterSpec;

/**
 * @hide
 */
public abstract class KeyStoreAuthenticatedCipherSpi extends CipherSpi {

    public static final class AES {
        private AES() {}

        public static class OCB extends KeyStoreAuthenticatedCipherSpi {

            private byte[] mIV;
            private Integer mTagLength;

            protected OCB(String transformation) {
                super(transformation);
            }

            @Override
            protected byte[] engineGetIV() {
                return (mIV != null) ? mIV.clone() : null;
            }

            @Override
            protected void addAlgorithmSpecificParameters(
                    int opmode, AlgorithmParameterSpec paramSpec, AlgorithmParameters params,
                    KeymasterArguments keymasterArgs) throws InvalidAlgorithmParameterException {
                super.addAlgorithmSpecificParameters(opmode, paramSpec, params, keymasterArgs);

                // TODO: Use OCBParameterSpec
                if (paramSpec instanceof GCMParameterSpec) {
                    GCMParameterSpec gcmParamSpec = (GCMParameterSpec) paramSpec;
                    mIV = gcmParamSpec.getIV();
                    if (mIV == null) {
                        throw new InvalidAlgorithmParameterException(
                                "IV is null in OCBParameterSpec");
                    }
                    mTagLength = gcmParamSpec.getTLen();
                    // TODO: Provide tag length to keymaster once it supports that
                    // TODO: Provide IV to keymaster once it supports that
                    // keymasterArgs.addBlob(KeymasterDefs.KM_TAG_NONCE, mIV);
                } else if (paramSpec != null) {
                    throw new InvalidAlgorithmParameterException(
                            "OCBParameterSpec must be provided. Actual: " + paramSpec);
                } else {
                    if (opmode == Cipher.DECRYPT_MODE) {
                        throw new InvalidAlgorithmParameterException(
                                "OCBParameterSpec must be provided when decrypting");
                    }
                }

                keymasterArgs.addInt(KeymasterDefs.KM_TAG_BLOCK_MODE, KeymasterDefs.KM_MODE_OCB);
            }

            /**
             * Invoked by {@code engineInit} to obtain algorithm-specific parameters from the result of the
             * Keymaster's {@code begin} operation.
             */
            @Override
            protected void loadAlgorithmSpecificParameters(KeymasterArguments keymasterArgs) {
                super.loadAlgorithmSpecificParameters(keymasterArgs);

                mIV = keymasterArgs.getBlob(KeymasterDefs.KM_TAG_NONCE, mIV);
            }


            public static final class NoPadding extends OCB {
                public NoPadding() {
                    super("AES/OCB/NoPadding");
                }

                @Override
                protected void addAlgorithmSpecificParameters(
                        int opmode, AlgorithmParameterSpec paramSpec, AlgorithmParameters params,
                        KeymasterArguments keymasterArgs)
                                throws InvalidAlgorithmParameterException {
                    super.addAlgorithmSpecificParameters(opmode, paramSpec, params, keymasterArgs);
                    keymasterArgs.addInt(KeymasterDefs.KM_TAG_PADDING, KeymasterDefs.KM_PAD_NONE);
                }
            }
        }
    }

    private final KeyStore mKeyStore;
    private final String mTransformation;

    /**
     * Token referencing this operation inside keystore service. It is initialized by
     * {@code engineInit} and is invalidated when {@code engineDoFinal} succeeds and one some
     * error conditions in between.
     */
    private IBinder mOperationToken;

    // Fields below must be reset by init/doFinal.
    private ByteArrayOutputStream mBufferedOutput;

    // Fields below are populated by Cipher.init and should be preserved after doFinal finishes.
    private boolean mEncrypting;
    private KeyStoreSecretKey mKey;


    protected KeyStoreAuthenticatedCipherSpi(String transformation) {
        mKeyStore = KeyStore.getInstance();
        mTransformation = transformation;
    }

    @Override
    protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
            throws IllegalBlockSizeException, BadPaddingException {
        if (mOperationToken == null) {
            throw new IllegalStateException("Not initialized");
        }

        byte[] updateOutput = null;
        if (inputLen > 0) {
            updateOutput = engineUpdate(input, inputOffset, inputLen);
        }

        OperationResult opResult = mKeyStore.finish(mOperationToken, null, null);
        if (opResult == null) {
            mOperationToken = null;
            throw new IllegalStateException("Keystore crashed");
        }
        if ((opResult.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (opResult.resultCode != KeyStore.NO_ERROR)) {
            throw new BadPaddingException("Keystore error code: " + opResult.resultCode);
        }
        System.out.println(this + ".engineDoFinal got "
                + ((opResult.output != null) ? String.valueOf(opResult.output.length) : "null")
                + " from keystore");
        mOperationToken = null;
        byte[] finalOutput = opResult.output;

        if ((updateOutput == null) || (updateOutput.length == 0)) {
            if (mBufferedOutput != null) {
                if (finalOutput != null) {
                    try {
                        mBufferedOutput.write(finalOutput);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to buffer output", e);
                    }
                }
                byte[] result = mBufferedOutput.toByteArray();
                System.out.println(this + ".engineDoFinal total buffered: " + result.length);
                mBufferedOutput = null;
                return result;
            } else {
                System.out.println(this + ".engineDoFinal nothing buffered");
                if (finalOutput != null) {
                    return finalOutput;
                } else {
                    return new byte[0];
                }
            }
        } else {
            if (mBufferedOutput != null) {
                throw new IllegalStateException("update did not buffer output when buffered output"
                        + " present");
            }
            System.out.println(this + ".engineDoFinal returning updateOutput + finalOutput");
            return concat(updateOutput, finalOutput);
        }
    }

    @Override
    protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output,
            int outputOffset) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected int engineGetBlockSize() {
        return 0;
    }

    @Override
    protected byte[] engineGetIV() {
        return null;
    }

    @Override
    protected int engineGetOutputSize(int arg0) {
        return 0;
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        return null;
    }

    /**
     * Invoked by {@code engineInit} to add algorithm-specific parameters to be passed to
     * Keymaster's {@code begin} operation.
     *
     * @throws InvalidAlgorithmParameterException if some/all of the parameters cannot be
     *         automatically configured and thus {@code Cipher.init} needs to be invoked with
     *         explicitly provided parameters.
     */
    protected void addAlgorithmSpecificParameters(
            int opmode, AlgorithmParameterSpec paramSpec, AlgorithmParameters params,
            KeymasterArguments keymasterArgs) throws InvalidAlgorithmParameterException {}

    /**
     * Invoked by {@code engineInit} to obtain algorithm-specific parameters from the result of the
     * Keymaster's {@code begin} operation.
     */
    protected void loadAlgorithmSpecificParameters(KeymasterArguments keymasterArgs) {}

    @Override
    protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
        try {
            engineInit(opmode, key, (AlgorithmParameterSpec) null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException(e.getMessage());
        }
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params,
            SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (!(key instanceof KeyStoreSecretKey)) {
            throw new InvalidKeyException(
                    "Unsupported key: " + ((key != null) ? key.getClass().getName() : "null"));
        }
        mKey = (KeyStoreSecretKey) key;

        if ((opmode != Cipher.ENCRYPT_MODE) && (opmode != Cipher.DECRYPT_MODE)) {
            throw new UnsupportedOperationException(
                    "Only ENCRYPT and DECRYPT modes supported. Mode: " + opmode);
        }

        KeymasterArguments keymasterInputArgs = new KeymasterArguments();
        addAlgorithmSpecificParameters(opmode, params, null, keymasterInputArgs);

        // TODO: Remove these parameters once Keymaster HAL is fixed.
        keymasterInputArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        keymasterInputArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);

        // IMPLEMENTATION NOTE: Configure keymaster to add authentication tag only at the very end
        // of ciphertext. This is to make the resulting ciphertext compatible with the contract of
        // Cipher class. A side-effect of this configuration is that during decrypting plaintext is
        // returned to us before it's authenticated. Thus, when decrypting this class must buffer
        // plaintext until all of ciphertext is processed and doFinal has verified all of the
        // ciphertext against  the authentication tag.
        // TODO: Uncomment this code once Keymaster HAL honors these parameters during "begin"
        // keymasterInputArgs.addInt(KeymasterDefs.KM_TAG_CHUNK_LENGTH, 0);
        // keymasterInputArgs.addBoolean(KeymasterDefs.KM_TAG_RETURN_UNAUTHED);

        KeymasterArguments keymasterOutputArgs = new KeymasterArguments();
        int keymasterPurpose = (opmode == Cipher.ENCRYPT_MODE)
                ? KeymasterDefs.KM_PURPOSE_ENCRYPT : KeymasterDefs.KM_PURPOSE_DECRYPT;
        OperationResult opResult = mKeyStore.begin(
                mKey.getAlias(),
                keymasterPurpose,
                true, // permit aborting this operation if keystore runs out of resources
                keymasterInputArgs,
                keymasterOutputArgs);
        if (opResult == null) {
            throw new IllegalStateException("Keystore crashed");
        }
        if ((opResult.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (opResult.resultCode != KeyStore.NO_ERROR)) {
            throw new InvalidKeyException("Keystore error code: " + opResult.resultCode);
        }

        mOperationToken = opResult.token;
        mBufferedOutput = null;

        loadAlgorithmSpecificParameters(keymasterOutputArgs);
    }

    @Override
    protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        if (!(key instanceof KeyStoreSecretKey)) {
            throw new InvalidKeyException(
                    "Unsupported key: " + ((key != null) ? key.getClass().getName() : "null"));
        }
        mKey = (KeyStoreSecretKey) key;

        if ((opmode != Cipher.ENCRYPT_MODE) && (opmode != Cipher.DECRYPT_MODE)) {
            throw new UnsupportedOperationException(
                    "Only ENCRYPT and DECRYPT modes supported. Mode: " + opmode);
        }

        throw new UnsupportedOperationException();
    }

    @Override
    protected void engineSetMode(String arg0) throws NoSuchAlgorithmException {
    }

    @Override
    protected void engineSetPadding(String arg0) throws NoSuchPaddingException {
    }

    @Override
    protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
        if (mOperationToken == null) {
            throw new IllegalStateException("Not initialized");
        }
        if (inputLen == 0) {
            return null;
        }
        OperationResult opResult =
                mKeyStore.update(mOperationToken, null, subarray(input, inputOffset, inputLen));
        if (opResult == null) {
            mOperationToken = null;
            throw new IllegalStateException("Keystore crashed");
        }

        if (mEncrypting) {
            System.out.println(this + ".engineUpdate produced "
                    + ((opResult.output != null) ? String.valueOf(opResult.output.length) : "null"));
            return opResult.output;
        } else {
            System.out.println(this + ".engineUpdate buffered "
                    + ((opResult.output != null) ? String.valueOf(opResult.output.length) : "null"));
            if ((opResult.output != null) && (opResult.output.length > 0)) {
                if (mBufferedOutput == null) {
                    mBufferedOutput = new ByteArrayOutputStream();
                }
                try {
                    mBufferedOutput.write(opResult.output);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to buffer output");
                }
            }
            return null;
        }
    }

    @Override
    protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output,
            int outputOffset) throws ShortBufferException {
        throw new UnsupportedOperationException();
    }

    private static byte[] subarray(byte[] array, int offset, int len) {
        if ((offset == 0) && (array.length == len)) {
            return array;
        }
        byte[] result = new byte[len];
        System.arraycopy(array, offset, result, 0, len);
        return result;
    }

    private static byte[] concat(byte[]... arrays) {
        int outputSize = 0;
        for (byte[] array : arrays) {
            outputSize += (array != null) ? array.length : 0;
        }

        byte[] result = new byte[outputSize];
        int offset = 0;
        for (byte[] array : arrays) {
            if ((array != null) && (array.length > 0)) {
                System.arraycopy(array, 0, result, offset, array.length);
                offset += array.length;
            }
        }
        return result;
    }
}