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

import android.os.Binder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    private static final Set<String> ALL_ALGOS =
            new HashSet<>(Arrays.asList(ALGO_AES_CBC, ALGO_HMAC_MD5, ALGO_HMAC_SHA1));

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
        if (!ALL_ALGOS.contains(algorithm)) {
            throw new IllegalArgumentException(
                    "Unknown Algorithm " + algorithm + " passed to IpSecAlgorithm");
        }
        mAlgorithm = algorithm;
        mKey = key.clone();
        mTruncLenBits = Math.min(truncLenBits, key.length * 8);
    }

    private static void enforceSystemUid() {
        final int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only Available to the System UID");
        }
    }

    /** @hide */
    public String getAlgorithm() {
        enforceSystemUid();
        return mAlgorithm;
    }

    /** @hide */
    public byte[] getKey() {
        enforceSystemUid();
        return mKey.clone();
    }

    /** @hide */
    public int getTruncLenBits() {
        enforceSystemUid();
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
};
