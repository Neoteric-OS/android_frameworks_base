package android.security;

import android.security.keymaster.KeyCharacteristics;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;

import java.security.InvalidAlgorithmParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

/**
 * @hide
 */
public abstract class KeyStoreKeyGeneratorSpi extends KeyGeneratorSpi {

    public static class AES extends KeyStoreKeyGeneratorSpi {
        public AES() {
            super(KeyStoreKeyConstraints.Algorithm.AES, 128);
        }
    }

    public static class HmacSHA256 extends KeyStoreKeyGeneratorSpi {
        public HmacSHA256() {
            super(KeyStoreKeyConstraints.Algorithm.HMAC,
                    KeyStoreKeyConstraints.Digest.SHA256,
                    256);
        }
    }

    private final KeyStore mKeyStore = KeyStore.getInstance();
    private final @KeyStoreKeyConstraints.AlgorithmEnum int mAlgorithm;
    private final @KeyStoreKeyConstraints.AlgorithmEnum Integer mDigest;
    private final int mDefaultKeySizeBits;

    private KeyGeneratorSpec mSpec;
    private SecureRandom mRng;

    protected KeyStoreKeyGeneratorSpi(
            @KeyStoreKeyConstraints.AlgorithmEnum int algorithm,
            int defaultKeySizeBits) {
        this(algorithm, null, defaultKeySizeBits);
    }

    protected KeyStoreKeyGeneratorSpi(
            @KeyStoreKeyConstraints.AlgorithmEnum int algorithm,
            @KeyStoreKeyConstraints.DigestEnum Integer digest,
            int defaultKeySizeBits) {
        mAlgorithm = algorithm;
        mDigest = digest;
        mDefaultKeySizeBits = defaultKeySizeBits;
    }

    @Override
    protected SecretKey engineGenerateKey() {
        KeyGeneratorSpec spec = mSpec;
        if (spec == null) {
            throw new IllegalStateException("Not initialized");
        }

        if ((spec.isEncryptionRequired())
                && (mKeyStore.state() != KeyStore.State.UNLOCKED)) {
            throw new IllegalStateException(
                    "Android KeyStore must be in initialized and unlocked state if encryption is"
                    + " required");
        }

        KeymasterArguments args = new KeymasterArguments();
        args.addInt(KeymasterDefs.KM_TAG_ALGORITHM,
                KeyStoreKeyConstraints.Algorithm.toKeymaster(mAlgorithm));
        Integer digest = (mDigest != null) ? mDigest : spec.getDigest();
        if (digest != null) {
            args.addInt(KeymasterDefs.KM_TAG_DIGEST,
                    KeyStoreKeyConstraints.Digest.toKeymaster(digest));
        }
        int keySizeBits = (spec.getKeySize() != null) ? spec.getKeySize() : mDefaultKeySizeBits;
        args.addInt(KeymasterDefs.KM_TAG_KEY_SIZE, keySizeBits);
        for (int keymasterPurpose :
            KeyStoreKeyConstraints.Purpose.allToKeymaster(spec.getPurposes())) {
            args.addInt(KeymasterDefs.KM_TAG_PURPOSE, keymasterPurpose);
        }
        if (spec.getBlockMode() != null) {
            args.addInt(KeymasterDefs.KM_TAG_BLOCK_MODE,
                    KeyStoreKeyConstraints.BlockMode.toKeymaster(spec.getBlockMode()));
        }
        if (spec.getPadding() != null) {
            args.addInt(KeymasterDefs.KM_TAG_PADDING,
                    KeyStoreKeyConstraints.Padding.toKeymaster(spec.getPadding()));
        }
        if (spec.getMaxUsesPerBoot() != null) {
            args.addInt(KeymasterDefs.KM_TAG_MAX_USES_PER_BOOT, spec.getMaxUsesPerBoot());
        }
        if (spec.getMinSecondsBetweenOperations() != null) {
            args.addInt(KeymasterDefs.KM_TAG_MIN_SECONDS_BETWEEN_OPS,
                    spec.getMinSecondsBetweenOperations());
        }
        if (!spec.isUserAuthenticationRequired()) {
            args.addBoolean(KeymasterDefs.KM_TAG_NO_AUTH_REQUIRED);
        }
        if (spec.getUserAuthenticators() != null) {
            for (int userAuthenticatorId : spec.getUserAuthenticators()) {
                args.addInt(KeymasterDefs.KM_TAG_USER_AUTH_ID, userAuthenticatorId);
            }
        }
        if (spec.getMaxSecondsSinceUserAuthentication() != null) {
            args.addInt(KeymasterDefs.KM_TAG_AUTH_TIMEOUT,
                    spec.getMaxSecondsSinceUserAuthentication());
        }
        if (spec.getKeyValidityStart() != null) {
            args.addDate(KeymasterDefs.KM_TAG_ACTIVE_DATETIME, spec.getKeyValidityStart());
        }
        if (spec.getKeyValidityForOriginationEnd() != null) {
            args.addDate(KeymasterDefs.KM_TAG_ORIGINATION_EXPIRE_DATETIME,
                    spec.getKeyValidityForOriginationEnd());
        }
        if (spec.getKeyValidityForConsumptionEnd() != null) {
            args.addDate(KeymasterDefs.KM_TAG_USAGE_EXPIRE_DATETIME,
                    spec.getKeyValidityForConsumptionEnd());
        }

        // Permit caller-specified IV. This is needed due to the Cipher abstraction.
        args.addBoolean(KeymasterDefs.KM_TAG_CALLER_NONCE);

        // TODO: Remove this once Keymaster HAL is fixed
        args.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        args.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);

        // TODO: Remove this workaround for AEAD crypto once Keymaster HAL is fixed
        args.addInt(KeymasterDefs.KM_TAG_CHUNK_LENGTH, 1 << 30);
//        args.addInt(KeymasterDefs.KM_TAG_CHUNK_LENGTH, 0);
//        args.addBoolean(KeymasterDefs.KM_TAG_RETURN_UNAUTHED);
        args.addInt(KeymasterDefs.KM_TAG_MAC_LENGTH, keySizeBits / 8);

        // TODO: Mix in entropy once that works
//        SecureRandom rng = mRng;
//        if (rng != null) {
//            byte[] additionalEntropy = new byte[(keySizeBits + 7) / 8];
//            rng.nextBytes(additionalEntropy);
//            if (!mKeyStore.addRngEntropy(additionalEntropy)) {
//                throw new IllegalStateException(
//                        "Failed to mix in entropy from provided SecureRandom");
//            }
//        }

        int flags = spec.getFlags();
        String keyAliasInKeystore = Credentials.USER_SECRET_KEY + spec.getKeystoreAlias();
        int errorCode =
                mKeyStore.generateKey(keyAliasInKeystore, args, flags, new KeyCharacteristics());
        if ((errorCode != KeyStore.NO_ERROR) && (errorCode != KeymasterDefs.KM_ERROR_OK)) {
            throw new IllegalStateException("Failed to generate key. Error code: " + errorCode);
        }
        String keyAlgorithmJCA =
                KeyStoreKeyConstraints.Algorithm.toJCASecretKeyAlgorithm(mAlgorithm, digest);
        return new KeyStoreSecretKey(keyAliasInKeystore, keyAlgorithmJCA);
    }

    @Override
    protected void engineInit(SecureRandom random) {
        throw new UnsupportedOperationException("Cannot initialize without an "
                + KeyGeneratorSpec.class.getName() + " parameter");
    }

    @Override
    protected void engineInit(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if ((params == null) || (!(params instanceof KeyGeneratorSpec))) {
            throw new InvalidAlgorithmParameterException("Cannot initialize without an "
                    + KeyGeneratorSpec.class.getName() + " parameter");
        }
        KeyGeneratorSpec spec = (KeyGeneratorSpec) params;
        if (spec.getKeystoreAlias() == null) {
            throw new InvalidAlgorithmParameterException("KeyStore entry alias not provided");
        }
        if (spec.getAlgorithm() != null) {
            // Key algorithm explicitly specified in the spec -- check that it matches this
            // generator's key algorithm
            if (spec.getAlgorithm() != mAlgorithm) {
                throw new InvalidAlgorithmParameterException("Algorithm mismatch."
                        + " KeyGenerator: " + KeyStoreKeyConstraints.Algorithm.toString(mAlgorithm)
                        + ", parameter spec: "
                        + KeyStoreKeyConstraints.Algorithm.toString(spec.getAlgorithm()));
            }
        }
        if (spec.getDigest() != null) {
            // Key usage digest explicitly specified in the spec. If this generator's algorithm name
            // specifies a digest, check that they match.
            if ((mDigest != null) && (spec.getDigest() != mDigest)) {
                throw new InvalidAlgorithmParameterException("Digest mismatch."
                        + " KeyGenerator: " + KeyStoreKeyConstraints.Digest.toString(mDigest)
                        + ", parameter spec: "
                        + KeyStoreKeyConstraints.Digest.toString(spec.getDigest()));
            }
        }

        mSpec = spec;
        mRng = random;
    }

    @Override
    protected void engineInit(int keySize, SecureRandom random) {
        throw new UnsupportedOperationException("Cannot initialize without a "
                + KeyGeneratorSpec.class.getName() + " parameter");
    }
}
