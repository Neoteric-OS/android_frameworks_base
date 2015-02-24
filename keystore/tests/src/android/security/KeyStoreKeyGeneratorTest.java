package android.security;

import junit.framework.TestCase;

import java.security.Key;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeyStoreKeyGeneratorTest extends TestCase {
    private static final String KEYSTORE_KEY_ALIAS = KeyStoreKeyGeneratorTest.class.getName();
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

    public void testAes() throws Exception {
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance("AES", AndroidKeyStoreProvider.PROVIDER_NAME);
        keyGenerator.init(new KeyGeneratorSpec.Builder()
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setAlgorithm(KeyStoreKeyConstraints.Algorithm.AES)
                .setKeySize(64)
                .build());
        SecretKey key = keyGenerator.generateKey();
        assertEquals("AES", key.getAlgorithm());
        assertNotExportable(key);

        assertTrue(mAndroidKeyStore.containsAlias(KEYSTORE_KEY_ALIAS));
        key = (SecretKey) mAndroidKeyStore.getKey(KEYSTORE_KEY_ALIAS, null);
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertNotExportable(key);
    }

    public void testHmacSHA256() throws Exception {
        KeyGenerator keyGenerator =
                KeyGenerator.getInstance("HmacSHA256", AndroidKeyStoreProvider.PROVIDER_NAME);
        keyGenerator.init(new KeyGeneratorSpec.Builder()
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setAlgorithm(KeyStoreKeyConstraints.Algorithm.HMAC)
                .setKeySize(64)
                .build());
        SecretKey key = keyGenerator.generateKey();
        assertEquals("HmacSHA256", key.getAlgorithm());
        assertNotExportable(key);

        assertTrue(mAndroidKeyStore.containsAlias(KEYSTORE_KEY_ALIAS));
        key = (SecretKey) mAndroidKeyStore.getKey(KEYSTORE_KEY_ALIAS, null);
        assertNotNull(key);
        assertEquals("HmacSHA256", key.getAlgorithm());
        assertNotExportable(key);
    }

    public void testImportAES() throws Exception {
        mAndroidKeyStore.setEntry(
                KEYSTORE_KEY_ALIAS,
                new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[16], "AES")),
                null);
        SecretKey key = (SecretKey) mAndroidKeyStore.getKey(KEYSTORE_KEY_ALIAS, null);
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertNotExportable(key);
    }

    public void testImportHmacSHA256() throws Exception {
        mAndroidKeyStore.setEntry(
                KEYSTORE_KEY_ALIAS,
                new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "HmacSHA256")),
                null);
        SecretKey key = (SecretKey) mAndroidKeyStore.getKey(KEYSTORE_KEY_ALIAS, null);
        assertNotNull(key);
        assertEquals("HmacSHA256", key.getAlgorithm());
        assertNotExportable(key);
    }

    public void testImportHmacSHA1() throws Exception {
        mAndroidKeyStore.setEntry(
                KEYSTORE_KEY_ALIAS,
                new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "HmacSHA1")),
                null);
        SecretKey key = (SecretKey) mAndroidKeyStore.getKey(KEYSTORE_KEY_ALIAS, null);
        assertNotNull(key);
        assertEquals("HmacSHA1", key.getAlgorithm());
        assertNotExportable(key);
    }

    private static void assertNotExportable(Key key) {
        assertEquals(null, key.getFormat());
        assertEquals(null, key.getEncoded());
    }
}
