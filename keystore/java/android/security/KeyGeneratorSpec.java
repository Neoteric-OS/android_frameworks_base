package android.security;

import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * @hide
 */
public class KeyGeneratorSpec implements AlgorithmParameterSpec {

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
    private final Integer mMaxSecondsSinceUserAuthentication;
    private final int mFlags;

    /**
     * @hide
     */
    KeyGeneratorSpec(String keystoreKeyAlias, Integer keySize, Date keyValidityStart,
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
            Integer maxSecondsSinceUserAuthentication,
            int flags) {
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
        mMaxSecondsSinceUserAuthentication = maxSecondsSinceUserAuthentication;
        mFlags = flags;
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

    public Integer getMaxSecondsSinceUserAuthentication() {
        return mMaxSecondsSinceUserAuthentication;
    }

    public boolean isEncryptionRequired() {
        return (mFlags & KeyStore.FLAG_ENCRYPTED) != 0;
    }

    /**
     * @hide
     */
    int getFlags() {
        return mFlags;
    }

    public static class Builder {
        private String mKeystoreAlias;
        private Integer mKeySize;
        private Date mKeyValidityStart;
        private Date mKeyValidityForOriginationEnd;
        private Date mKeyValidityForConsumptionEnd;
        private @KeyStoreKeyConstraints.PurposeEnum int mPurposes;
        private @KeyStoreKeyConstraints.AlgorithmEnum Integer mAlgorithm;
        private @KeyStoreKeyConstraints.PaddingEnum Integer mPadding;
        private @KeyStoreKeyConstraints.DigestEnum Integer mDigest;
        private @KeyStoreKeyConstraints.BlockModeEnum Integer mBlockMode;
        private Integer mMinSecondsBetweenOperations;
        private Integer mMaxUsesPerBoot;
        private boolean mUserAuthenticationRequired;
        private Set<Integer> mUserAuthenticators;
        private Integer mMaxSecondsSinceUserAuthentication;
        private int mFlags;

        public Builder setAlias(String alias) {
            mKeystoreAlias = alias;
            return this;
        }

        public Builder setKeySize(int keySize) {
            mKeySize = keySize;
            return this;
        }

        public Builder setKeyValidityStart(Date startDate) {
            mKeyValidityStart = startDate;
            return this;
        }

        public Builder setKeyValidityForOriginationEnd(Date instant) {
            mKeyValidityForOriginationEnd = instant;
            return this;
        }

        public Builder setKeyValidityForConsumptionEnd(Date instant) {
            mKeyValidityForConsumptionEnd = instant;
            return this;
        }

        public Builder setPurposes(@KeyStoreKeyConstraints.PurposeEnum int purposes) {
            mPurposes = purposes;
            return this;
        }

        public Builder setAlgorithm(@KeyStoreKeyConstraints.AlgorithmEnum int algorithm) {
            mAlgorithm = algorithm;
            return this;
        }

        public Builder setPadding(@KeyStoreKeyConstraints.PaddingEnum int padding) {
            mPadding = padding;
            return this;
        }

        public Builder setDigest(@KeyStoreKeyConstraints.DigestEnum int digest) {
            mDigest = digest;
            return this;
        }

        public Builder setBlockMode(@KeyStoreKeyConstraints.BlockModeEnum int blockMode) {
            mBlockMode = blockMode;
            return this;
        }

        public Builder setMinSecondsBetweenOperations(int seconds) {
            mMinSecondsBetweenOperations = seconds;
            return this;
        }

        public Builder setMaxUsesPerBoot(int count) {
            mMaxUsesPerBoot = count;
            return this;
        }

        public Builder setUserAuthenticationRequired(boolean required) {
            mUserAuthenticationRequired = required;
            return this;
        }

        public Builder setUserAuthenticators(int... userAuthenticators) {
            Set<Integer> userAuthenticatorsSet = new HashSet<Integer>();
            for (int userAuthenticator : userAuthenticators) {
                userAuthenticatorsSet.add(userAuthenticator);
            }
            mUserAuthenticators = userAuthenticatorsSet;
            return this;
        }

        public Builder setMaxSecondsSinceUserAuthentication(int seconds) {
            mMaxSecondsSinceUserAuthentication = seconds;
            return this;
        }

        public Builder setEncryptionRequired(boolean required) {
            if (required) {
                mFlags |= KeyStore.FLAG_ENCRYPTED;
            } else {
                mFlags &= ~KeyStore.FLAG_ENCRYPTED;
            }
            return this;
        }

        public KeyGeneratorSpec build() {
            return new KeyGeneratorSpec(mKeystoreAlias, mKeySize, mKeyValidityStart,
                    mKeyValidityForOriginationEnd, mKeyValidityForConsumptionEnd, mPurposes,
                    mAlgorithm, mPadding, mDigest, mBlockMode, mMinSecondsBetweenOperations,
                    mMaxUsesPerBoot, mUserAuthenticationRequired, mUserAuthenticators,
                    mMaxSecondsSinceUserAuthentication, mFlags);
        }
    }
}
