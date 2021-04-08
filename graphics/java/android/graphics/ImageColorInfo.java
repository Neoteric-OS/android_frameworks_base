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

package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;
import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.Arrays;
import java.nio.ByteBuffer;

/** Stores color info.
*
*/
public final class ImageColorInfo implements Parcelable {

    private static final String TAG = "MTK_imgColorInfo";
    /**
     * The color space of the image. Valid values are {@link MediaFormat#ColorStandard}
     */
    private int colorStandard;

    /**
     * The color range of the image. Valid values are {@link MediaFormat#ColorRange}
     */
    private int colorRange;

    /**
     * The color transfer of the image. Valid values are {@link MediaFormat#ColorTransfer}
     */
    private int colorTransfer;

    /**
    * HdrStaticInfo as defined in CTA-861.3, or null if none specified.
    */
    @NonNull
    private byte[] hdrStaticInfo;

    // Lazily initialized hashcode.
    private int hashCode;

    /**
     * Constructs the ColorInfo.
     *
     * @param colorSpace The color space of the video.
     * @param colorRange The color range of the video.
     * @param colorTransfer The color transfer characteristics of the video.
     * @param hdrStaticInfo HdrStaticInfo as defined in CTA-861.3, or null if none specified.
     */
    public ImageColorInfo(
            int colorStandard, int colorRange, int colorTransfer, @NonNull byte[] hdrStaticInfo) {
        this.colorStandard = colorStandard;
        this.colorRange = colorRange;
        this.colorTransfer = colorTransfer;
        this.hdrStaticInfo = hdrStaticInfo;
    }

    @SuppressWarnings("ResourceType")
    /* package */ ImageColorInfo(Parcel in) {
        colorStandard = in.readInt();
        colorRange = in.readInt();
        colorTransfer = in.readInt();
        // boolean hasHdrStaticInfo = Util.readBoolean(in);
        // hdrStaticInfo = hasHdrStaticInfo ? in.createByteArray() : null;
        hdrStaticInfo = in.createByteArray();
    }

    /* Parcelable implementation.
    *
    */
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      ImageColorInfo other = (ImageColorInfo) obj;
      return colorStandard == other.colorStandard
          && colorRange == other.colorRange
          && colorTransfer == other.colorTransfer
          && Arrays.equals(hdrStaticInfo, other.hdrStaticInfo);
     }

     @Override
     public String toString() {
       return "ColorInfo(" + colorStandard + ", " + colorRange + ", " + colorTransfer
           + ", " + (hdrStaticInfo != null) + ")";
     }

     @Override
     public int hashCode() {
       if (hashCode == 0) {
         int result = 17;
         result = 31 * result + colorStandard;
         result = 31 * result + colorRange;
         result = 31 * result + colorTransfer;
         result = 31 * result + Arrays.hashCode(hdrStaticInfo);
         hashCode = result;
       }
       return hashCode;
     }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(colorStandard);
        dest.writeInt(colorRange);
        dest.writeInt(colorTransfer);
        // Util.writeBoolean(dest, hdrStaticInfo != null);
        if (hdrStaticInfo != null) {
            dest.writeByteArray(hdrStaticInfo);
        }
    }

    @NonNull
    public static final Parcelable.Creator<ImageColorInfo> CREATOR =
            new Parcelable.Creator<ImageColorInfo>() {
                @Override
                public ImageColorInfo createFromParcel(Parcel in) {
                    return new ImageColorInfo(in);
                }

                @Override
                public ImageColorInfo[] newArray(int size) {
                    return new ImageColorInfo[size];
                }
            };

    public int getStandard() {
        return colorStandard;
    }

    public int getColorRange() {
        return colorRange;
    }

    public int getColorTransfer() {
        return colorTransfer;
    }

    @NonNull
    public byte[] getHdrStaticInfo() {
        return hdrStaticInfo;
    }
}

