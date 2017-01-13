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

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
public class IpSecAlgorithm implements Parcelable {
    public static final String ALGO_AES_CBC = "cbc(aes)";
    public static final String ALGO_HMAC_MD5 = "hmac(md5)";
    public static final String ALGO_HMAC_SHA1 = "hmac(sha1)";
    /* etc etc */

    public int mAlgorithm;
    public byte[] mKey;
    public int mTruncLenBits;

    public IpSecAlgorithm(int algorithm, byte[] key) {
        this(algorithm, key, key.length * 8);
    }

    public IpSecAlgorithm(int algorithm, byte[] key, int truncLenBits) {
        mAlgorithm = algorithm;
        mKey = key;
        mTruncLenBits = truncLenBits > (key.length * 8) ?
            key.length * 8 : truncLenBits;
    }

    /* Parcelable Implementation */

    public int describeContents() {
      return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
      /* TODO: stuff */
    }

    public static final Parcelable.Creator<IpSecAlgorithm> CREATOR
        = new Parcelable.Creator<IpSecAlgorithm>() {
        public IpSecAlgorithm createFromParcel(Parcel in) {
            return new IpSecAlgorithm(in);
        }

        public IpSecAlgorithm[] newArray(int size) {
            return new IpSecAlgorithm[size];
        }
    };

    private IpSecAlgorithm(Parcel in) {
        return;
    }
};


