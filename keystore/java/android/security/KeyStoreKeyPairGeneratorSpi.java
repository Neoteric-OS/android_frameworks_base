package android.security;

import android.security.keymaster.ExportResult;
import android.security.keymaster.KeyCharacteristics;
import android.security.keymaster.KeymasterArguments;
import android.security.keymaster.KeymasterDefs;

import com.android.org.bouncycastle.x509.X509V3CertificateGenerator;
import com.android.org.conscrypt.OpenSSLEngine;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGeneratorSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @hide
 */
public abstract class KeyStoreKeyPairGeneratorSpi extends KeyPairGeneratorSpi {

    public static final class RSA extends KeyStoreKeyPairGeneratorSpi {
        public RSA() {
            super("RSA", 2048);
        }
    }

    public static final class EC extends KeyStoreKeyPairGeneratorSpi {
        public EC() {
            super("EC", 256);
        }
    }

    private final KeyStore mKeyStore = KeyStore.getInstance();
    private final String mJCAAlgorithm;
    private final int mDefaultKeySizeBits;

    private SecureRandom mRng;
    private KeyPairGeneratorSpec mSpec;
    private @KeyStoreKeyConstraints.AlgorithmEnum int mAlgorithm;
    private int mKeySizeBits;

    protected KeyStoreKeyPairGeneratorSpi(String jcaAlgorithm, int defaultKeySizeBits) {
        mJCAAlgorithm = jcaAlgorithm;
        mDefaultKeySizeBits = defaultKeySizeBits;
    }

    @Override
    public KeyPair generateKeyPair() {
        KeyPairGeneratorSpec spec = mSpec;
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
        args.addInt(KeymasterDefs.KM_TAG_KEY_SIZE, mKeySizeBits);
        for (int keymasterPurpose :
            KeyStoreKeyConstraints.Purpose.allToKeymaster(spec.getPurposes())) {
            args.addInt(KeymasterDefs.KM_TAG_PURPOSE, keymasterPurpose);
        }
        if (spec.getDigest() != null) {
            args.addInt(KeymasterDefs.KM_TAG_DIGEST,
                    KeyStoreKeyConstraints.Digest.toKeymaster(spec.getDigest()));
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

        // Add algorithm-specific arguments.
        switch (mAlgorithm) {
            case KeyStoreKeyConstraints.Algorithm.RSA:
                if (spec.getAlgorithmParameterSpec() instanceof RSAKeyGenParameterSpec) {
                    RSAKeyGenParameterSpec rsaSpec =
                            (RSAKeyGenParameterSpec) spec.getAlgorithmParameterSpec();
                    args.addBlob(KeymasterDefs.KM_TAG_RSA_PUBLIC_EXPONENT,
                            rsaSpec.getPublicExponent().toByteArray());
                }
                break;
        }

        // TODO: Remove this once Keymaster HAL is fixed
        args.addBlob(KeymasterDefs.KM_TAG_APPLICATION_DATA, null);
        args.addBlob(KeymasterDefs.KM_TAG_APPLICATION_ID, null);

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
        String keyAliasInKeystore = Credentials.USER_PRIVATE_KEY + spec.getKeystoreAlias();
        int errorCode =
                mKeyStore.generateKey(keyAliasInKeystore, args, flags, new KeyCharacteristics());
        if ((errorCode != KeyStore.NO_ERROR) && (errorCode != KeymasterDefs.KM_ERROR_OK)) {
            throw new IllegalStateException("Failed to generate key. Error code: " + errorCode);
        }

        ExportResult exportResult = mKeyStore.exportKey(
                keyAliasInKeystore, KeymasterDefs.KM_KEY_FORMAT_x509, null, null);
        if (exportResult == null) {
            throw new IllegalStateException("Keystore service crashed");
        }
        errorCode = exportResult.resultCode;
        if ((errorCode != KeyStore.NO_ERROR) && (errorCode != KeymasterDefs.KM_ERROR_OK)) {
            throw new IllegalStateException(
                    "Failed to obtain public key. Error code: " + errorCode);
        }
        Credentials.deleteSecretKeyTypeForAlias(mKeyStore, spec.getKeystoreAlias());
        Credentials.deleteCertificateTypesForAlias(mKeyStore, spec.getKeystoreAlias());

        String jcaKeyAlgorithm = KeyStoreKeyConstraints.Algorithm.toJCAKeyPairAlgorithm(mAlgorithm);
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(jcaKeyAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to obtain " + jcaKeyAlgorithm + " KeyFactory", e);
        }
        PublicKey publicKey;
        try {
            publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(exportResult.exportData));
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(
                    "Failed to generate " + jcaKeyAlgorithm + " PublicKey from X.509 form", e);
        }

        KeyStorePrivateKey privateKey;
        if ("RSA".equals(jcaKeyAlgorithm)) {
            BigInteger modulus;
            if (publicKey instanceof RSAKey) {
                modulus = ((RSAKey) publicKey).getModulus();
            } else {
                RSAPublicKeySpec keySpec;
                try {
                    keySpec = keyFactory.getKeySpec(publicKey, RSAPublicKeySpec.class);
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException(
                            "Failed to obtain RSAPublicKeySpec for key: " + publicKey, e);
                }
                modulus = keySpec.getModulus();
            }
            privateKey = new KeyStoreRSAPrivateKey(spec.getKeystoreAlias(), modulus);
        } else if ("EC".equals(jcaKeyAlgorithm)) {
            ECParameterSpec ecParameterSpec;
            if (publicKey instanceof ECKey) {
                ecParameterSpec = ((ECKey) publicKey).getParams();
            } else {
                ECPublicKeySpec keySpec;
                try {
                    keySpec = keyFactory.getKeySpec(publicKey, ECPublicKeySpec.class);
                } catch (InvalidKeySpecException e) {
                    throw new IllegalStateException(
                            "Failed to obtain ECPublicKeySpec for key: " + publicKey, e);
                }
                ecParameterSpec = keySpec.getParams();
            }
            privateKey = new KeyStoreECPrivateKey(spec.getKeystoreAlias(), ecParameterSpec);
        } else {
            throw new RuntimeException("Unsupported key algorithm: " + jcaKeyAlgorithm);
        }

        // TODO: Skip loading the OpenSSL-backed private key once AndroidKeyStore offers a Signature
        // implementation.
//        final PrivateKey openSslPrivateKey;
//        final OpenSSLEngine engine = OpenSSLEngine.getInstance("keystore");
//        try {
//            openSslPrivateKey = engine.getPrivateKeyById(privateKey.getAlias());
//        } catch (InvalidKeyException e) {
//            throw new RuntimeException("Can't get OpenSSL-backed private key", e);
//        }
//
//        final X509Certificate cert;
//        try {
//            cert = generateCertificate(openSslPrivateKey, publicKey);
//        } catch (Exception e) {
//            Credentials.deleteAllTypesForAlias(mKeyStore, spec.getKeystoreAlias());
//            throw new IllegalStateException("Can't generate certificate", e);
//        }
//
//        byte[] certBytes;
//        try {
//            certBytes = cert.getEncoded();
//        } catch (CertificateEncodingException e) {
//            Credentials.deleteAllTypesForAlias(mKeyStore, spec.getKeystoreAlias());
//            throw new IllegalStateException("Can't get encoding of certificate", e);
//        }
//
//        if (!mKeyStore.put(Credentials.USER_CERTIFICATE + spec.getKeystoreAlias(), certBytes,
//                KeyStore.UID_SELF, mSpec.getFlags())) {
//            Credentials.deleteAllTypesForAlias(mKeyStore, spec.getKeystoreAlias());
//            throw new IllegalStateException("Can't store certificate in AndroidKeyStore");
//        }

        return new KeyPair(publicKey, privateKey);
    }

    @SuppressWarnings("deprecation")
    private X509Certificate generateCertificate(PrivateKey privateKey, PublicKey publicKey)
            throws Exception {
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        certGen.setPublicKey(publicKey);
        certGen.setSerialNumber(mSpec.getSerialNumber());
        certGen.setSubjectDN(mSpec.getSubjectDN());
        certGen.setIssuerDN(mSpec.getSubjectDN());
        certGen.setNotBefore(mSpec.getStartDate());
        certGen.setNotAfter(mSpec.getEndDate());
        certGen.setSignatureAlgorithm(getJCASignatureAlgorithmForCertificate());
        return certGen.generate(privateKey);
    }

    private String getJCASignatureAlgorithmForCertificate() {
        String digest;
        if ((mSpec.getDigest() != null)
                && (mSpec.getDigest() != KeyStoreKeyConstraints.Digest.NONE)) {
            // Key is constrained to a particular digest -- use that digest for signing the
            // certificate.
            digest = KeyStoreKeyConstraints.Digest.toJCASignatureAlgorithmDigest(mSpec.getDigest());
        } else {
            // Any digest can be used with the key -- use SHA-256 for signing the certificate.
            digest = "SHA256";
        }
        switch (mAlgorithm) {
            case KeyStoreKeyConstraints.Algorithm.RSA:
                return digest + "withRSA";
            case KeyStoreKeyConstraints.Algorithm.ECDSA:
                return digest + "withECDSA";
            default:
                throw new RuntimeException("Unexpected key algorithm: " + mAlgorithm);
        }
    }

    @Override
    public void initialize(int keysize, SecureRandom random) {
        throw new IllegalArgumentException("Must initialize with KeyPairGeneratorSpec");
    }

    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (params == null) {
            throw new InvalidAlgorithmParameterException(
                    "must supply params of type android.security.KeyPairGeneratorSpec");
        } else if (!(params instanceof KeyPairGeneratorSpec)) {
            throw new InvalidAlgorithmParameterException(
                    "params must be of type android.security.KeyPairGeneratorSpec");
        }

        KeyPairGeneratorSpec spec = (KeyPairGeneratorSpec) params;
        if (spec.getKeystoreAlias() == null) {
            throw new InvalidAlgorithmParameterException(
                    "Key alias not specified in KeyPairGeneratorSpec");
        }

        if (spec.getAlgorithm() != null) {
            mAlgorithm = spec.getAlgorithm();
        } else {
            // Use the legacy JCA key type from spec or key pair factory type to determine key
            // algorithm.
            String jcaKeyAlgorithm = spec.getKeyType();
            if (jcaKeyAlgorithm == null) {
                jcaKeyAlgorithm = mJCAAlgorithm;
            }
            if ("RSA".equals(jcaKeyAlgorithm)) {
                mAlgorithm = KeyStoreKeyConstraints.Algorithm.RSA;
            } else if ("EC".equals(jcaKeyAlgorithm)) {
                // Prior for Android M only ECDSA was supported for "EC" key algorithm.
                mAlgorithm = KeyStoreKeyConstraints.Algorithm.ECDSA;
            } else {
                throw new InvalidAlgorithmParameterException(
                        "Unsupported key algoritm: " + jcaKeyAlgorithm);
            }
        }

        // Check whether the algorithm-specific AlgorithmParameterSpec (if provided) is suitable for
        // the key algorithm.
        if (spec.getAlgorithmParameterSpec() != null) {
            switch (mAlgorithm) {
                case KeyStoreKeyConstraints.Algorithm.RSA:
                    if (!(spec.getAlgorithmParameterSpec() instanceof RSAKeyGenParameterSpec)) {
                        throw new InvalidAlgorithmParameterException(
                                "RSA may only use RSAKeyGenParameterSpec: "
                                        + spec.getAlgorithmParameterSpec().getClass().getName());
                    }
                    break;
            }
        }

        mKeySizeBits = mDefaultKeySizeBits;
        if (spec.getKeySize() != -1) {
            mKeySizeBits = mDefaultKeySizeBits;
        } else if (spec.getAlgorithmParameterSpec() != null) {
            switch (mAlgorithm) {
                case KeyStoreKeyConstraints.Algorithm.RSA:
                    if (spec.getAlgorithmParameterSpec() instanceof RSAKeyGenParameterSpec) {
                        RSAKeyGenParameterSpec rsaSpec =
                                (RSAKeyGenParameterSpec) spec.getAlgorithmParameterSpec();
                        mKeySizeBits = rsaSpec.getKeysize();
                    }
                    break;
            }
        }

        mSpec = spec;
        mRng = random;
    }
}
