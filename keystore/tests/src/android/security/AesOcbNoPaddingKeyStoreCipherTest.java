package android.security;

import android.os.IBinder;
import android.security.keymaster.KeyCharacteristics;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;
import android.security.keymaster.OperationResult;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;

public class AesOcbNoPaddingKeyStoreCipherTest extends TestCase {
    private static final String KEYSTORE_KEY_ALIAS =
            AesOcbNoPaddingKeyStoreCipherTest.class.getName();
    private KeyStore mAndroidKeyStore;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAndroidKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mAndroidKeyStore.load(null);
        mAndroidKeyStore.deleteEntry(KEYSTORE_KEY_ALIAS);
        assertFalse(mAndroidKeyStore.containsAlias(KEYSTORE_KEY_ALIAS));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            mAndroidKeyStore.deleteEntry(KEYSTORE_KEY_ALIAS);
        } finally {
            super.tearDown();
        }
    }

    public void testGenerateAndEncrypt() throws Exception {
        KeymasterArguments keyGenArgs = new KeymasterArguments();
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_ALGORITHM, KeymasterDefs.KM_ALGORITHM_AES);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_PURPOSE, KeymasterDefs.KM_PURPOSE_ENCRYPT);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_PURPOSE, KeymasterDefs.KM_PURPOSE_DECRYPT);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_PURPOSE, KeymasterDefs.KM_PURPOSE_SIGN);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_PURPOSE, KeymasterDefs.KM_PURPOSE_VERIFY);
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_KEY_SIZE, 128);
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_BLOCK_MODE, KeymasterDefs.KM_MODE_OCB);
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_PADDING, KeymasterDefs.KM_PAD_NONE);
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_CHUNK_LENGTH, 1 << 30);
        keyGenArgs.addInt(KeymasterDefs.KM_TAG_MAC_LENGTH, 16);
        keyGenArgs.addBoolean(KeymasterDefs.KM_TAG_RETURN_UNAUTHED);
        keyGenArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        keyGenArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);
        keyGenArgs.addBoolean(KeymasterDefs.KM_TAG_CALLER_NONCE);
        android.security.KeyStore keystore = android.security.KeyStore.getInstance();
        KeyCharacteristics keyCharacteristics = new KeyCharacteristics();
        int errorCode = keystore.generateKey(Credentials.USER_SECRET_KEY + KEYSTORE_KEY_ALIAS,
                keyGenArgs,
                0,
                keyCharacteristics);
        if ((errorCode != KeymasterDefs.KM_ERROR_OK)
                && (errorCode != android.security.KeyStore.NO_ERROR)) {
            fail("Failed to generate key. Keystore error code: " + errorCode);
        }

//        KeyGenerator keyGenerator =
//                KeyGenerator.getInstance("AES", AndroidKeyStoreProvider.PROVIDER_NAME);
//        keyGenerator.init(new KeyGeneratorSpec.Builder()
//                .setAlias(KEYSTORE_KEY_ALIAS)
//                .setPurposes(KeyStoreKeyConstraints.Purpose.ENCRYPT
//                        | KeyStoreKeyConstraints.Purpose.DECRYPT
//                        | KeyStoreKeyConstraints.Purpose.SIGN
//                        | KeyStoreKeyConstraints.Purpose.VERIFY)
//                .setAlgorithm(KeyStoreKeyConstraints.Algorithm.AES)
//                .setBlockMode(KeyStoreKeyConstraints.BlockMode.OCB)
//                .setPadding(KeyStoreKeyConstraints.Padding.NONE)
//                .setKeySize(128)
//                .build());
//        SecretKey key = keyGenerator.generateKey();
//        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(
//                key.getAlgorithm(), "AndroidKeyStore");
//        KeyStoreKeySpec spec =
//                (KeyStoreKeySpec) keyFactory.getKeySpec(key, KeyStoreKeySpec.class);
//        System.out.println("*** Key size: " + spec.getKeySize());
//        System.out.println("*** Key purposes: 0b" + Integer.toString(spec.getPurposes(), 2));
//        System.out.println("*** Key algorithm: "
//                + KeyStoreKeyConstraints.Algorithm.toString(spec.getAlgorithm()));
//        System.out.println("*** Key block mode: " + spec.getBlockMode());
//        System.out.println("*** Key padding: " + spec.getPadding());

        KeymasterArguments keymasterInArgs = new KeymasterArguments();
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_ALGORITHM, KeymasterDefs.KM_ALGORITHM_AES);
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_PURPOSE, KeymasterDefs.KM_PURPOSE_ENCRYPT);
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_BLOCK_MODE, 192);
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_PADDING, KeymasterDefs.KM_PAD_NONE);
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_MAC_LENGTH, 16);
        // keymasterInArgs.addInt(KeymasterDefs.KM_TAG_CHUNK_LENGTH, 0);
        // keymasterInArgs.addBoolean(KeymasterDefs.KM_TAG_RETURN_UNAUTHED);
        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);
        KeymasterArguments keymasterOutArgs = new KeymasterArguments();
        OperationResult result = keystore.begin(
                Credentials.USER_SECRET_KEY + KEYSTORE_KEY_ALIAS,
                KeymasterDefs.KM_PURPOSE_ENCRYPT,
                true,
                keymasterInArgs,
                keymasterOutArgs);
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.begin failed with error code: " + result.resultCode);
        }
        IBinder opToken = result.token;
//        byte[] iv = keymasterOutArgs.getBlob(KeymasterDefs.KM_TAG_NONCE, null);
//        System.out.println("*** IV (" + iv.length + " bytes): " + HexEncoding.encode(iv));

        ByteArrayOutputStream ciphertextOut = new ByteArrayOutputStream();
        keymasterInArgs = new KeymasterArguments();
        result = keystore.update(opToken, keymasterInArgs, "Hello, World!".getBytes());
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.update failed with error code: " + result.resultCode);
        }
        System.out.println("*** KeyStore.update consumed " + result.inputConsumed + " bytes");
        if (result.output == null) {
            System.out.println("*** KeyStore.update output: null");
        } else {
            System.out.println("*** KeyStore.update output (" + result.output.length
                    + " bytes): " + HexEncoding.encode(result.output));
            ciphertextOut.write(result.output);
        }

        keymasterInArgs = new KeymasterArguments();
        result = keystore.finish(opToken, keymasterInArgs, null);
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.finish failed with error code: " + result.resultCode);
        }
        System.out.println("*** KeyStore.finish consumed " + result.inputConsumed + " bytes");
        if (result.output == null) {
            System.out.println("*** KeyStore.finish output: null");
        } else {
            System.out.println("*** KeyStore.finish output (" + result.output.length
                    + " bytes): " + HexEncoding.encode(result.output));
            ciphertextOut.write(result.output);
        }

        byte[] ciphertext = ciphertextOut.toByteArray();
        System.out.println();
        System.out.println("*** CIPHERTEXT (" + ciphertext.length + "): " + HexEncoding.encode(ciphertext));

        keymasterInArgs = new KeymasterArguments();
        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);
        // keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_NONCE, Arrays.copyOf(ciphertext, 12));
        // keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_NONCE, new byte[16]);
        keymasterOutArgs = new KeymasterArguments();
        result = keystore.begin(
                Credentials.USER_SECRET_KEY + KEYSTORE_KEY_ALIAS,
                KeymasterDefs.KM_PURPOSE_DECRYPT,
                true,
                keymasterInArgs,
                keymasterOutArgs);
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.begin failed with error code: " + result.resultCode);
        }
        opToken = result.token;

        ByteArrayOutputStream plaintextOut = new ByteArrayOutputStream();
        keymasterInArgs = new KeymasterArguments();
        result = keystore.update(opToken, keymasterInArgs, ciphertext);
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.update failed with error code: " + result.resultCode);
        }
        System.out.println("*** KeyStore.update consumed " + result.inputConsumed + " bytes");
        if (result.output == null) {
            System.out.println("*** KeyStore.update output: null");
        } else {
            System.out.println("*** KeyStore.update output (" + result.output.length
                    + " bytes): " + HexEncoding.encode(result.output));
            plaintextOut.write(result.output);
        }

        keymasterInArgs = new KeymasterArguments();
        result = keystore.finish(opToken, keymasterInArgs, null);
        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
            fail("KeyStore.finish failed with error code: " + result.resultCode);
        }
        System.out.println("*** KeyStore.finish consumed " + result.inputConsumed + " bytes");
        if (result.output == null) {
            System.out.println("*** KeyStore.finish output: null");
        } else {
            System.out.println("*** KeyStore.finish output (" + result.output.length
                    + " bytes): " + HexEncoding.encode(result.output));
            plaintextOut.write(result.output);
        }

        byte[] plaintext = plaintextOut.toByteArray();
        System.out.println();
        System.out.println("*** PLAINTEXT (" + plaintext.length + "): " + new String(plaintext)
            + " (" + HexEncoding.encode(plaintext) + ")");

        System.out.println("******** SUCCESS!!!");
    }

//    public void testGenerateAndEncrypt() throws Exception {
//        KeyGenerator keyGenerator =
//                KeyGenerator.getInstance("AES", AndroidKeyStoreProvider.PROVIDER_NAME);
//        keyGenerator.init(new KeyGeneratorSpec.Builder()
//                .setAlias(KEYSTORE_KEY_ALIAS)
//                .setAlgorithm(KeyStoreKeyConstraints.Algorithm.AES)
//                .setBlockMode(KeyStoreKeyConstraints.BlockMode.OCB)
//                .setPadding(KeyStoreKeyConstraints.Padding.NONE)
//                .setKeySize(128)
//                .build());
//        SecretKey key = keyGenerator.generateKey();
//
//        Cipher cipher = Cipher.getInstance("AES/OCB/NoPadding", "AndroidKeyStore");
//        cipher.init(Cipher.ENCRYPT_MODE, key);
//
////        byte[] iv = cipher.getIV();
////        System.out.println("*** IV (" + iv.length + " bytes): " + HexEncoding.encode(iv));
//        byte[] ciphertext = cipher.doFinal("Hello".getBytes());
//        assertNotNull(ciphertext);
//        System.out.println("*** Ciphertext (" + ciphertext.length + " bytes): " + HexEncoding.encode(ciphertext));
//
//        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, new byte[12]));
//        byte[] plaintext = cipher.doFinal(ciphertext);
//        assertNotNull(plaintext);
//        System.out.println("*** Plaintext (" + plaintext.length + " bytes): "
//                + new String(plaintext) + " ( " + HexEncoding.encode(plaintext) + ")");
//
//
//        System.out.println("******** SUCCESS!!!");
//    }
}
