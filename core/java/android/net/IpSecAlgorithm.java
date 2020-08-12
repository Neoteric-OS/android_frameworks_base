/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.net;

import android.annotation.NonNull;
import android.annotation.StringDef;
import android.content.res.Resources;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.HexDump;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class represents a single algorithm that can be used by an {@link IpSecTransform}.
 *
 * @see <a href="https://tools.ietf.org/html/rfc4301">RFC 4301, Security Architecture for the
 * Internet Protocol</a>
 */
public final class IpSecAlgorithm implements Parcelable {
    private static final String TAG = "IpSecAlgorithm";

    /**
     * Null cipher.
     *
     * @hide
     */
    public static final String CRYPT_NULL = "ecb(cipher_null)";

    /**
     * AES-CBC Encryption/Ciphering Algorithm.
     *
     * <p>Valid lengths for this key are {128, 192, 256}.
     */
    public static final String CRYPT_AES_CBC = "cbc(aes)";

    /**
     * AES-CTR Encryption/Ciphering Algorithm.
     *
     * <p>Valid lengths for keying material are {160, 224, 288}.
     *
     * <p>As per <a href="https://tools.ietf.org/html/rfc3686#section-5.1">RFC3686 (Section
     * 5.1)</a>, keying material consists of a 128, 192, or 256 bit AES key followed by a 32-bit
     * nonce. RFC compliance requires that the nonce must be unique per security association.
     *
     * <p>This algorithm may not be supported on old devices. Caller MUST check if it is supported
     * before using it.
     */
    public static final String CRYPT_AES_CTR = "rfc3686(ctr(aes))";

    /**
     * MD5 HMAC Authentication/Integrity Algorithm. <b>This algorithm is not recommended for use in
     * new applications and is provided for legacy compatibility with 3gpp infrastructure.</b>
     *
     * <p>Keys for this algorithm must be 128 bits in length.
     *
     * <p>Valid truncation lengths are multiples of 8 bits from 96 to 128.
     */
    public static final String AUTH_HMAC_MD5 = "hmac(md5)";

    /**
     * SHA1 HMAC Authentication/Integrity Algorithm. <b>This algorithm is not recommended for use in
     * new applications and is provided for legacy compatibility with 3gpp infrastructure.</b>
     *
     * <p>Keys for this algorithm must be 160 bits in length.
     *
     * <p>Valid truncation lengths are multiples of 8 bits from 96 to 160.
     */
    public static final String AUTH_HMAC_SHA1 = "hmac(sha1)";

    /**
     * SHA256 HMAC Authentication/Integrity Algorithm.
     *
     * <p>Keys for this algorithm must be 256 bits in length.
     *
     * <p>Valid truncation lengths are multiples of 8 bits from 96 to 256.
     */
    public static final String AUTH_HMAC_SHA256 = "hmac(sha256)";

    /**
     * SHA384 HMAC Authentication/Integrity Algorithm.
     *
     * <p>Keys for this algorithm must be 384 bits in length.
     *
     * <p>Valid truncation lengths are multiples of 8 bits from 192 to 384.
     */
    public static final String AUTH_HMAC_SHA384 = "hmac(sha384)";

    /**
     * SHA512 HMAC Authentication/Integrity Algorithm.
     *
     * <p>Keys for this algorithm must be 512 bits in length.
     *
     * <p>Valid truncation lengths are multiples of 8 bits from 256 to 512.
     */
    public static final String AUTH_HMAC_SHA512 = "hmac(sha512)";

    /**
     * AES-XCBC Authentication/Integrity Algorithm.
     *
     * <p>Keys for this algorithm must be 128 bits in length.
     *
     * <p>Valid truncation lengths must be 96 bits in length.
     *
     * <p>This algorithm may not be supported on old devices. Caller MUST check if it is supported
     * before using it.
     */
    public static final String AUTH_AES_XCBC = "xcbc(aes)";

    /**
     * AES-GCM Authentication/Integrity + Encryption/Ciphering Algorithm.
     *
     * <p>Valid lengths for keying material are {160, 224, 288}.
     *
     * <p>As per <a href="https://tools.ietf.org/html/rfc4106#section-8.1">RFC4106 (Section
     * 8.1)</a>, keying material consists of a 128, 192, or 256 bit AES key followed by a 32-bit
     * salt. RFC compliance requires that the salt must be unique per invocation with the same key.
     *
     * <p>Valid ICV (truncation) lengths are {64, 96, 128}.
     */
    public static final String AUTH_CRYPT_AES_GCM = "rfc4106(gcm(aes))";

    /**
     * CHACHA20-POLY1305 Authentication/Integrity + Encryption/Ciphering Algorithm.
     *
     * <p>Keys for this algorithm must be 288 bits in length.
     *
     * <p>As per <a href="https://tools.ietf.org/html/rfc7634#section-2">RFC7634 (Section 2)</a>,
     * keying material consists of a 256 bit key followed by a 32-bit salt. The salt is fixed per
     * security association.
     *
     * <p>Valid ICV (truncation) must be 128 bits in length.
     *
     * <p>This algorithm may not be supported on old devices. Caller MUST check if it is supported
     * before using it.
     */
    public static final String AUTH_CRYPT_CHACHA20_POLY1305 = "rfc7539esp(chacha20,poly1305)";

    /** @hide */
    @StringDef({
        CRYPT_AES_CBC,
        CRYPT_AES_CTR,
        AUTH_HMAC_MD5,
        AUTH_HMAC_SHA1,
        AUTH_HMAC_SHA256,
        AUTH_HMAC_SHA384,
        AUTH_HMAC_SHA512,
        AUTH_AES_XCBC,
        AUTH_CRYPT_AES_GCM,
        AUTH_CRYPT_CHACHA20_POLY1305
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AlgorithmName {}

    private static final List<String> MANDATORY_FOR_ALL_KERNEL = new ArrayList<>();
    private static final List<String> MANDATORY_SINCE_S_KERNEL = new ArrayList<>();

    static {
        MANDATORY_FOR_ALL_KERNEL.add(CRYPT_AES_CBC);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_HMAC_MD5);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_HMAC_SHA1);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_HMAC_SHA256);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_HMAC_SHA384);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_HMAC_SHA512);
        MANDATORY_FOR_ALL_KERNEL.add(AUTH_CRYPT_AES_GCM);

        MANDATORY_SINCE_S_KERNEL.add(CRYPT_AES_CTR);
        MANDATORY_SINCE_S_KERNEL.add(AUTH_AES_XCBC);
        MANDATORY_SINCE_S_KERNEL.add(AUTH_CRYPT_CHACHA20_POLY1305);
    }

    private final String mName;
    private final byte[] mKey;
    private final int mTruncLenBits;

    /**
     * Creates an IpSecAlgorithm of one of the supported types. Supported algorithm names are
     * defined as constants in this class.
     *
     * <p>For algorithms that produce an integrity check value, the truncation length is a required
     * parameter. See {@link #IpSecAlgorithm(String algorithm, byte[] key, int truncLenBits)}
     *
     * @param algorithm name of the algorithm.
     * @param key key padded to a multiple of 8 bits.
     */
    public IpSecAlgorithm(@NonNull @AlgorithmName String algorithm, @NonNull byte[] key) {
        this(algorithm, key, 0);
    }

    /**
     * Creates an IpSecAlgorithm of one of the supported types. Supported algorithm names are
     * defined as constants in this class.
     *
     * <p>This constructor only supports algorithms that use a truncation length. i.e.
     * Authentication and Authenticated Encryption algorithms.
     *
     * @param algorithm name of the algorithm.
     * @param key key padded to a multiple of 8 bits.
     * @param truncLenBits number of bits of output hash to use.
     */
    public IpSecAlgorithm(
            @NonNull @AlgorithmName String algorithm, @NonNull byte[] key, int truncLenBits) {
        mName = algorithm;
        mKey = key.clone();
        mTruncLenBits = truncLenBits;
        checkValidOrThrow(mName, mKey.length * 8, mTruncLenBits);
    }

    /** Get the algorithm name */
    @NonNull
    public String getName() {
        return mName;
    }

    /** Get the key for this algorithm */
    @NonNull
    public byte[] getKey() {
        return mKey.clone();
    }

    /** Get the truncation length of this algorithm, in bits */
    public int getTruncationLengthBits() {
        return mTruncLenBits;
    }

    /* Parcelable Implementation */
    public int describeContents() {
        return 0;
    }

    /** Write to parcel */
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeByteArray(mKey);
        out.writeInt(mTruncLenBits);
    }

    /** Parcelable Creator */
    public static final @android.annotation.NonNull Parcelable.Creator<IpSecAlgorithm> CREATOR =
            new Parcelable.Creator<IpSecAlgorithm>() {
                public IpSecAlgorithm createFromParcel(Parcel in) {
                    final String name = in.readString();
                    final byte[] key = in.createByteArray();
                    final int truncLenBits = in.readInt();

                    return new IpSecAlgorithm(name, key, truncLenBits);
                }

                public IpSecAlgorithm[] newArray(int size) {
                    return new IpSecAlgorithm[size];
                }
            };

    /**
     * Returns supported IPsec algorithms.
     *
     * <p>Some algorithms may not be supported on old devices. Callers MUST check if an algorithm is
     * supported before using it.
     */
    public static @NonNull List<String> getSupportedAlgorithms() {
        List<String> algoList = new ArrayList<>();
        algoList.addAll(MANDATORY_FOR_ALL_KERNEL);

        if (Build.VERSION.FIRST_SDK_INT > Build.VERSION_CODES.R) {
            algoList.addAll(MANDATORY_SINCE_S_KERNEL);
        }

        algoList.addAll(loadOptionalAlgos());
        return algoList;
    }

    private static Set<String> loadOptionalAlgos() {
        String[] resourceAlgos =
                Resources.getSystem()
                        .getStringArray(
                                com.android.internal.R.array.config_optionalIpSecAlgorithms);

        Set<String> optionalAlgoAllowSet = new HashSet<>();
        if (Build.VERSION.FIRST_SDK_INT <= Build.VERSION_CODES.R) {
            optionalAlgoAllowSet.addAll(MANDATORY_SINCE_S_KERNEL);
        }

        // Validate resource.
        Set<String> enabledOptionalAlgoSet = new HashSet<>();
        for (String str : resourceAlgos) {
            if (!optionalAlgoAllowSet.contains(str) || enabledOptionalAlgoSet.contains(str)) {
                // This error should be caught by CTS and never be thrown to API callers
                throw new IllegalArgumentException("Invalid or repeated optional algorithm found");
            }
            enabledOptionalAlgoSet.add(str);
        }

        return enabledOptionalAlgoSet;
    }

    private static void checkValidOrThrow(String name, int keyLen, int truncLen) {
        boolean isValidLen = true;
        boolean isValidTruncLen = true;

        if (!getSupportedAlgorithms().contains(name)) {
            throw new IllegalArgumentException("Couldn't find an algorithm: " + name);
        }

        switch (name) {
            case CRYPT_AES_CBC:
                isValidLen = keyLen == 128 || keyLen == 192 || keyLen == 256;
                break;
            case CRYPT_AES_CTR:
                // The keying material for AES-CTR is a key plus a 32-bit salt
                isValidLen = keyLen == 128 + 32 || keyLen == 192 + 32 || keyLen == 256 + 32;
                break;
            case AUTH_HMAC_MD5:
                isValidLen = keyLen == 128;
                isValidTruncLen = truncLen >= 96 && truncLen <= 128;
                break;
            case AUTH_HMAC_SHA1:
                isValidLen = keyLen == 160;
                isValidTruncLen = truncLen >= 96 && truncLen <= 160;
                break;
            case AUTH_HMAC_SHA256:
                isValidLen = keyLen == 256;
                isValidTruncLen = truncLen >= 96 && truncLen <= 256;
                break;
            case AUTH_HMAC_SHA384:
                isValidLen = keyLen == 384;
                isValidTruncLen = truncLen >= 192 && truncLen <= 384;
                break;
            case AUTH_HMAC_SHA512:
                isValidLen = keyLen == 512;
                isValidTruncLen = truncLen >= 256 && truncLen <= 512;
                break;
            case AUTH_AES_XCBC:
                isValidLen = keyLen == 128;
                isValidTruncLen = truncLen == 96;
                break;
            case AUTH_CRYPT_AES_GCM:
                // The keying material for GCM is a key plus a 32-bit salt
                isValidLen = keyLen == 128 + 32 || keyLen == 192 + 32 || keyLen == 256 + 32;
                isValidTruncLen = truncLen == 64 || truncLen == 96 || truncLen == 128;
                break;
            case AUTH_CRYPT_CHACHA20_POLY1305:
                // The keying material for ChaCha20Poly1305 is a key plus a 32-bit salt
                isValidLen = keyLen == 256 + 32;
                isValidTruncLen = truncLen == 128;
                break;
            default:
                // Should never hit it
                throw new IllegalArgumentException("Couldn't find an algorithm: " + name);
        }

        if (!isValidLen) {
            throw new IllegalArgumentException("Invalid key material keyLength: " + keyLen);
        }
        if (!isValidTruncLen) {
            throw new IllegalArgumentException("Invalid truncation keyLength: " + truncLen);
        }
    }

    /** @hide */
    public boolean isAuthentication() {
        switch (getName()) {
            // Fallthrough
            case AUTH_HMAC_MD5:
            case AUTH_HMAC_SHA1:
            case AUTH_HMAC_SHA256:
            case AUTH_HMAC_SHA384:
            case AUTH_HMAC_SHA512:
            case AUTH_AES_XCBC:
                return true;
            default:
                return false;
        }
    }

    /** @hide */
    public boolean isEncryption() {
        switch (getName()) {
                // Fallthrough
            case CRYPT_AES_CBC:
            case CRYPT_AES_CTR:
                return true;
            default:
                return false;
        }
    }

    /** @hide */
    public boolean isAead() {
        switch (getName()) {
                // Fallthrough
            case AUTH_CRYPT_AES_GCM:
            case AUTH_CRYPT_CHACHA20_POLY1305:
                return true;
            default:
                return false;
        }
    }

    // Because encryption keys are sensitive and userdebug builds are used by large user pools
    // such as beta testers, we only allow sensitive info such as keys on eng builds.
    private static boolean isUnsafeBuild() {
        return Build.IS_DEBUGGABLE && Build.IS_ENG;
    }

    @Override
    @NonNull
    public String toString() {
        return new StringBuilder()
                .append("{mName=")
                .append(mName)
                .append(", mKey=")
                .append(isUnsafeBuild() ? HexDump.toHexString(mKey) : "<hidden>")
                .append(", mTruncLenBits=")
                .append(mTruncLenBits)
                .append("}")
                .toString();
    }

    /** @hide */
    @VisibleForTesting
    public static boolean equals(IpSecAlgorithm lhs, IpSecAlgorithm rhs) {
        if (lhs == null || rhs == null) return (lhs == rhs);
        return (lhs.mName.equals(rhs.mName)
                && Arrays.equals(lhs.mKey, rhs.mKey)
                && lhs.mTruncLenBits == rhs.mTruncLenBits);
    }
};
