package android.security;

import android.os.IBinder;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;
import android.security.keymaster.OperationResult;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.MacSpi;

/**
 * @hide
 */
public abstract class KeyStoreHmacSpi extends MacSpi {

    public static class HmacSHA256 extends KeyStoreHmacSpi {
        public HmacSHA256() {
            super(KeyStoreKeyConstraints.Digest.SHA256, 256 / 8);
        }
    }

    private final KeyStore mKeyStore = KeyStore.getInstance();
    private final @KeyStoreKeyConstraints.DigestEnum int mDigest;
    private final int mMacSizeBytes;

    private String mKeyAliasInKeyStore;

    private IBinder mOperationToken;

    protected KeyStoreHmacSpi(@KeyStoreKeyConstraints.DigestEnum int digest, int macSizeBytes) {
        mDigest = digest;
        mMacSizeBytes = macSizeBytes;
    }

    @Override
    protected int engineGetMacLength() {
        return mMacSizeBytes;
    }

    @Override
    protected void engineInit(Key key, AlgorithmParameterSpec params) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        if (key == null) {
            throw new InvalidKeyException("key == null");
        } else if (!(key instanceof KeyStoreSecretKey)) {
            throw new InvalidKeyException(
                    "Only Android KeyStore secret keys supported. Key: " + key);
        }

        if (params != null) {
            throw new InvalidAlgorithmParameterException(
                    "Unsupported algorithm parameters: " + params);
        }

        mKeyAliasInKeyStore = ((KeyStoreSecretKey) key).getAlias();
        engineReset();
    }

    @Override
    protected void engineReset() {
        IBinder operationToken = mOperationToken;
        if (operationToken != null) {
            mOperationToken = null;
            mKeyStore.abort(operationToken);
        }

        KeymasterArguments keymasterArgs = new KeymasterArguments();
        keymasterArgs.addInt(KeymasterDefs.KM_TAG_DIGEST, mDigest);

        // TODO: Remove these parameters once Keymaster HAL is fixed.
        keymasterArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        keymasterArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);

        OperationResult opResult = mKeyStore.begin(mKeyAliasInKeyStore,
                KeymasterDefs.KM_PURPOSE_SIGN,
                true,
                keymasterArgs,
                new KeymasterArguments());
        if (opResult == null) {
            throw new IllegalStateException("Keystore service crashed");
        }
        int errorCode = opResult.resultCode;
        if ((errorCode != KeymasterDefs.KM_ERROR_OK) && (errorCode != KeyStore.NO_ERROR)) {
            throw new IllegalStateException("Keystore error code: " + errorCode);
        }
        mOperationToken = opResult.token;
    }

    @Override
    protected void engineUpdate(byte input) {
        engineUpdate(new byte[] {input}, 0, 1);
    }

    @Override
    protected void engineUpdate(byte[] input, int offset, int len) {
        if (mOperationToken == null) {
            throw new IllegalStateException("Not initialized");
        }

        OperationResult opResult =
                mKeyStore.update(mOperationToken, null, subarray(input, offset, len));
        if (opResult == null) {
            throw new IllegalStateException("Keystore service crashed");
        }
        int errorCode = opResult.resultCode;
        if ((errorCode != KeymasterDefs.KM_ERROR_OK) && (errorCode != KeyStore.NO_ERROR)) {
            throw new IllegalStateException("Keystore error code: " + errorCode);
        }
    }

    @Override
    protected byte[] engineDoFinal() {
        if (mOperationToken == null) {
            throw new IllegalStateException("Not initialized");
        }

        OperationResult opResult = mKeyStore.finish(mOperationToken, null, null);
        if (opResult == null) {
            throw new IllegalStateException("Keystore service crashed");
        }
        int errorCode = opResult.resultCode;
        if ((errorCode != KeymasterDefs.KM_ERROR_OK) && (errorCode != KeyStore.NO_ERROR)) {
            throw new IllegalStateException("Keystore error code: " + errorCode);
        }

        engineReset();
        return opResult.output;
    }

    @Override
    public void finalize() throws Throwable {
        try {
            IBinder operationToken = mOperationToken;
            if (operationToken != null) {
                mOperationToken = null;
                mKeyStore.abort(operationToken);
            }
        } finally {
            super.finalize();
        }
    }

    private static byte[] subarray(byte[] array, int offset, int len) {
        if ((offset == 0) && (array.length == len)) {
            return array;
        }
        byte[] result = new byte[len];
        System.arraycopy(array, offset, result, 0, len);
        return result;
    }
}
