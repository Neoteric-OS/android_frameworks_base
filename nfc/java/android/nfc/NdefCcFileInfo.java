/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.nfc;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents NDEF NFCEE CC File data
 */
@FlaggedApi(Flags.FLAG_NFC_OEM_EXTENSION)
public final class NdefCcFileInfo implements Parcelable {
    /**
     * Indicates the size of this capability container (called “CC File”)<p>
     */
    private int mCclen;
    /**
     * Indicates the mapping specification version<p>
     */
    private int mVersion;
    /**
     * Indicates the max data size by a single ReadBinary<p>
     */
    private int mMaxLe;
    /**
     * Indicates the max data size by a single UpdateBinary<p>
     */
    private int mMaxLc;
    /**
     * Indicates the NDEF File Identifier<p>
     */
    private int mNdefFileId;
    /**
     * Indicates the maximum Max NDEF file size<p>
     */
    private int mNdefMaxFileSize;
    /**
     * Indicates the read access condition<p>
     */
    private int mNdefReadAccess;
    /**
     * Indicates the write access condition<p>
     */
    private int mNdefWriteAccess;

    /**
     * Constructor to be used by NFC service and internal classes.
     */
    public NdefCcFileInfo(int cclen, int version, int maxLe, int maxLc,
                      int ndefFileId, int ndefMaxFileSize,
                      int ndefReadAccess, int ndefWriteAccess) {
        mCclen = cclen;
        mVersion = version;
        mMaxLc = maxLc;
        mMaxLe = maxLe;
        mNdefFileId = ndefFileId;
        mNdefMaxFileSize = ndefMaxFileSize;
        mNdefReadAccess = ndefReadAccess;
        mNdefWriteAccess = ndefWriteAccess;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {

        dest.writeInt(mCclen);
        dest.writeInt(mVersion);
        dest.writeInt(mMaxLc);
        dest.writeInt(mMaxLe);
        dest.writeInt(mNdefFileId);
        dest.writeInt(mNdefMaxFileSize);
        dest.writeInt(mNdefReadAccess);
        dest.writeInt(mNdefWriteAccess);
    }

    public int getCclen() {
        return mCclen;
    }

    public int getVersion() {
        return mVersion;
    }

    public int getMaxLe() {
        return mMaxLe;
    }

    public int getMaxLc() {
        return mMaxLc;
    }

    public int getNdefFileId() {
        return mNdefFileId;
    }

    public int getNdefMaxFileSize() {
        return mNdefMaxFileSize;
    }

    public int getNdefReadAccess() {
        return mNdefReadAccess;
    }

    public int getNdefWriteAccess() {
        return mNdefWriteAccess;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @android.annotation.NonNull Parcelable.Creator<NdefCcFileInfo> CREATOR =
            new Parcelable.Creator<NdefCcFileInfo>() {
                @Override
                public NdefCcFileInfo createFromParcel(Parcel in) {

                    // NdefCcFileInfo fields
                    int cclen = in.readInt();
                    int version = in.readInt();
                    int maxLe = in.readInt();
                    int maxLc = in.readInt();
                    int ndefFileId = in.readInt();
                    int ndefMaxFileSize = in.readInt();
                    int ndefReadAccess = in.readInt();
                    int ndefWriteAccess = in.readInt();

                    return new NdefCcFileInfo(cclen, version, maxLe, maxLc,
                            ndefFileId, ndefMaxFileSize,
                            ndefReadAccess, ndefWriteAccess);
                }

                @Override
                public NdefCcFileInfo[] newArray(int size) {
                    return new NdefCcFileInfo[size];
                }
            };
}
