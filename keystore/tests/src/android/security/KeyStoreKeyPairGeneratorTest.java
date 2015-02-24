package android.security;

import android.test.AndroidTestCase;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

public class KeyStoreKeyPairGeneratorTest extends AndroidTestCase {
    private static final String KEYSTORE_KEY_ALIAS = KeyStoreKeyPairGeneratorTest.class.getName();
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
        } catch (KeyStoreException ignored) {
        } finally {
            super.tearDown();
        }
    }

    public void testRsa() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("RSAnew", "AndroidKeyStore");
        Date certNotBefore = new Date(System.currentTimeMillis() - 3600 * 1000);
        Date certNotAfter = new Date(System.currentTimeMillis() + 3600 * 96 * 1000);
        keyPairGenerator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setSubject(new X500Principal(
                        "CN=" + KeyStoreKeyPairGeneratorTest.class.getSimpleName()))
                .setSerialNumber(new BigInteger("1"))
                .setStartDate(certNotBefore)
                .setEndDate(certNotAfter)
                .build());
        System.out.println("*** Generating key pair...");
        long timeBefore = System.currentTimeMillis();
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        long timeAfter = System.currentTimeMillis();
        System.out.println("*** Generated " + keyPair.getPublic().getAlgorithm() + " key pair in "
                + (timeAfter - timeBefore) + " ms");
        PublicKey publicKey = keyPair.getPublic();
        System.out.println("*** Public key: " + publicKey.getAlgorithm() + ", class: "
                + publicKey.getClass().getName());
        PrivateKey privateKey = keyPair.getPrivate();
        System.out.println("*** Private key: " + privateKey.getAlgorithm() + ", class: "
                + privateKey.getClass().getName());
    }

    public void testEcdsa() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("ECnew", "AndroidKeyStore");
        Date certNotBefore = new Date(System.currentTimeMillis() - 3600 * 1000);
        Date certNotAfter = new Date(System.currentTimeMillis() + 3600 * 96 * 1000);
        keyPairGenerator.initialize(new KeyPairGeneratorSpec.Builder(getContext())
                .setAlias(KEYSTORE_KEY_ALIAS)
                .setSubject(new X500Principal(
                        "CN=" + KeyStoreKeyPairGeneratorTest.class.getSimpleName()))
                .setSerialNumber(new BigInteger("1"))
                .setStartDate(certNotBefore)
                .setEndDate(certNotAfter)
                .build());
        System.out.println("*** Generating key pair...");
        long timeBefore = System.currentTimeMillis();
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        long timeAfter = System.currentTimeMillis();
        System.out.println("*** Generated " + keyPair.getPublic().getAlgorithm() + " key pair in "
                + (timeAfter - timeBefore) + " ms");
        PublicKey publicKey = keyPair.getPublic();
        System.out.println("*** Public key: " + publicKey.getAlgorithm() + ", class: "
                + publicKey.getClass().getName());
        PrivateKey privateKey = keyPair.getPrivate();
        System.out.println("*** Private key: " + privateKey.getAlgorithm() + ", class: "
                + privateKey.getClass().getName());
    }
}
