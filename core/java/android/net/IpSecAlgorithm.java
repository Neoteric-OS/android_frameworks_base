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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * IpSecAlgorithm specifies a single algorithm that can be applied to an IpSec Transform. Refer to
 * RFC 4301.
 *
 * @hide
 */
public class IpSecAlgorithm implements Parcelable {

    /**
     * Refer to the ip-xfrm man pages for support-able key types; however, only keys specified here
     * will be supported.
     */
    public static final String ALGO_AES_CBC = "cbc(aes)";

    public static final String ALGO_HMAC_MD5 = "hmac(md5)";
    public static final String ALGO_HMAC_SHA1 = "hmac(sha1)";
    public static final String ALGO_HMAC_SHA256 = "hmac(sha256)";
    public static final String ALGO_HMAC_SHA384 = "hmac(sha384)";
    public static final String ALGO_HMAC_SHA512 = "hmac(sha512)";

    private final String mAlgorithm;
    private final byte[] mKey;
    private final int mTruncLenBits;

    public IpSecAlgorithm(String algorithm, byte[] key) {
        this(algorithm, key, key.length * 8);
    }

    /**
     * Specify a IpSecAlgorithm of one of the supported types
     *
     * @param algorithm type for IpSec.
     * @param key non-null Key padded to a multiple of 8 bits
     * @param truncLenBits the number of bits of the key to use
     */
    public IpSecAlgorithm(String algorithm, byte[] key, int truncLenBits) {
        if (!validateAlgorithm(algorithm, truncLenBits)) {
            throw new IllegalArgumentException("Unknown algorithm or invalid length");
        }
        mAlgorithm = algorithm;
        mKey = key.clone();
        mTruncLenBits = Math.min(truncLenBits, key.length * 8);
    }

    /** @hide */
    public String getAlgorithm() {
        return mAlgorithm;
    }

    /** @hide */
    public byte[] getKey() {
        return mKey.clone();
    }

    /** @hide */
    public int getTruncLenBits() {
        return mTruncLenBits;
    }

    /* Parcelable Implementation */

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mAlgorithm);
        out.writeByteArray(mKey);
        out.writeInt(mTruncLenBits);
    }

    public static final Parcelable.Creator<IpSecAlgorithm> CREATOR =
            new Parcelable.Creator<IpSecAlgorithm>() {
                public IpSecAlgorithm createFromParcel(Parcel in) {
                    return new IpSecAlgorithm(in);
                }

                public IpSecAlgorithm[] newArray(int size) {
                    return new IpSecAlgorithm[size];
                }
            };

    private IpSecAlgorithm(Parcel in) {
        mAlgorithm = in.readString();
        mKey = in.createByteArray();
        mTruncLenBits = in.readInt();
        return;
    }

    private static boolean validateAlgorithm(String algo, int truncLenBits) {
        switch (algo) {
            case ALGO_AES_CBC:
                return (truncLenBits == 128 || truncLenBits == 192 || truncLenBits == 256);
            case ALGO_HMAC_MD5:
                return (truncLenBits >= 96 && truncLenBits <= 128);
            case ALGO_HMAC_SHA1:
                return (truncLenBits >= 96 && truncLenBits <= 160);
            case ALGO_HMAC_SHA256:
                return (truncLenBits >= 96 && truncLenBits <= 256);
            case ALGO_HMAC_SHA384:
                return (truncLenBits >= 192 && truncLenBits <= 384);
            case ALGO_HMAC_SHA512:
                return (truncLenBits >= 256 && truncLenBits <= 512);
            default:
                return false;
        }
    }
};
