package android.security;

import android.os.IBinder;
import android.security.keymaster.KeyCharacteristics;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;
import android.security.keymaster.OperationResult;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class KeyStoreHmacTest extends TestCase {
    private static final String KEYSTORE_KEY_ALIAS = KeyStoreHmacTest.class.getName();
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

//    public void testGenerateAndEncrypt() throws Exception {
//        KeymasterArguments keyGenArgs = new KeymasterArguments();
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_ALGORITHM, KeymasterDefs.KM_ALGORITHM_HMAC);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_DIGEST, KeymasterDefs.KM_DIGEST_SHA_2_256);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_KEY_SIZE, 256);
//        keyGenArgs.addInt(KeymasterDefs.KM_TAG_MAC_LENGTH, 32);
//        keyGenArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
//        keyGenArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);
//        android.security.KeyStore keystore = android.security.KeyStore.getInstance();
//        KeyCharacteristics keyCharacteristics = new KeyCharacteristics();
//        int errorCode = keystore.generateKey(Credentials.USER_SECRET_KEY + KEYSTORE_KEY_ALIAS,
//                keyGenArgs,
//                0,
//                keyCharacteristics);
//        if ((errorCode != KeymasterDefs.KM_ERROR_OK)
//                && (errorCode != android.security.KeyStore.NO_ERROR)) {
//            fail("Failed to generate key. Keystore error code: " + errorCode);
//        }
//
//        KeymasterArguments keymasterInArgs = new KeymasterArguments();
//        keymasterInArgs.addInt(KeymasterDefs.KM_TAG_ALGORITHM, KeymasterDefs.KM_ALGORITHM_HMAC);
//        keymasterInArgs.addInt(KeymasterDefs.KM_TAG_DIGEST, KeymasterDefs.KM_DIGEST_SHA_2_256);
//        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
//        keymasterInArgs.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);
//        KeymasterArguments keymasterOutArgs = new KeymasterArguments();
//        OperationResult result = keystore.begin(
//                Credentials.USER_SECRET_KEY + KEYSTORE_KEY_ALIAS,
//                KeymasterDefs.KM_PURPOSE_SIGN,
//                true,
//                keymasterInArgs,
//                keymasterOutArgs);
//        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
//                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
//            fail("KeyStore.begin failed with error code: " + result.resultCode);
//        }
//        IBinder opToken = result.token;
//
//        ByteArrayOutputStream ciphertextOut = new ByteArrayOutputStream();
//        keymasterInArgs = new KeymasterArguments();
//        result = keystore.update(opToken, keymasterInArgs, "Hello, World!".getBytes());
//        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
//                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
//            fail("KeyStore.update failed with error code: " + result.resultCode);
//        }
//        System.out.println("*** KeyStore.update consumed " + result.inputConsumed + " bytes");
//        if (result.output == null) {
//            System.out.println("*** KeyStore.update output: null");
//        } else {
//            System.out.println("*** KeyStore.update output (" + result.output.length
//                    + " bytes): " + HexEncoding.encode(result.output));
//            ciphertextOut.write(result.output);
//        }
//
//        keymasterInArgs = new KeymasterArguments();
//        result = keystore.finish(opToken, keymasterInArgs, null);
//        if ((result.resultCode != KeymasterDefs.KM_ERROR_OK)
//                && (result.resultCode != android.security.KeyStore.NO_ERROR)) {
//            fail("KeyStore.finish failed with error code: " + result.resultCode);
//        }
//        System.out.println("*** KeyStore.finish consumed " + result.inputConsumed + " bytes");
//        if (result.output == null) {
//            System.out.println("*** KeyStore.finish output: null");
//        } else {
//            System.out.println("*** KeyStore.finish output (" + result.output.length
//                    + " bytes): " + HexEncoding.encode(result.output));
//            ciphertextOut.write(result.output);
//        }
//
//        byte[] ciphertext = ciphertextOut.toByteArray();
//        System.out.println();
//        System.out.println("*** MAC (" + ciphertext.length + "): " + HexEncoding.encode(ciphertext));
//    }

    public void testHmacSHA256() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore");
        keyGenerator.init(new KeyGeneratorSpec.Builder()
                .setAlias(KEYSTORE_KEY_ALIAS)
                .build());
        SecretKey key = keyGenerator.generateKey();

        Mac mac = Mac.getInstance("HmacSHA256", "AndroidKeyStore");
        mac.init(key);
        byte[] output = mac.doFinal("Hello, World!".getBytes());

        System.out.println("*** MAC (" + output.length + "): " + HexEncoding.encode(output));

        output = mac.doFinal("Hello, World!".getBytes());

        System.out.println("*** MAC (" + output.length + "): " + HexEncoding.encode(output));

        output = mac.doFinal("Hello, World#2!".getBytes());

        System.out.println("*** MAC (" + output.length + "): " + HexEncoding.encode(output));
    }
}
