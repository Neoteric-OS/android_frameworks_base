package android.security;

import java.security.spec.KeySpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * @hide
 */
public class KeyStoreKeySpec implements KeySpec {
    private final String mKeystoreAlias;
    private final Integer mKeySize;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final @KeyStoreKeyConstraints.PurposeEnum int mPurposes;
    private final @KeyStoreKeyConstraints.AlgorithmEnum Integer mAlgorithm;
    private final @KeyStoreKeyConstraints.PaddingEnum Integer mPadding;
    private final @KeyStoreKeyConstraints.DigestEnum Integer mDigest;
    private final @KeyStoreKeyConstraints.BlockModeEnum Integer mBlockMode;
    private final Integer mMinSecondsBetweenOperations;
    private final Integer mMaxUsesPerBoot;
    private final boolean mUserAuthenticationRequired;
    private final Set<Integer> mUserAuthenticators;
    private final Set<Integer> mTeeBackedUserAuthenticators;
    private final Integer mMaxSecondsSinceUserAuthentication;

    private final @KeyStoreKeyCharacteristics.OriginEnum int mOrigin;

    /**
     * @hide
     */
    KeyStoreKeySpec(String keystoreKeyAlias, Integer keySize, Date keyValidityStart,
            Date keyValidityForOriginationEnd, Date keyValidityForConsumptionEnd,
            @KeyStoreKeyConstraints.PurposeEnum int purposes,
            @KeyStoreKeyConstraints.AlgorithmEnum Integer algorithm,
            @KeyStoreKeyConstraints.PaddingEnum Integer padding,
            @KeyStoreKeyConstraints.DigestEnum Integer digest,
            @KeyStoreKeyConstraints.BlockModeEnum Integer blockMode,
            Integer minSecondsBetweenOperations,
            Integer maxUsesPerBoot,
            boolean userAuthenticationRequired,
            Set<Integer> userAuthenticators,
            Set<Integer> teeBackedUserAuthenticators,
            Integer maxSecondsSinceUserAuthentication,
            @KeyStoreKeyCharacteristics.OriginEnum int origin) {
        mKeystoreAlias = keystoreKeyAlias;
        mKeySize = keySize;
        mKeyValidityStart = keyValidityStart;
        mKeyValidityForOriginationEnd = keyValidityForOriginationEnd;
        mKeyValidityForConsumptionEnd = keyValidityForConsumptionEnd;
        mPurposes = purposes;
        mAlgorithm = algorithm;
        mPadding = padding;
        mDigest = digest;
        mBlockMode = blockMode;
        mMinSecondsBetweenOperations = minSecondsBetweenOperations;
        mMaxUsesPerBoot = maxUsesPerBoot;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticators = (userAuthenticators != null)
                ? new HashSet<Integer>(userAuthenticators)
                : Collections.<Integer>emptySet();
        mTeeBackedUserAuthenticators = (teeBackedUserAuthenticators != null)
                ? new HashSet<Integer>(teeBackedUserAuthenticators)
                : Collections.<Integer>emptySet();
        mMaxSecondsSinceUserAuthentication = maxSecondsSinceUserAuthentication;
        mOrigin = origin;
    }

    public String getKeystoreAlias() {
        return mKeystoreAlias;
    }

    public Integer getKeySize() {
        return mKeySize;
    }

    public Date getKeyValidityStart() {
        return mKeyValidityStart;
    }

    public Date getKeyValidityForConsumptionEnd() {
        return mKeyValidityForConsumptionEnd;
    }

    public Date getKeyValidityForOriginationEnd() {
        return mKeyValidityForOriginationEnd;
    }

    public @KeyStoreKeyConstraints.PurposeEnum int getPurposes() {
        return mPurposes;
    }

    public @KeyStoreKeyConstraints.AlgorithmEnum Integer getAlgorithm() {
        return mAlgorithm;
    }

    public @KeyStoreKeyConstraints.PaddingEnum Integer getPadding() {
        return mPadding;
    }

    public @KeyStoreKeyConstraints.DigestEnum Integer getDigest() {
        return mDigest;
    }

    public @KeyStoreKeyConstraints.BlockModeEnum Integer getBlockMode() {
        return mBlockMode;
    }

    public Integer getMinSecondsBetweenOperations() {
        return mMinSecondsBetweenOperations;
    }

    public Integer getMaxUsesPerBoot() {
        return mMaxUsesPerBoot;
    }

    public boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    public Set<Integer> getUserAuthenticators() {
        return new HashSet<Integer>(mUserAuthenticators);
    }

    public Set<Integer> getTeeBackedUserAuthenticators() {
        return new HashSet<Integer>(mTeeBackedUserAuthenticators);
    }

    public Integer getMaxSecondsSinceUserAuthentication() {
        return mMaxSecondsSinceUserAuthentication;
    }

    public @KeyStoreKeyCharacteristics.OriginEnum int getOrigin() {
        return mOrigin;
    }
}
