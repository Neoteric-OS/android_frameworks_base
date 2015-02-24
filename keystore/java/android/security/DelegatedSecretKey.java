package android.security;

import javax.crypto.SecretKey;

/**
 * @hide
 */
class DelegatedSecretKey implements SecretKey {

    private final String mAlias;
    private final SecretKey mDelegate;

    DelegatedSecretKey(String alias, SecretKey delegate) {
        mAlias = alias;
        mDelegate = delegate;
    }

    SecretKey getDelegate() {
        return mDelegate;
    }

    String getAlias() {
        return mAlias;
    }

    @Override
    public String getAlgorithm() {
        return mDelegate.getAlgorithm();
    }

    @Override
    public String getFormat() {
        // This key does not export its key material
        return null;
    }

    @Override
    public byte[] getEncoded() {
        // This key does not export its key material
        return null;
    }
}
