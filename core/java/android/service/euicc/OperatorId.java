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

package android.service.euicc;

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/** This represents the operator id of a profile on eUICC. */
public class OperatorId implements Parcelable {

    // A table mapping from a number to a hex character for fast encoding hex strings.
    private static final char[] HEX_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    private final String mMcc;
    private final String mMnc;
    private final @Nullable byte[] mGid1;
    private final @Nullable byte[] mGid2;

    /**
     * Creates an operator id instance.
     *
     * @param mccMnc A 3-byte array as defined by 3GPP TS 24.008.
     * @throws IllegalArgumentException If the length of {@code mccMnc} is not 3.
     */
    public OperatorId(byte[] mccMnc, @Nullable byte[] gid1, @Nullable byte[] gid2) {
        if (mccMnc.length != 3) {
            throw new IllegalArgumentException(
                    "MCC & MNC must be set by a 3-byte array: byte[" + mccMnc.length + "]");
        }
        String hex = bytesToHex(mccMnc);
        mMcc = new String(new char[] {hex.charAt(1), hex.charAt(0), hex.charAt(3)});
        if (hex.charAt(2) == 'F') {
            mMnc = new String(new char[] {hex.charAt(5), hex.charAt(4)});
        } else {
            mMnc = new String(new char[] {hex.charAt(5), hex.charAt(4), hex.charAt(2)});
        }
        mGid1 = gid1;
        mGid2 = gid2;
    }

    /** @return A 3-character string for MCC */
    public String getMcc() {
        return mMcc;
    }

    /** @return A 2-character or 3-character string for MNC */
    public String getMnc() {
        return mMnc;
    }

    @Nullable
    public byte[] getGid1() {
        return mGid1;
    }

    @Nullable
    public byte[] getGid2() {
        return mGid2;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        OperatorId that = (OperatorId) obj;
        return Objects.equals(mMcc, that.mMcc)
                && Objects.equals(mMnc, that.mMnc)
                && Arrays.equals(mGid1, that.mGid1)
                && Arrays.equals(mGid2, that.mGid2);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(mMcc);
        result = 31 * result + Objects.hashCode(mMnc);
        result = 31 * result + Arrays.hashCode(mGid1);
        result = 31 * result + Arrays.hashCode(mGid2);
        return result;
    }

    @Override
    public String toString() {
        return "OperatorId (mcc="
                + mMcc
                + ", mnc="
                + mMnc
                + ", gid1="
                + (mGid1 == null ? "none" : bytesToHex(mGid1))
                + ", gid2="
                + (mGid2 == null ? "none" : bytesToHex(mGid2))
                + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mMcc);
        dest.writeString(mMnc);
        dest.writeByteArray(mGid1);
        dest.writeByteArray(mGid2);
    }

    private OperatorId(Parcel source) {
        mMcc = source.readString();
        mMnc = source.readString();
        mGid1 = source.createByteArray();
        mGid2 = source.createByteArray();
    }

    public static final Creator<OperatorId> CREATOR =
            new Creator<OperatorId>() {
                @Override
                public OperatorId createFromParcel(Parcel source) {
                    return new OperatorId(source);
                }

                @Override
                public OperatorId[] newArray(int size) {
                    return new OperatorId[size];
                }
            };

    /**
     * Converts an array of bytes to a hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            output.append(HEX_CHARS[(b & 0xFF) >>> 4]);
            output.append(HEX_CHARS[b & 0xF]);
        }
        return output.toString();
    }
}
