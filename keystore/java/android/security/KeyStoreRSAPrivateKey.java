package android.security;

import java.math.BigInteger;
import java.security.interfaces.RSAKey;

/**
 * @hide
 */
public class KeyStoreRSAPrivateKey extends KeyStorePrivateKey implements RSAKey {
    private final BigInteger mModulus;

    public KeyStoreRSAPrivateKey(String alias, BigInteger modulus) {
        super(alias, "RSA");
        mModulus = modulus;
    }

    @Override
    public BigInteger getModulus() {
        return mModulus;
    }
}
