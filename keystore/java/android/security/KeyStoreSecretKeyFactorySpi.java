package android.security;

import android.security.keymaster.KeyCharacteristics;
import android.security.keymaster.KeymasterDefs;

import java.security.InvalidKeyException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactorySpi;
import javax.crypto.spec.SecretKeySpec;

/**
 * @hide
 */
public class KeyStoreSecretKeyFactorySpi extends SecretKeyFactorySpi {

    private final KeyStore mKeyStore = KeyStore.getInstance();

    @Override
    protected KeySpec engineGetKeySpec(SecretKey key,
            @SuppressWarnings("rawtypes") Class keySpecClass) throws InvalidKeySpecException {
        if (keySpecClass == null) {
            throw new InvalidKeySpecException("keySpecClass == null");
        }
        if (!(key instanceof KeyStoreSecretKey)) {
            throw new InvalidKeySpecException("Only Android KeyStore secret keys supported: " +
                    ((key != null) ? key.getClass().getName() : "null"));
        }
        if (SecretKeySpec.class.isAssignableFrom(keySpecClass)) {
            throw new InvalidKeySpecException(
                    "Key material export of Android KeyStore keys is not supported");
        }
        if (!KeyStoreKeySpec.class.equals(keySpecClass)) {
            throw new InvalidKeySpecException("Unsupported key spec: " + keySpecClass.getName());
        }
        String keyAliasInKeystore = ((KeyStoreSecretKey) key).getAlias();
        KeyCharacteristics keyCharacteristics = new KeyCharacteristics();
        int errorCode =
                mKeyStore.getKeyCharacteristics(keyAliasInKeystore, null, null, keyCharacteristics);
        if ((errorCode != KeymasterDefs.KM_ERROR_OK) && (errorCode != KeyStore.NO_ERROR)) {
            throw new InvalidKeySpecException("Failed to obtain information about key."
                    + " Keystore error: " + errorCode);
        }
        String entryAlias;
        if (keyAliasInKeystore.startsWith(Credentials.USER_SECRET_KEY)) {
            entryAlias = keyAliasInKeystore.substring(Credentials.USER_SECRET_KEY.length());
        } else if (keyAliasInKeystore.startsWith(Credentials.USER_PRIVATE_KEY)) {
            entryAlias = keyAliasInKeystore.substring(Credentials.USER_PRIVATE_KEY.length());
        } else {
            throw new InvalidKeySpecException("Invalid key alias: " + keyAliasInKeystore);
        }

        @KeyStoreKeyConstraints.PurposeEnum int purposes;
        @KeyStoreKeyConstraints.AlgorithmEnum Integer algorithm;
        @KeyStoreKeyConstraints.PaddingEnum Integer padding;
        @KeyStoreKeyConstraints.DigestEnum Integer digest;
        @KeyStoreKeyConstraints.BlockModeEnum Integer blockMode;
        @KeyStoreKeyCharacteristics.OriginEnum Integer origin;
        try {
            purposes = KeyStoreKeyConstraints.Purpose.allFromKeymaster(
                    getInts(keyCharacteristics, KeymasterDefs.KM_TAG_PURPOSE));
            algorithm = getInt(keyCharacteristics, KeymasterDefs.KM_TAG_ALGORITHM);
            if (algorithm != null) {
                algorithm = KeyStoreKeyConstraints.Algorithm.fromKeymaster(algorithm);
            }
            padding = getInt(keyCharacteristics, KeymasterDefs.KM_TAG_PADDING);
            if (padding != null) {
                padding = KeyStoreKeyConstraints.Padding.fromKeymaster(padding);
            }
            digest = getInt(keyCharacteristics, KeymasterDefs.KM_TAG_DIGEST);
            if (digest != null) {
                digest = KeyStoreKeyConstraints.Digest.fromKeymaster(digest);
            }
            blockMode = getInt(keyCharacteristics, KeymasterDefs.KM_TAG_BLOCK_MODE);
            if (blockMode != null) {
                blockMode = KeyStoreKeyConstraints.BlockMode.fromKeymaster(blockMode);
            }
            origin = getInt(keyCharacteristics, KeymasterDefs.KM_TAG_ORIGIN);
            if (origin == null) {
                throw new InvalidKeySpecException("Key origin information not available");
            }
            origin = KeyStoreKeyCharacteristics.Origin.fromKeymaster(origin);
        } catch (IllegalArgumentException e) {
            throw new InvalidKeySpecException("Unsupported key characteristic", e);
        }

        Set<Integer> userAuthenticators = new HashSet<Integer>(
                getInts(keyCharacteristics, KeymasterDefs.KM_TAG_USER_AUTH_ID));
        Set<Integer> teeBackedUserAuthenticators = new HashSet<Integer>(
                keyCharacteristics.hwEnforced.getInts(KeymasterDefs.KM_TAG_USER_AUTH_ID));

        return new KeyStoreKeySpec(entryAlias,
                getInt(keyCharacteristics, KeymasterDefs.KM_TAG_KEY_SIZE),
                getDate(keyCharacteristics, KeymasterDefs.KM_TAG_ACTIVE_DATETIME),
                getDate(keyCharacteristics, KeymasterDefs.KM_TAG_ORIGINATION_EXPIRE_DATETIME),
                getDate(keyCharacteristics, KeymasterDefs.KM_TAG_USAGE_EXPIRE_DATETIME),
                purposes,
                algorithm,
                padding,
                digest,
                blockMode,
                getInt(keyCharacteristics, KeymasterDefs.KM_TAG_MIN_SECONDS_BETWEEN_OPS),
                getInt(keyCharacteristics, KeymasterDefs.KM_TAG_MAX_USES_PER_BOOT),
                !getBoolean(keyCharacteristics, KeymasterDefs.KM_TAG_NO_AUTH_REQUIRED),
                userAuthenticators,
                teeBackedUserAuthenticators,
                getInt(keyCharacteristics, KeymasterDefs.KM_TAG_AUTH_TIMEOUT),
                origin);
    }

    @Override
    protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
        throw new UnsupportedOperationException(
                "Key import into Android KeyStore is not supported");
    }

    @Override
    protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
        throw new UnsupportedOperationException(
                "Key import into Android KeyStore is not supported");
    }

    private static Integer getInt(KeyCharacteristics keyCharacteristics, int tag) {
        if (keyCharacteristics.hwEnforced.containsTag(tag)) {
            return keyCharacteristics.hwEnforced.getInt(tag, -1);
        } else if (keyCharacteristics.swEnforced.containsTag(tag)) {
            return keyCharacteristics.swEnforced.getInt(tag, -1);
        } else {
            return null;
        }
    }

    private static List<Integer> getInts(KeyCharacteristics keyCharacteristics, int tag) {
        List<Integer> result = new ArrayList<Integer>();
        result.addAll(keyCharacteristics.hwEnforced.getInts(tag));
        result.addAll(keyCharacteristics.swEnforced.getInts(tag));
        return result;
    }

    private static Date getDate(KeyCharacteristics keyCharacteristics, int tag) {
        Date result = keyCharacteristics.hwEnforced.getDate(tag, null);
        if (result == null) {
            result = keyCharacteristics.swEnforced.getDate(tag, null);
        }
        return result;
    }

    private static boolean getBoolean(KeyCharacteristics keyCharacteristics, int tag) {
        if (keyCharacteristics.hwEnforced.containsTag(tag)) {
            return keyCharacteristics.hwEnforced.getBoolean(tag, false);
        } else {
            return keyCharacteristics.swEnforced.getBoolean(tag, false);
        }
    }
}
