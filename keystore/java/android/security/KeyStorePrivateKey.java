package android.security;

import java.security.PrivateKey;

/**
 * @hide
 */
public class KeyStorePrivateKey implements PrivateKey {

    private final String mAlias;
    private final String mAlgorithm;

    protected KeyStorePrivateKey(String alias, String algorithm) {
        mAlias = alias;
        mAlgorithm = algorithm;
    }

    String getAlias() {
        return mAlias;
    }

    @Override
    public String getAlgorithm() {
        return mAlgorithm;
    }

    @Override
    public byte[] getEncoded() {
        // This key does not export its key material
        return null;
    }

    @Override
    public String getFormat() {
        // This key does not export its key material
        return null;
    }

}
