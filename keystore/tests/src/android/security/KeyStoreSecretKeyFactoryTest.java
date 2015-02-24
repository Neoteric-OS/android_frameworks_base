package android.security;

import junit.framework.TestCase;

import java.security.KeyStore;
import java.util.Collections;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

public class KeyStoreSecretKeyFactoryTest extends TestCase {
    private static final String KEYSTORE_KEY_ALIAS = KeyStoreSecretKeyFactoryTest.class.getName();
    private KeyStore mAndroidKeyStore;
    private android.security.KeyStore mKeyStore;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAndroidKeyStore = KeyStore.getInstance("AndroidKeyStore");
        mAndroidKeyStore.load(null);
        mAndroidKeyStore.deleteEntry(KEYSTORE_KEY_ALIAS);
        assertFalse(mAndroidKeyStore.containsAlias(KEYSTORE_KEY_ALIAS));

        mKeyStore = android.security.KeyStore.getInstance();
        if (mKeyStore.state() != android.security.KeyStore.State.UNINITIALIZED) {
            mKeyStore.reset();
        }
        mKeyStore.password("1111");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            mKeyStore.reset();
        } finally {
            super.tearDown();
        }
    }

    public void testGeneratedAes() throws Exception {
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance("AES", AndroidKeyStoreProvider.PROVIDER_NAME);
        keyGenerator.init(new KeyGeneratorSpec.Builder()
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setAlgorithm(KeyStoreKeyConstraints.Algorithm.AES)
                .setKeySize(64)
                .build());
        SecretKey key = keyGenerator.generateKey();

        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(
                key.getAlgorithm(), "AndroidKeyStore");
        KeyStoreKeySpec spec =
                (KeyStoreKeySpec) keyFactory.getKeySpec(key, KeyStoreKeySpec.class);
        assertEquals(KEYSTORE_KEY_ALIAS, spec.getKeystoreAlias());
        assertEquals(64, (int) spec.getKeySize());
        assertEquals(KeyStoreKeyConstraints.Algorithm.AES, (int) spec.getAlgorithm());
        assertEquals(null, spec.getBlockMode());
        assertEquals(null, spec.getPadding());
        assertEquals(KeyStoreKeyCharacteristics.Origin.GENERATED_OUTSIDE_OF_TEE, spec.getOrigin());
        assertEquals(0, spec.getPurposes());
        assertEquals(null, spec.getDigest());
        assertEquals(null, spec.getMaxSecondsSinceUserAuthentication());
        assertEquals(null, spec.getMaxUsesPerBoot());
        assertEquals(null, spec.getMinSecondsBetweenOperations());
        assertEquals(Collections.emptySet(), spec.getUserAuthenticators());
        assertEquals(Collections.emptySet(), spec.getTeeBackedUserAuthenticators());
    }

    public void testGeneratedHmacSHA256() throws Exception {
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance("HmacSHA256", AndroidKeyStoreProvider.PROVIDER_NAME);
        keyGenerator.init(new KeyGeneratorSpec.Builder()
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setKeySize(72)
                .build());
        SecretKey key = keyGenerator.generateKey();

        SecretKeyFactory keyFactory =
                SecretKeyFactory.getInstance(key.getAlgorithm(), "AndroidKeyStore");
        KeyStoreKeySpec spec =
                (KeyStoreKeySpec) keyFactory.getKeySpec(key, KeyStoreKeySpec.class);
        assertEquals(KEYSTORE_KEY_ALIAS, spec.getKeystoreAlias());
        assertEquals(72, (int) spec.getKeySize());
        assertEquals(KeyStoreKeyConstraints.Algorithm.HMAC, (int) spec.getAlgorithm());
        assertEquals(KeyStoreKeyConstraints.Digest.SHA256, (int) spec.getDigest());
        assertEquals(null, spec.getBlockMode());
        assertEquals(null, spec.getPadding());
        assertEquals(KeyStoreKeyCharacteristics.Origin.GENERATED_OUTSIDE_OF_TEE, spec.getOrigin());
        assertEquals(0, spec.getPurposes());
        assertEquals(null, spec.getMaxSecondsSinceUserAuthentication());
        assertEquals(null, spec.getMaxUsesPerBoot());
        assertEquals(null, spec.getMinSecondsBetweenOperations());
        assertEquals(Collections.emptySet(), spec.getUserAuthenticators());
        assertEquals(Collections.emptySet(), spec.getTeeBackedUserAuthenticators());
    }
}
