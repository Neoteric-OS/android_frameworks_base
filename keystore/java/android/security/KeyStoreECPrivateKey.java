package android.security;

import java.security.interfaces.ECKey;
import java.security.spec.ECParameterSpec;

/**
 * @hide
 */
public class KeyStoreECPrivateKey extends KeyStorePrivateKey implements ECKey {
    private final ECParameterSpec mParameterSpec;

    public KeyStoreECPrivateKey(String alias, ECParameterSpec ecParameterSpec) {
        super(alias, "EC");
        mParameterSpec = ecParameterSpec;
    }

    @Override
    public ECParameterSpec getParams() {
        return mParameterSpec;
    }
}
